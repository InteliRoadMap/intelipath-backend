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

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "full_name")
    private String fullName;

    private LocalDate yob;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "create_at")
    private LocalDateTime createAt;

    @Column(name = "update_at")
    private LocalDateTime updateAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private UserRole role = UserRole.STUDENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status")
    private UserStatus userStatus = UserStatus.ACTIVE;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<OauthAccount> oauthAccounts;

//    // 1 User có nhiều records ở bảng con
//    // mappedBy = tên field trong class con trỏ ngược lại User
//    // cascade = ALL: thao tác trên User sẽ ảnh hưởng luôn các bảng con
//    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
//    private List<OauthAccount> oauthAccounts;
//
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<RefreshToken> refreshTokens;

    @PrePersist
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

    @PreUpdate
    public void preUpdate() {
        updateAt = LocalDateTime.now();
    }


}