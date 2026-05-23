@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    private UUID userId;

    @ManyToOne
    @JoinColumn(name = "career_id") // FK trỏ tới career_roles.career_id
    private CareerRole careerRole;

    @ManyToOne
    @JoinColumn(name = "assessment_id") // FK trỏ tới assessment.assessment_id
    private Assessment assessment;

    @Column(nullable = false)
    private String password;

    @Column(name = "full_name")
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    private LocalDate yob;

    @Column(columnDefinition = "TEXT") // TEXT = không giới hạn ký tự
    private String bio;

    private String university;

    @Column(name = "year_of_admission")
    private LocalDate yearOfAdmission;

    private String major;

    @Column(name = "create_at")
    private LocalDateTime createAt;

    @Column(name = "update_at")
    private LocalDateTime updateAt;

    @Column(name = "account_status")
    private Boolean accountStatus;

    @Column(name = "role")
    private String role;

    // 1 User có nhiều records ở bảng con
    // mappedBy = tên field trong class con trỏ ngược lại User
    // cascade = ALL: thao tác trên User sẽ ảnh hưởng luôn các bảng con
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<OauthAccount> oauthAccounts;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<RefreshToken> refreshTokens;

    @PrePersist // Tự động chạy trước khi INSERT
    public void prePersist() {
        createAt = LocalDateTime.now();
        updateAt = LocalDateTime.now();
        accountStatus = true;
        role = "ST";
    }

    @PreUpdate // Tự động chạy trước khi UPDATE
    public void preUpdate() {
        updateAt = LocalDateTime.now();
    }
}