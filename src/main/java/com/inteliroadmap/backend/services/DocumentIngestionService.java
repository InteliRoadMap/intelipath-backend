package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.entity.RagDocument;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface DocumentIngestionService {

    /**
     * Chunk a PDF and store it against {@code document}, whose scope and owner decide who
     * may retrieve those chunks later. The RagDocument is required rather than the file
     * alone: without an owner on each chunk, retrieval cannot tell one student's
     * transcript from another's.
     */
    void ingestPdfDocument(MultipartFile file, RagDocument document) throws IOException;
}
