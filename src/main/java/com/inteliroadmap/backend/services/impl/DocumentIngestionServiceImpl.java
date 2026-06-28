package com.inteliroadmap.backend.services.impl;

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

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionServiceImpl {

    private final VectorStore vectorStore;
    private final PdfToMarkdownServiceImpl pdfToMarkdownService;

    /**
     * Ingest a PDF file into the Vector DB
     * @param file The PDF file
     * @throws IOException If file reading fails
     */
    public void ingestPdfDocument(MultipartFile file) throws IOException {
        log.info("Starting ingestion for PDF document: {}", file.getOriginalFilename());

        // BƯỚC 1: Dùng GPT-4o-mini Vision để parse PDF → Markdown
        // Thay vì dùng PagePdfDocumentReader (chỉ bóc text thô, mất hết bảng biểu),
        // giờ ta dùng Vision AI để "nhìn" từng trang PDF và xuất ra Markdown chuẩn.
        String markdown = pdfToMarkdownService.convertToMarkdown(file);
        log.info("Converted PDF to Markdown. Length: {} chars", markdown.length());

        // BƯỚC 2: Tách Markdown thành các Document để chunking
        // Mỗi trang PDF đã được phân tách bằng comment <!-- page: X -->
        // Ta split theo page separator trước, rồi mới chunk nhỏ hơn nếu cần
        List<Document> documents = splitMarkdownIntoDocuments(markdown, file.getOriginalFilename());
        log.info("Split Markdown into {} page-level documents.", documents.size());

        // BƯỚC 3: Cắt nhỏ thêm nếu một trang quá dài (> 800 tokens)
        TokenTextSplitter tokenTextSplitter = new TokenTextSplitter(800, 200, 5, 10000, true);
        List<Document> chunkedDocuments = tokenTextSplitter.apply(documents);

        // Thêm metadata
        for (Document doc : chunkedDocuments) {
            doc.getMetadata().put("file_name", file.getOriginalFilename());
            doc.getMetadata().put("content_type", "markdown");
        }

        log.info("Split into {} final chunks. Saving to Vector DB...", chunkedDocuments.size());

        // BƯỚC 4: Lưu vào VectorStore (tự động gọi EmbeddingModel)
        vectorStore.accept(chunkedDocuments);

        log.info("Successfully ingested PDF document: {}", file.getOriginalFilename());
    }

    /**
     * Tách chuỗi Markdown thành danh sách Document theo page separator.
     * Mỗi page separator có dạng: <!-- page: X -->
     */
    private List<Document> splitMarkdownIntoDocuments(String markdown, String fileName) {
        List<Document> documents = new ArrayList<>();

        // Split theo page separator
        String[] pages = markdown.split("(?=<!-- page: \\d+ -->)");

        int pageNumber = 0;
        for (String page : pages) {
            String trimmed = page.trim();
            if (trimmed.isEmpty()) continue;

            pageNumber++;
            Document doc = new Document(trimmed, Map.of(
                    "page_number", pageNumber,
                    "source", fileName
            ));
            documents.add(doc);
        }

        // Nếu không có page separator (PDF chỉ 1 trang), tạo 1 document duy nhất
        if (documents.isEmpty() && !markdown.isBlank()) {
            documents.add(new Document(markdown.trim(), Map.of(
                    "page_number", 1,
                    "source", fileName
            )));
        }

        return documents;
    }
}
