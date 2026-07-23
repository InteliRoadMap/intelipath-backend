package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.services.PdfToMarkdownService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
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
 * Service implementation for converting PDF documents to Markdown format using GPT-4o-mini Vision.
 *
 * Processing flow:
 * 1. Receives a PDF file (via MultipartFile or URL).
 * 2. Renders each PDF page into a PNG image using PDFBox.
 * 3. Sends the images to GPT-4o-mini Vision with a prompt to convert content to Markdown.
 * 4. Concatenates the Markdown results from all pages into a complete document.
 */
@Service
@Slf4j
public class PdfToMarkdownServiceImpl implements PdfToMarkdownService {

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

    /**
     * Constructor for PdfToMarkdownServiceImpl.
     *
     * @param chatClient the shared {@link ChatClient}
     */
    public PdfToMarkdownServiceImpl(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Converts an uploaded PDF file (MultipartFile) to Markdown.
     *
     * @param file the uploaded PDF file
     * @return a complete Markdown string representing the document
     * @throws IOException if reading the file fails
     */
    @Override
    public String convertToMarkdown(MultipartFile file) throws IOException {
        log.info("PdfToMarkdownServiceImpl: Starting PDF-to-Markdown conversion for file: {}, size: {} bytes",
                file.getOriginalFilename(), file.getSize());

        // Extract the file data into an InputStream and read it fully into a byte array
        try (InputStream inputStream = file.getInputStream()) {
            byte[] pdfBytes = inputStream.readAllBytes();
            // Delegate to the shared processing logic
            return processPages(pdfBytes, file.getOriginalFilename());
        }
    }

    /**
     * Converts a PDF file fetched from a URL to Markdown.
     *
     * @param pdfUrl the URL pointing to the PDF file
     * @return a complete Markdown string representing the document
     * @throws IOException if downloading or reading the file fails
     */
    @Override
    public String convertToMarkdown(String pdfUrl) throws IOException {
        log.info("PdfToMarkdownServiceImpl: Starting PDF-to-Markdown conversion for URL: {}", pdfUrl);

        try (InputStream inputStream = new URL(pdfUrl).openStream()) {
            byte[] pdfBytes = inputStream.readAllBytes();
            return processPages(pdfBytes, pdfUrl);
        }
    }

    /**
     * Converts a PDF byte array to Markdown.
     *
     * @param pdfBytes the byte array of the PDF file
     * @param sourceName the name of the file or URL (used for logging purposes)
     * @return a complete Markdown string representing the document
     * @throws IOException if reading the PDF fails
     */
    @Override
    public String convertToMarkdown(byte[] pdfBytes, String sourceName) throws IOException {
        log.info("PdfToMarkdownServiceImpl: Starting PDF-to-Markdown conversion for: {}, size: {} bytes",
                sourceName, pdfBytes.length);
        return processPages(pdfBytes, sourceName);
    }

    /**
     * Main processing logic: renders each PDF page to an image, sends it to the Vision AI,
     * and concatenates the resulting Markdown.
     *
     * @param pdfBytes the byte array of the PDF file
     * @param sourceName the name of the source (for logging)
     * @return the combined Markdown string of all pages
     * @throws IOException if PDF processing fails
     */
    private String processPages(byte[] pdfBytes, String sourceName) throws IOException {
        // Convert the PDF bytes into a list of PNG image byte arrays (one per page)
        List<byte[]> pageImages = renderPdfToImages(pdfBytes);
        log.info("PdfToMarkdownServiceImpl: Rendered {} pages from PDF: {}", pageImages.size(), sourceName);

        // Handle edge case of an empty PDF
        if (pageImages.isEmpty()) {
            log.warn("PdfToMarkdownServiceImpl: PDF has no pages: {}", sourceName);
            return "<!-- Document is empty -->";
        }

        StringBuilder fullMarkdown = new StringBuilder();

        // Iterate through each rendered page image
        for (int i = 0; i < pageImages.size(); i++) {
            int pageNumber = i + 1;
            log.debug("PdfToMarkdownServiceImpl: Processing page {}/{} of {}", pageNumber, pageImages.size(), sourceName);

            try {
                // Send the image to the AI vision model to extract its markdown content
                String pageMarkdown = convertPageToMarkdown(pageImages.get(i), pageNumber);

                // Add a page separator if this is a multi-page document
                if (pageImages.size() > 1) {
                    fullMarkdown.append("\n\n<!-- page: ").append(pageNumber).append(" -->\n\n");
                }
                // Append the extracted markdown for the current page
                fullMarkdown.append(pageMarkdown);

            } catch (Exception e) {
                // In case of a processing failure, log the error and embed a failure note in the markdown
                log.error("PdfToMarkdownServiceImpl: Failed to convert page {} of {}: {}", pageNumber, sourceName, e.getMessage());
                fullMarkdown.append("\n\n<!-- page: ").append(pageNumber)
                        .append(" failed: ").append(e.getMessage()).append(" -->\n\n");
            }
        }

        // Return the accumulated and trimmed markdown string
        String result = fullMarkdown.toString().trim();
        log.info("PdfToMarkdownServiceImpl: PDF-to-Markdown conversion completed for {}. Output length: {} chars", sourceName, result.length());
        return result;
    }

    /**
     * Uses PDFBox to render all PDF pages into a list of PNG images (byte arrays).
     *
     * @param pdfBytes the byte array of the PDF file
     * @return a list of byte arrays, each representing a PNG image of a page
     * @throws IOException if PDF rendering fails
     */
    private List<byte[]> renderPdfToImages(byte[] pdfBytes) throws IOException {
        List<byte[]> images = new ArrayList<>();

        // Load the PDF byte data into a PDFBox document model
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int totalPages = document.getNumberOfPages();

            // Iterate over each page and render it as an image
            for (int i = 0; i < totalPages; i++) {
                // Render the page to a BufferedImage at the specified DPI (150)
                BufferedImage image = renderer.renderImageWithDPI(i, RENDER_DPI, ImageType.RGB);

                // Write the rendered image to a byte array as a PNG format
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    ImageIO.write(image, "png", baos);
                    images.add(baos.toByteArray());
                }
            }
        }

