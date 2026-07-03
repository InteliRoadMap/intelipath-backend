package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "student_education")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentEducation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "education_id")
    private UUID educationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_se_user"))
    private Student user;

    @Column(name = "university", nullable = false)
    private String university;

    @Column(name = "degree")
    private String degree;

    @Column(name = "period", length = 100)
    private String period;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}

