package com.inteliroadmap.backend.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.inteliroadmap.backend.domain.enums.FptResourceKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A concrete lesson resource harvested from an FPT syllabus: either a reference
 * MATERIAL (textbook / link) or a SESSION (one class session's topic). Shown under
 * a roadmap node's "learn at FPT" section. Empty until the syllabus re-scrape runs.
 */
@Entity
@Table(name = "fpt_subject_resources")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FptSubjectResource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "subject_code", length = 20, nullable = false)
    private String subjectCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 20, nullable = false)
    private FptResourceKind kind;

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    private String title;

    /** A reference link the syllabus itself published (docs, library catalogue). Safe to show. */
    @Column(name = "url", columnDefinition = "TEXT")
    private String url;

    /**
     * Where the file was harvested from. Never leaves the server: clients are given a
     * short-lived signed URL to our own copy instead, so withholding the material is a
     * real decision rather than an unlisted link.
     */
    @JsonIgnore
    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    /** Object key in the private storage bucket, or null when nothing has been mirrored. */
    @JsonIgnore
    @Column(name = "storage_path", columnDefinition = "TEXT")
    private String storagePath;

    /** Size of the mirrored file, so the UI can say "13.6 MB" before a student commits. */
    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "mirrored_at")
    private LocalDateTime mirroredAt;

    @Column(name = "topic", columnDefinition = "TEXT")
    private String topic;

    @Column(name = "clo_ref", columnDefinition = "TEXT")
    private String cloRef;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;
}
