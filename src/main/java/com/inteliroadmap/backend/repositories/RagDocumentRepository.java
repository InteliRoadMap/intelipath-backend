package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.RagDocument;
import com.inteliroadmap.backend.domain.enums.RagDocumentSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RagDocumentRepository extends JpaRepository<RagDocument, UUID> {

    Optional<RagDocument> findFirstByOwnerUserIdAndSourceTypeOrderByUpdatedAtDesc(
            UUID ownerUserId,
            RagDocumentSourceType sourceType
    );
}
