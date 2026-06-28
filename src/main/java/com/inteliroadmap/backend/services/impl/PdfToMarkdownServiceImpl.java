package com.inteliroadmap.backend.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Service chuyển đổi PDF sang Markdown bằng GPT-4o-mini Vision.
 *
 * Luồng xử lý:
 * 1. Nhận file PDF (từ MultipartFile hoặc URL)
 * 2. Dùng PDFBox render từng trang PDF thành ảnh PNG
 * 3. Gửi ảnh cho GPT-4o-mini Vision kèm prompt yêu cầu convert sang Markdown
 * 4. Ghép Markdown của tất cả các trang lại thành một chuỗi hoàn chỉnh
 */
@Service
@Slf4j
public class PdfToMarkdownServiceImpl {

    private final ChatClient chatClient;

    /**
     * DPI để render PDF thành ảnh. 150 DPI là mức cân bằng giữa chất lượng và kích thước ảnh.
     * - 72 DPI: quá mờ, AI khó đọc bảng biểu
     * - 150 DPI: đủ rõ, kích thước ảnh hợp lý (~200KB/trang)
     * - 300 DPI: quá nặng, tốn token không cần thiết
     */
    private static final float RENDER_DPI = 150f;

    private static final String VISION_PROMPT = """
            You are an elite document-to-Markdown converter. Your sole task is to transcribe the content of the provided PDF page image into Markdown with strict formatting and zero external commentary.
            
            OUTPUT RULES (non-negotiable):
            1. Output raw Markdown only. No code fences (```markdown), no explanations, no preamble.
            2. If the page is blank or completely unreadable, output exactly: <!-- empty page -->
            
            TEXT & SPACING:
            - Reproduce all text accurately. Ensure there is a proper space between distinct words, numbers, and symbols.
            - Use # / ## / ### for headings only when the source document visually uses a title or section header.
            - Separate distinct paragraphs with EXACTLY ONE blank line.
            
            TABLES (CRITICAL - highest-priority rule):
            - EVERY table row MUST begin with | and end with |.
            - The column separator row MUST EXACTLY match the number of columns in the header. Use |---|---| format.
            - ABSOLUTELY NO BLANK LINES inside a table. Do not put newlines between table rows.
            - NEVER merge adjacent columns. Each visual column = one Markdown column.
            - If a cell spans multiple lines visually, merge them into a single line using a space or <br>.
            - Example of correct output:
              | Course Code | Course Name | Credits | Grade |
              |---|---|---|---|
              | CS101 | Intro to Computing | 3 | A |
              | CS102 | Advanced Data | 3 | B+ |
            
            PROHIBITED:
            - Do not add, infer, or summarize any content not visible on the page.
            - Do not output any sentence before or after the Markdown document.
            - Do not leave empty lines between the header, separator, and body rows of a table.
            """;

