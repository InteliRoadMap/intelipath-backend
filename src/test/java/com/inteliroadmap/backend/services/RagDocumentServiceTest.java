package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.entity.RagDocument;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.RagDocumentScope;
import com.inteliroadmap.backend.domain.enums.RagDocumentSourceType;
import com.inteliroadmap.backend.domain.enums.RagDocumentStatus;
import com.inteliroadmap.backend.repositories.RagDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagDocumentServiceTest {

    @Mock
    private RagDocumentRepository ragDocumentRepository;

    @Test
    void startsStudentTranscriptWithPrivateScopeAndVersion() {
        User user = User.builder().userId(UUID.randomUUID()).build();
        MockMultipartFile file = new MockMultipartFile("file", "transcript.pdf", "application/pdf", "marks".getBytes());
        when(ragDocumentRepository.findFirstByOwnerUserIdAndSourceTypeOrderByUpdatedAtDesc(
                user.getUserId(), RagDocumentSourceType.TRANSCRIPT)).thenReturn(Optional.empty());
        when(ragDocumentRepository.save(any(RagDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RagDocument result = new com.inteliroadmap.backend.services.impl.RagDocumentServiceImpl(ragDocumentRepository)
                .startStudentTranscript(user, "https://storage/transcript.pdf", file);

        assertEquals(RagDocumentScope.STUDENT, result.getScope());
        assertEquals(RagDocumentSourceType.TRANSCRIPT, result.getSourceType());
        assertEquals(RagDocumentStatus.PROCESSING, result.getIngestionStatus());
        assertEquals(1, result.getIngestionVersion());
        assertEquals(user.getUserId(), result.getOwnerUserId());
        assertNotNull(result.getChecksum());
    }

    @Test
    void reingestionIncrementsExistingDocumentVersion() {
        User user = User.builder().userId(UUID.randomUUID()).build();
        RagDocument existing = RagDocument.builder()
                .documentId(UUID.randomUUID())
                .ownerUserId(user.getUserId())
                .scope(RagDocumentScope.STUDENT)
                .sourceType(RagDocumentSourceType.TRANSCRIPT)
                .ingestionVersion(3)
                .build();
        MockMultipartFile file = new MockMultipartFile("file", "new.pdf", "application/pdf", "updated marks".getBytes());
        when(ragDocumentRepository.findFirstByOwnerUserIdAndSourceTypeOrderByUpdatedAtDesc(
                user.getUserId(), RagDocumentSourceType.TRANSCRIPT)).thenReturn(Optional.of(existing));
        when(ragDocumentRepository.save(any(RagDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        new com.inteliroadmap.backend.services.impl.RagDocumentServiceImpl(ragDocumentRepository).startStudentTranscript(user, "https://storage/new.pdf", file);

        ArgumentCaptor<RagDocument> captor = ArgumentCaptor.forClass(RagDocument.class);
        verify(ragDocumentRepository).save(captor.capture());
        assertEquals(4, captor.getValue().getIngestionVersion());
        assertEquals(RagDocumentStatus.PROCESSING, captor.getValue().getIngestionStatus());
    }
}
