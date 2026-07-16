package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "university_id", foreignKey = @ForeignKey(name = "fk_ac_university"))
    private University university;

    @Column(name = "department")
    private String department;

    @Column(name = "year_of_admission")
    private Integer yearOfAdmission;
}

