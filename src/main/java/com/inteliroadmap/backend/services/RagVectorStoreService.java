package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.entity.RagDocument;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.UUID;

public interface RagVectorStoreService {

    void replaceDocumentChunks(RagDocument ragDocument, List<Document> chunks);

    void deleteDocumentChunks(UUID documentId);
}
