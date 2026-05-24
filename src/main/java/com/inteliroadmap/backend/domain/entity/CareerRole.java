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
public class CareerRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "career_id")
    private UUID careerId;

    @Column(name = "role_name", nullable = false)
    private String roleName;

    @Column(name = "descriptiion")
    private String description;
}
