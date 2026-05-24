package com.inteliroadmap.backend.domain.entity;//import com.inteliroadmap.backend.domain.entity.Assessment;
//import com.inteliroadmap.backend.domain.entity.CareerRole;


import com.inteliroadmap.backend.domain.enums.UserRole;
import com.inteliroadmap.backend.domain.enums.UserStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


//ORM - MAPPING CLASS INTO DATABASE
@Entity
@Table(name = "users")

//LOMBOK TO AVOID BOILER-PLATE
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "yob")
    private LocalDate yob;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "university")
    private String university;

    @Column(name = "year_of_admission")
    private LocalDate yearOfAdmission;

    @Column(name = "major")
    private String major;

    @Column(name = "create_at", nullable = false)
    private LocalDateTime createAt;

    @Column(name = "update_at", nullable = false)
    private LocalDateTime updateAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private UserRole role = UserRole.STUDENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status")
    private UserStatus userStatus = UserStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_id", foreignKey = @ForeignKey(name = "fk_users_career"))
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.SET_NULL)
    private CareerRole careerRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", foreignKey = @ForeignKey(name = "fk_users_assessment"))
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.SET_NULL)
    private Assessment assessment;

    // 1 User có nhiều records ở bảng con
    // mappedBy = tên field trong class con trỏ ngược lại User
    // cascade = ALL: thao tác trên User sẽ ảnh hưởng luôn các bảng con
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<OauthAccount> oauthAccounts;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<RefreshToken> refreshTokens;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<PortfolioProject> portfolioProjects;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<ChatSession> chatSessions;

    @PrePersist // Tự động chạy trước khi INSERT
    public void prePersist() {
        createAt = LocalDateTime.now();
        updateAt = LocalDateTime.now();
        if (this.userStatus == null) {
            this.userStatus = UserStatus.ACTIVE;
        }

        if (this.role == null) {
            this.role = UserRole.STUDENT    ;
        }
    }

    @PreUpdate // Tự động chạy trước khi UPDATE
    public void preUpdate() {
        updateAt = LocalDateTime.now();
    }
}