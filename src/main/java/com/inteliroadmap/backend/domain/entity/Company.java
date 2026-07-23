package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * AI-processed company, mirroring the service's processed_companies output.
 *   signatures = { link, logo, name }
 *   infos      = { info, contact }   (AI-summarised)
 */
@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    @Column(name = "company_id", nullable = false)
    private String topCvCompanyId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "signatures", columnDefinition = "jsonb")
    private Map<String, Object> signatures;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "infos", columnDefinition = "jsonb")
    private Map<String, Object> infos;
}
