package com.inteliroadmap.backend.domain.entity;

//import com.inteliroadmap.backend.domain.entity.Assessment;
//import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.inteliroadmap.backend.domain.enums.AccountType;
import com.inteliroadmap.backend.domain.enums.UserRole;
import com.inteliroadmap.backend.domain.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Column(nullable = false, unique = true)
    private String email;

    /** Login name for provisioned accounts (staff, FPT students); null for OAuth accounts. */
    @Column(name = "username", length = 100, unique = true)
    private String username;

    /** BCrypt hash; null for OAuth accounts, which have no local credential. */
    @JsonIgnore
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "full_name")
    private String fullName;

    private Integer yob;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private UserRole role = UserRole.STUDENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status")
    private UserStatus userStatus = UserStatus.ACTIVE;

    // @Builder.Default because the column is NOT NULL: without it Lombok's builder
    // ignores this initializer and inserts null.
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType = AccountType.OTHER;

//    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
//    @com.fasterxml.jackson.annotation.JsonIgnore
//    private List<OauthAccount> oauthAccounts;
//
//    // 1 User có nhiều records ở bảng con
//    // mappedBy = tên field trong class con trỏ ngược lại User
//    // cascade = ALL: thao tác trên User sẽ ảnh hưởng luôn các bảng con
//    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
//    private List<OauthAccount> oauthAccounts;
//
//    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
//    @com.fasterxml.jackson.annotation.JsonIgnore
//    private List<RefreshToken> refreshTokens;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (this.userStatus == null) {
            this.userStatus = UserStatus.ACTIVE;
        }

        if (this.role == null) {
            this.role = UserRole.STUDENT    ;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
