package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "career_roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CareerRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "career_id")
    private UUID careerId;

    @Column(name = "career_name", nullable = false)
    private String careerName;

    @Column(name = "prerequisite")
    private String prerequisite;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
