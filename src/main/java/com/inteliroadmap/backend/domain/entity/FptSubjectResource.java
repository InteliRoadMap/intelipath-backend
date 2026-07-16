package com.inteliroadmap.backend.domain.entity;

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

    @Column(name = "url", columnDefinition = "TEXT")
    private String url;

    @Column(name = "topic", columnDefinition = "TEXT")
    private String topic;

    @Column(name = "clo_ref", columnDefinition = "TEXT")
    private String cloRef;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;
}
