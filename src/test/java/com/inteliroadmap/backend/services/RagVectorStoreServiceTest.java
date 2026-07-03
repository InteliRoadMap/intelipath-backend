package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.entity.RagDocument;
import com.inteliroadmap.backend.domain.enums.RagDocumentScope;
import com.inteliroadmap.backend.domain.enums.RagDocumentSourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RagVectorStoreServiceTest {

    @Mock
    private VectorStore vectorStore;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void replacesOnlyChunksOfOneDocumentAndAddsConsistentMetadata() {
        UUID documentId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        RagDocument ragDocument = RagDocument.builder()
                .documentId(documentId)
                .ownerUserId(ownerUserId)
                .scope(RagDocumentScope.STUDENT)
                .sourceType(RagDocumentSourceType.TRANSCRIPT)
                .fileName("transcript.pdf")
                .build();
        Document chunk = new Document("Transcript content");

        new com.inteliroadmap.backend.services.impl.RagVectorStoreServiceImpl(vectorStore, jdbcTemplate)
                .replaceDocumentChunks(ragDocument, List.of(chunk));

        verify(jdbcTemplate).update(anyString(), eq(documentId.toString()));
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        Document enrichedChunk = captor.getValue().getFirst();
        assertEquals(documentId.toString(), enrichedChunk.getMetadata().get("documentId"));
        assertEquals(ownerUserId.toString(), enrichedChunk.getMetadata().get("userId"));
        assertEquals("STUDENT", enrichedChunk.getMetadata().get("scope"));
        assertEquals(0, enrichedChunk.getMetadata().get("chunkIndex"));
    }
}
