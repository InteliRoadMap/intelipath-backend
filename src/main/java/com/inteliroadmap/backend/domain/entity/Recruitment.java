package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "recruitments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recruitment {

    @Id
    @Column(name = "recruitment_id", nullable = false)
    private String topCvRecruitmentId;

    @Column(name = "recruitment_link", columnDefinition = "TEXT")
    private String recruitmentLink;

    @Column(name = "title", columnDefinition = "TEXT")
    private String title;

    @Column(name = "salary", columnDefinition = "TEXT")
    private String salary;

    @Column(name = "location", columnDefinition = "TEXT")
    private String location;

    @Column(name = "experience", columnDefinition = "TEXT")
    private String experience;

    @Column(name = "application_deadline")
    private LocalDate applicationDeadline;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, List<String>> tags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, List<String>> descriptions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> generalInfos;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, List<String>> relatedTags;

    @OneToMany(mappedBy = "recruitment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RecruitmentPost> recruitmentPosts = new ArrayList<>();
}
