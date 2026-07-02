package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.entity.RagDocument;
import com.inteliroadmap.backend.services.RagVectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Adds and replaces vector chunks while keeping their document-level metadata consistent. */
@Service
@RequiredArgsConstructor
public class RagVectorStoreServiceImpl implements RagVectorStoreService {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void replaceDocumentChunks(RagDocument ragDocument, List<Document> chunks) {
        deleteDocumentChunks(ragDocument.getDocumentId());

        for (int index = 0; index < chunks.size(); index++) {
            Document chunk = chunks.get(index);
            chunk.getMetadata().put("documentId", ragDocument.getDocumentId().toString());
            chunk.getMetadata().put("scope", ragDocument.getScope().name());
            chunk.getMetadata().put("sourceType", ragDocument.getSourceType().name());
            chunk.getMetadata().put("fileName", ragDocument.getFileName());
            chunk.getMetadata().put("chunkIndex", index);
            if (ragDocument.getOwnerUserId() != null) {
                chunk.getMetadata().put("userId", ragDocument.getOwnerUserId().toString());
            }
        }

        if (!chunks.isEmpty()) {
            vectorStore.add(chunks);
        }
    }

    @Override
    public void deleteDocumentChunks(UUID documentId) {
        jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata->>'documentId' = ?",
                documentId.toString()
        );
    }
}
