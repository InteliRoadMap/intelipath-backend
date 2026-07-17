package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.entity.RagDocument;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.RagDocumentScope;
import com.inteliroadmap.backend.domain.enums.RagDocumentSourceType;
import com.inteliroadmap.backend.domain.enums.RagDocumentStatus;
import com.inteliroadmap.backend.repositories.RagDocumentRepository;
import com.inteliroadmap.backend.services.RagDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/** Owns the lifecycle metadata for documents sent to the RAG vector store. */
@Service
@RequiredArgsConstructor
public class RagDocumentServiceImpl implements RagDocumentService {

    private final RagDocumentRepository ragDocumentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public RagDocument startStudentTranscript(User user, String storageUrl, MultipartFile file) {
        RagDocument document = ragDocumentRepository
                .findFirstByOwnerUserIdAndSourceTypeOrderByUpdatedAtDesc(
                        user.getUserId(),
                        RagDocumentSourceType.TRANSCRIPT
                )
                .orElseGet(() -> RagDocument.builder()
                        .ownerUserId(user.getUserId())
                        .scope(RagDocumentScope.STUDENT)
                        .sourceType(RagDocumentSourceType.TRANSCRIPT)
                        .ingestionVersion(0)
                        .build());

        populateForIngestion(document, file, storageUrl);
        return ragDocumentRepository.save(document);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public RagDocument startGlobalKnowledge(MultipartFile file) {
        RagDocument document = RagDocument.builder()
                .scope(RagDocumentScope.GLOBAL)
                .sourceType(RagDocumentSourceType.ADMIN_KNOWLEDGE)
                .ingestionVersion(0)
                .build();

        populateForIngestion(document, file, null);
        return ragDocumentRepository.save(document);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void markCompleted(UUID documentId) {
        ragDocumentRepository.findById(documentId).ifPresent(document -> {
            document.setIngestionStatus(RagDocumentStatus.COMPLETED);
            document.setErrorMessage(null);
            ragDocumentRepository.save(document);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void markFailed(UUID documentId, Exception exception) {
        ragDocumentRepository.findById(documentId).ifPresent(document -> {
            document.setIngestionStatus(RagDocumentStatus.FAILED);
            document.setErrorMessage(exception.getMessage());
            ragDocumentRepository.save(document);
        });
    }

    private void populateForIngestion(RagDocument document, MultipartFile file, String storageUrl) {
        document.setFileName(file.getOriginalFilename() == null ? "unnamed-document" : file.getOriginalFilename());
        document.setStorageUrl(storageUrl);
        document.setChecksum(sha256(file));
        document.setIngestionStatus(RagDocumentStatus.PROCESSING);
        document.setErrorMessage(null);
        document.setIngestionVersion(document.getIngestionVersion() + 1);
    }

    private String sha256(MultipartFile file) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(file.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read document for checksum calculation", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot calculate document checksum", exception);
        }
    }
}
