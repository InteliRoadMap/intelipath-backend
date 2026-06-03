package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.*;
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
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "mentor_id")
    private UUID mentorId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_im_user"))
    private User user;

    @Column(name = "company")
    private String company;

    @Column(name = "industry_focus")
    private String industryFocus;
}
