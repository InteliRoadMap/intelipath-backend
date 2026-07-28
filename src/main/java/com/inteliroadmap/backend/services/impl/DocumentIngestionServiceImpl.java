package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.entity.RagDocument;
import com.inteliroadmap.backend.services.DocumentIngestionService;
import com.inteliroadmap.backend.services.PdfToMarkdownService;
import com.inteliroadmap.backend.services.RagVectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
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

    private final PdfToMarkdownService pdfToMarkdownService;
    private final RagVectorStoreService ragVectorStoreService;

    /**
     * Ingest a PDF file into the Vector DB, owned by {@code document}.
     *
     * @param file     The PDF file
     * @param document The RagDocument whose scope and owner the chunks inherit
     * @throws IOException If file reading fails
     */
    @Override
    public void ingestPdfDocument(MultipartFile file, RagDocument document) throws IOException {
        log.info("DocumentIngestionServiceImpl: Starting ingestion for PDF document: {}", file.getOriginalFilename());

        String markdown = pdfToMarkdownService.convertToMarkdown(file);
        log.info("DocumentIngestionServiceImpl: Converted PDF to Markdown. Length: {} chars", markdown.length());

        List<Document> documents = splitMarkdownIntoDocuments(markdown, file.getOriginalFilename());
        log.info("DocumentIngestionServiceImpl: Split Markdown into {} page-level documents.", documents.size());

        // Built rather than constructed so the splitter keeps Spring AI's default
        // punctuation marks: the all-args constructor takes them as its last argument, and
        // passing an empty list there fails validation ("punctuationMarks must not be
        // empty"), which aborted every transcript upload. Chunk sizes stay as tuned —
        // minChunkSizeChars is deliberately 200, below the builder's own 350 default.
        TokenTextSplitter tokenTextSplitter = TokenTextSplitter.builder()
                .withChunkSize(800)
                .withMinChunkSizeChars(200)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();
        List<Document> chunkedDocuments = tokenTextSplitter.apply(documents);

        for (Document doc : chunkedDocuments) {
            doc.getMetadata().put("content_type", "markdown");
        }

        log.info("DocumentIngestionServiceImpl: Split into {} final chunks. Saving to Vector DB...", chunkedDocuments.size());

        // Stamps each chunk with the document's owner and scope, and drops the previous
        // version's chunks so a re-upload replaces rather than duplicates.
        ragVectorStoreService.replaceDocumentChunks(document, chunkedDocuments);

        log.info("DocumentIngestionServiceImpl: Successfully ingested PDF document: {}", file.getOriginalFilename());
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

        if (documents.isEmpty() && !markdown.isBlank()) {
            documents.add(new Document(markdown.trim(), Map.of(
                    "page_number", 1,
                    "source", fileName
            )));
        }

        return documents;
    }
}
