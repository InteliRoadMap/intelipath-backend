package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "assessment")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assessment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "assessment_id")
    private UUID assessmentId;

    @Column(name = "career")
    private String career;

    @Column(name = "question_answers", columnDefinition = "jsonb")
    private String questionAnswers;

    @Column(name = "grade")
    private Integer grade;
}
