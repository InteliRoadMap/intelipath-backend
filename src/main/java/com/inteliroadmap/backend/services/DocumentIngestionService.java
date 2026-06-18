package com.inteliroadmap.backend.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionService {

    private final VectorStore vectorStore;

    /**
     * Ingest a PDF file into the Vector DB.
     * It reads the PDF, splits it into chunks, and saves to VectorStore.
     *
     * @param file The PDF file
     * @throws IOException If file reading fails
     */
    public void ingestPdfDocument(MultipartFile file) throws IOException {
        log.info("Starting ingestion for PDF document: {}", file.getOriginalFilename());

        Resource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };

        // [CHỈNH SỬA TẠI ĐÂY - BƯỚC 1: ĐỌC FILE TÀI LIỆU]
        // Mặc định đang dùng PagePdfDocumentReader của Spring AI (chỉ bóc chữ thô - plain text).
        // NẾU MUỐN DÙNG LLAMAPARSE, TIKA, HOẶC MARKITDOWN:
        // Bạn hãy xóa đoạn cấu hình PdfDocumentReaderConfig và pdfReader này đi, 
        // gọi API/thư viện của bên thứ 3 để lấy file Markdown về.
        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPageExtractedTextFormatter(new ExtractedTextFormatter.Builder()
                        .withNumberOfBottomTextLinesToDelete(0)
                        .withNumberOfTopPagesToSkipBeforeDelete(0)
                        .build())
                .withPagesPerDocument(1) // 1 document per page initially
                .build();

        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource, config);
        
        // Read the entire PDF into a list of Documents (1 per page)
        List<Document> documents = pdfReader.get();
        log.info("Read {} pages from PDF.", documents.size());

        // [CHỈNH SỬA TẠI ĐÂY - BƯỚC 2: CẮT NHỎ TÀI LIỆU (CHUNKING)]
        // Mặc định đang dùng TokenTextSplitter cắt theo độ dài token (max 800 tokens).
        // NẾU BẠN CHUYỂN SANG DÙNG MARKDOWN TỪ BƯỚC 1:
        // Hãy đổi class này thành MarkdownSplitter (để nó cắt theo thẻ Heading, Paragraph)
        // chứ đừng dùng TokenTextSplitter sẽ bị đứt gãy cấu trúc bảng biểu.
        TokenTextSplitter tokenTextSplitter = new TokenTextSplitter(800, 200, 5, 10000, true);
        List<Document> chunkedDocuments = tokenTextSplitter.apply(documents);
        
        // Add metadata to each chunk
        for (Document doc : chunkedDocuments) {
            doc.getMetadata().put("file_name", file.getOriginalFilename());
        }

        log.info("Split into {} chunks. Saving to Vector DB...", chunkedDocuments.size());

        // [CHỈNH SỬA TẠI ĐÂY - BƯỚC 3: NHÚNG (EMBEDDING) & LƯU LÊN VECTOR DB]
        // Hàm vectorStore.accept() này thực chất làm 2 việc ẩn bên trong:
        // 1. Gọi EmbeddingModel (như OpenAI text-embedding-3-small đã cấu hình trong .env) biến text thành Vector.
        // 2. Lưu Vector đó xuống PostgreSQL (bảng vector_store).
        // Nếu bạn muốn đổi model Embedding, chỉ cần sửa tên model trong file application.yml (hoặc .env) là xong,
        // không cần sửa code ở đây.
        vectorStore.accept(chunkedDocuments);

        log.info("Successfully ingested PDF document: {}", file.getOriginalFilename());
    }
}
