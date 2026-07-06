package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "processed_companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    @Column(name = "company_id", nullable = false)
    private String topCvCompanyId;
//
//    @Column(name = "company_link", columnDefinition = "TEXT")
//    private String companyLink;
//
//    @Column(name = "logo", columnDefinition = "TEXT")
//    private String logo;
//
//    @Column(name = "name", columnDefinition = "TEXT")
//    private String name;
//
//    @JdbcTypeCode(SqlTypes.JSON)
//    @Column(columnDefinition = "jsonb")
//    private List<String> introduction;

    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private com.fasterxml.jackson.databind.JsonNode signatures;

    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private com.fasterxml.jackson.databind.JsonNode infos;

//    @JdbcTypeCode(SqlTypes.JSON)
//    @Column(columnDefinition = "jsonb")
//    private List<String> contact;
//
//    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
//    @Builder.Default
//    private List<RecruitmentPost> recruitmentPosts = new ArrayList<>();
}

