package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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

    @Column(name = "university_name")
    private String universityName;

    @Column(name = "department")
    private String department;

    @Column(name = "admission_date")
    private LocalDate admissionDate;
}