    public PdfToMarkdownServiceImpl(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Chuyển đổi file PDF (MultipartFile) sang Markdown.
     *
     * @param file File PDF upload từ user
     * @return Chuỗi Markdown hoàn chỉnh của toàn bộ file
     * @throws IOException Nếu đọc file thất bại
     */
    public String convertToMarkdown(MultipartFile file) throws IOException {
        log.info("Starting PDF-to-Markdown conversion for file: {}, size: {} bytes",
                file.getOriginalFilename(), file.getSize());

        try (InputStream inputStream = file.getInputStream()) {
            byte[] pdfBytes = inputStream.readAllBytes();
            return processPages(pdfBytes, file.getOriginalFilename());
        }
    }

    /**
     * Chuyển đổi file PDF từ URL sang Markdown.
     *
     * @param pdfUrl URL trỏ tới file PDF
     * @return Chuỗi Markdown hoàn chỉnh của toàn bộ file
     * @throws IOException Nếu download hoặc đọc file thất bại
     */
    public String convertToMarkdown(String pdfUrl) throws IOException {
        log.info("Starting PDF-to-Markdown conversion for URL: {}", pdfUrl);

        try (InputStream inputStream = new URL(pdfUrl).openStream()) {
            byte[] pdfBytes = inputStream.readAllBytes();
            return processPages(pdfBytes, pdfUrl);
        }
    }

    /**
     * Chuyển đổi mảng byte PDF sang Markdown.
     *
     * @param pdfBytes Mảng byte của file PDF
     * @param sourceName Tên file hoặc URL (dùng cho logging)
     * @return Chuỗi Markdown hoàn chỉnh
     * @throws IOException Nếu đọc PDF thất bại
     */
    public String convertToMarkdown(byte[] pdfBytes, String sourceName) throws IOException {
        log.info("Starting PDF-to-Markdown conversion for: {}, size: {} bytes",
                sourceName, pdfBytes.length);
        return processPages(pdfBytes, sourceName);
    }

    /**
     * Xử lý chính: render từng trang PDF thành ảnh, gửi cho Vision AI, ghép kết quả.
     */
    private String processPages(byte[] pdfBytes, String sourceName) throws IOException {
        List<byte[]> pageImages = renderPdfToImages(pdfBytes);
        log.info("Rendered {} pages from PDF: {}", pageImages.size(), sourceName);

        if (pageImages.isEmpty()) {
            log.warn("PDF has no pages: {}", sourceName);
            return "<!-- Document is empty -->";
        }

        StringBuilder fullMarkdown = new StringBuilder();

        for (int i = 0; i < pageImages.size(); i++) {
            int pageNumber = i + 1;
            log.debug("Processing page {}/{} of {}", pageNumber, pageImages.size(), sourceName);

            try {
                String pageMarkdown = convertPageToMarkdown(pageImages.get(i), pageNumber);

                if (pageImages.size() > 1) {
                    fullMarkdown.append("\n\n<!-- page: ").append(pageNumber).append(" -->\n\n");
                }
                fullMarkdown.append(pageMarkdown);

            } catch (Exception e) {
                log.error("Failed to convert page {} of {}: {}", pageNumber, sourceName, e.getMessage());
                fullMarkdown.append("\n\n<!-- page: ").append(pageNumber)
                        .append(" failed: ").append(e.getMessage()).append(" -->\n\n");
            }
        }

        String result = fullMarkdown.toString().trim();
        log.info("PDF-to-Markdown conversion completed for {}. Output length: {} chars", sourceName, result.length());
        return result;
    }

    /**
     * Dùng PDFBox render tất cả các trang PDF thành danh sách ảnh PNG (byte array).
     */
    private List<byte[]> renderPdfToImages(byte[] pdfBytes) throws IOException {
        List<byte[]> images = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int totalPages = document.getNumberOfPages();

            for (int i = 0; i < totalPages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, RENDER_DPI, ImageType.RGB);

                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    ImageIO.write(image, "png", baos);
                    images.add(baos.toByteArray());
                }
            }
        }

        return images;
    }

    /**
     * Gửi một ảnh trang PDF cho GPT-4o-mini Vision và nhận về Markdown.
     */
    private String convertPageToMarkdown(byte[] imageBytes, int pageNumber) {
        log.debug("Sending page {} image ({} bytes) to Vision AI", pageNumber, imageBytes.length);

        // Wrap byte[] thành Resource để Spring AI Media có thể đọc
        ByteArrayResource imageResource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return "page-" + pageNumber + ".png";
            }
        };

        // Tạo Media object chứa ảnh PNG để gửi cho Vision API
        Media imageMedia = new Media(MimeTypeUtils.IMAGE_PNG, imageResource);

        // Tạo UserMessage kèm ảnh
        UserMessage userMessage = new UserMessage(
                VISION_PROMPT + "\n\nThis is page " + pageNumber + " of the document.",
                List.of(imageMedia)
        );

        // Gọi ChatClient (GPT-4o-mini Vision) và nhận kết quả
        String markdown = chatClient.prompt()
                .messages(List.of(userMessage))
                .call()
                .content();

        if (markdown == null || markdown.isBlank()) {
            return "<!-- page: " + pageNumber + " - no content extracted -->";
        }

        return markdown.trim();
    }
}
