package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.entity.RagDocument;
import com.inteliroadmap.backend.domain.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface RagDocumentService {

    RagDocument startStudentTranscript(User user, String storageUrl, MultipartFile file);

    RagDocument startGlobalKnowledge(MultipartFile file);

    void markCompleted(UUID documentId);

    void markFailed(UUID documentId, Exception exception);
}