        return images;
    }

    /**
     * Sends a single PDF page image to GPT-4o-mini Vision and retrieves the Markdown result.
     *
     * @param imageBytes the byte array of the page image
     * @param pageNumber the page number being processed
     * @return the Markdown representation of the page
     */
    private String convertPageToMarkdown(byte[] imageBytes, int pageNumber) {
        log.debug("PdfToMarkdownServiceImpl: Sending page {} image ({} bytes) to Vision AI", pageNumber, imageBytes.length);

        // Wrap byte[] thành Resource để Spring AI Media có thể đọc
        // Wrap the raw image bytes into a Spring Resource required by the AI Media wrapper
        ByteArrayResource imageResource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return "page-" + pageNumber + ".png";
            }
        };

        // Tạo Media object chứa ảnh PNG để gửi cho Vision API
        // Create a Media object specifying the MIME type and resource data
        Media imageMedia = new Media(MimeTypeUtils.IMAGE_PNG, imageResource);

        // Tạo UserMessage kèm ảnh
        // Construct the final user prompt injecting the specific page context alongside the image media
        UserMessage userMessage = UserMessage.builder()
                .text(VISION_PROMPT + "\n\nThis is page " + pageNumber + " of the document.")
                .media(List.of(imageMedia))
                .build();

        // Gọi ChatClient (GPT-4o-mini Vision) và nhận kết quả
        // Execute the call to the configured ChatClient and extract the raw markdown response content
        String markdown = chatClient.prompt()
                .messages(List.of(userMessage))
                .call()
                .content();

        // Handle case where AI fails to return any content
        if (markdown == null || markdown.isBlank()) {
            return "<!-- page: " + pageNumber + " - no content extracted -->";
        }

        return markdown.trim();
    }
}
