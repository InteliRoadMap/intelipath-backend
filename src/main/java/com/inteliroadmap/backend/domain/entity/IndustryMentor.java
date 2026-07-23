package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "industry_mentor")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndustryMentor {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "company")
    private String company;

    @Column(name = "industry_focus")
    private String industryFocus;
}

