package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.services.DocumentIngestionService;
import com.inteliroadmap.backend.services.PdfToMarkdownService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link DocumentIngestionService} responsible for processing
 * and ingesting document files (like PDFs) into a Vector Database.
 * This service parses documents into Markdown, chunks them into smaller segments,
 * and stores them in the vector store for AI retrieval.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionServiceImpl implements DocumentIngestionService {

    private final VectorStore vectorStore;
    private final PdfToMarkdownService pdfToMarkdownService;

    /**
     * Ingest a PDF file into the Vector DB
     * @param file The PDF file
     * @throws IOException If file reading fails
     */
    @Override
    public void ingestPdfDocument(MultipartFile file) throws IOException {
        log.info("Starting ingestion for PDF document: {}", file.getOriginalFilename());

        // BƯỚC 1: Dùng GPT-4o-mini Vision để parse PDF → Markdown
        // Thay vì dùng PagePdfDocumentReader (chỉ bóc text thô, mất hết bảng biểu),
        // giờ ta dùng Vision AI để "nhìn" từng trang PDF và xuất ra Markdown chuẩn.
        // Step 1: Use the AI service to convert the PDF content to a Markdown string
        String markdown = pdfToMarkdownService.convertToMarkdown(file);
        log.info("Converted PDF to Markdown. Length: {} chars", markdown.length());

        // BƯỚC 2: Tách Markdown thành các Document để chunking
        // Mỗi trang PDF đã được phân tách bằng comment <!-- page: X -->
        // Ta split theo page separator trước, rồi mới chunk nhỏ hơn nếu cần
        // Step 2: Split the full Markdown text into a list of Document objects, one per page
        List<Document> documents = splitMarkdownIntoDocuments(markdown, file.getOriginalFilename());
        log.info("Split Markdown into {} page-level documents.", documents.size());

        // BƯỚC 3: Cắt nhỏ thêm nếu một trang quá dài (> 800 tokens)
        // Step 3: Configure a TokenTextSplitter to break down large page-level documents into smaller chunks
        TokenTextSplitter tokenTextSplitter = new TokenTextSplitter(800, 200, 5, 10000, true, List.of());
        // Apply the splitter to the documents to get chunked representations
        List<Document> chunkedDocuments = tokenTextSplitter.apply(documents);

        // Thêm metadata
        // Step 4: Iterate through all chunked documents and inject file-related metadata
        for (Document doc : chunkedDocuments) {
            doc.getMetadata().put("file_name", file.getOriginalFilename());
            doc.getMetadata().put("content_type", "markdown");
        }

        log.info("Split into {} final chunks. Saving to Vector DB...", chunkedDocuments.size());

        // BƯỚC 4: Lưu vào VectorStore (tự động gọi EmbeddingModel)
        // Step 5: Save the chunked documents into the Vector DB, which automatically computes and stores embeddings
        vectorStore.accept(chunkedDocuments);

        log.info("Successfully ingested PDF document: {}", file.getOriginalFilename());
    }

    /**
     * Splits a Markdown string into a list of {@link Document}s based on page separators.
     * Each page separator is expected to be in the format: {@code <!-- page: X -->}.
     * 
     * @param markdown The full Markdown content extracted from the document
     * @param fileName The original file name to be stored in the document metadata
     * @return A list of {@link Document} objects representing individual pages
     */
    private List<Document> splitMarkdownIntoDocuments(String markdown, String fileName) {
        List<Document> documents = new ArrayList<>();

        // Split theo page separator
        // Split the markdown content into individual pages based on the HTML comment separator
        String[] pages = markdown.split("(?=<!-- page: \\d+ -->)");

        int pageNumber = 0;
        // Loop through the split pages and create a Document for each non-empty page
        for (String page : pages) {
            String trimmed = page.trim();
            // Skip empty segments
            if (trimmed.isEmpty()) continue;

            pageNumber++;
            // Create a new Document holding the page content and its metadata (page number and source file)
            Document doc = new Document(trimmed, Map.of(
                    "page_number", pageNumber,
                    "source", fileName
            ));
            documents.add(doc);
        }

        // Nếu không có page separator (PDF chỉ 1 trang), tạo 1 document duy nhất
        // Fallback: If no separators were found but the text isn't blank, assume it's a single page document
        if (documents.isEmpty() && !markdown.isBlank()) {
            documents.add(new Document(markdown.trim(), Map.of(
                    "page_number", 1,
                    "source", fileName
            )));
        }

        return documents;
    }
}
