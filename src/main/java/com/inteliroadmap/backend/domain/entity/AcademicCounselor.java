package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.*;
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

    @Column(name = "university")
    private String university;

    @Column(name = "department")
    private String department;

    @Column(name = "year_of_admission")
    private Integer yearOfAdmission;
}

