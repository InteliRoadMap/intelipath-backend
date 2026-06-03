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
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "counselor_id")
    private UUID counselorId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_ac_user"))
    private User user;

    @Column(name = "university")
    private String university;

    @Column(name = "department")
    private String department;

    @Column(name = "year_of_admission")
    private LocalDate yearOfAdmission;
}
