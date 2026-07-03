package com.inteliroadmap.backend.domain.entity;

import com.inteliroadmap.backend.domain.enums.ReviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "portfolio_review_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioReviewRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "request_id")
    private UUID requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false, foreignKey = @ForeignKey(name = "fk_prr_student"))
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_id", nullable = false, foreignKey = @ForeignKey(name = "fk_prr_mentor"))
    private User mentor;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private ReviewStatus status;

    @Column(name = "create_at", nullable = false)
    private LocalDateTime createAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    public void prePersist() {
        if (createAt == null) {
            createAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ReviewStatus.PENDING;
        }
    }
}
