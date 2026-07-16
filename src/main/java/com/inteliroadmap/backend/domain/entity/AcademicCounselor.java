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
@Table(name = "academic_counselor")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicCounselor {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "department")
    private String department;

    @Column(name = "year_of_admission")
    private Integer yearOfAdmission;
}

