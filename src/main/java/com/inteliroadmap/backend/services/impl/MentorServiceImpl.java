package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.response.mentor.Metric;
import com.inteliroadmap.backend.domain.dto.response.mentor.NeedsAttention;
import com.inteliroadmap.backend.domain.dto.response.mentor.SkillGap;

import com.inteliroadmap.backend.domain.dto.request.CreateFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateMentorProfileRequest;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorCareerDistributionResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorDashboardMetricsResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorDirectoryResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorFeedbackHistoryResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorPendingReviewResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorProfileResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorProgressReportResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorStudentResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.GithubImportAuditResponse;
import com.inteliroadmap.backend.domain.entity.Feedback;
import com.inteliroadmap.backend.domain.entity.IndustryMentor;
import com.inteliroadmap.backend.domain.entity.PortfolioReviewRequest;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentProgress;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.ReviewStatus;
import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
import com.inteliroadmap.backend.domain.enums.UserRole;
import com.inteliroadmap.backend.domain.enums.UserStatus;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.exceptions.ForbiddenException;
import com.inteliroadmap.backend.repositories.FeedbackRepository;
import com.inteliroadmap.backend.repositories.IndustryMentorRepository;
import com.inteliroadmap.backend.repositories.PortfolioReviewRequestRepository;
import com.inteliroadmap.backend.repositories.SkillNodeRepository;
import com.inteliroadmap.backend.repositories.StudentProgressRepository;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.services.MentorService;
import com.inteliroadmap.backend.services.GithubPortfolioService;
import com.inteliroadmap.backend.services.EmailService;
import com.inteliroadmap.backend.services.RoadmapService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MentorServiceImpl implements MentorService {

    private static final long STUCK_THRESHOLD_DAYS = 7;
    private static final int SKILL_GAP_HIGH_THRESHOLD = 3;

    private final UserRepository userRepository;
    private final FeedbackRepository feedbackRepository;
    private final PortfolioReviewRequestRepository reviewRequestRepository;
    private final StudentRepository studentRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final SkillNodeRepository skillNodeRepository;
    private final IndustryMentorRepository industryMentorRepository;
    private final RoadmapService roadmapService;
    private final EmailService emailService;
    private final GithubPortfolioService githubPortfolioService;

    private User getAuthenticatedMentor() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email);
        if (user == null || user.getRole() != UserRole.MENTOR) {
            throw new ForbiddenException("Mentor not found from token or invalid role");
        }
        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public GithubImportAuditResponse getStudentImportAudit(UUID studentId, String repoUrl) {
        User mentor = getAuthenticatedMentor();
        if (!reviewRequestRepository.existsByStudent_UserIdAndMentor_UserId(studentId, mentor.getUserId())) {
            // Do not turn the mentor directory into a way to probe another student's
            // private projects.  A review request is the student's explicit grant.
            throw new ForbiddenException("This student has not shared a portfolio review with you");
        }
        return githubPortfolioService.getImportAuditForStudent(studentId, repoUrl);
    }

    @Transactional
    @Override
    public MentorDashboardMetricsResponse getDashboardMetrics() {
        log.info("MentorServiceImpl: Get mentor dashboard metrics request received");
        User mentor = getAuthenticatedMentor();

        Double avgTimeSec = reviewRequestRepository.getAverageResponseTimeInSecondsByMentorId(mentor.getUserId());
        String responseTime = formatResponseTime(avgTimeSec);

        long pendingReviews = reviewRequestRepository.countByMentor_UserIdAndStatus(mentor.getUserId(), ReviewStatus.PENDING);
        
        LocalDateTime startOfWeek = LocalDateTime.now().minusDays(7);
        long feedbacksThisWeek = feedbackRepository.countFeedbacksBySenderIdSince(mentor.getUserId(), startOfWeek);

        long menteesCount = reviewRequestRepository.countDistinctStudentsByMentorId(mentor.getUserId());

        return MentorDashboardMetricsResponse.builder()
                .responseTime(responseTime)
                .mentees(menteesCount)
                .pendingReviews(pendingReviews)
                .feedbacks(feedbacksThisWeek)
                .build();
    }

    @Override
    public MentorResponse getWelcomeAlert() {
        User mentor = getAuthenticatedMentor();
        long pending = reviewRequestRepository.countByMentor_UserIdAndStatus(mentor.getUserId(), ReviewStatus.PENDING);
        String message = pending > 0
                ? "Chào ngày mới! Bạn có " + pending + " portfolio đang chờ duyệt."
                : "Chào ngày mới! Hiện không có portfolio nào đang chờ duyệt.";
        return MentorResponse.builder().welcomeAlert(message).build();
    }

    @Override
    public MentorResponse getInsight() {
        User mentor = getAuthenticatedMentor();
        long pending = reviewRequestRepository.countByMentor_UserIdAndStatus(mentor.getUserId(), ReviewStatus.PENDING);
        String insight = pending > 0
                ? "Có " + pending + " sinh viên đang chờ feedback portfolio từ bạn."
                : "Bạn đã xử lý hết các yêu cầu feedback. Làm tốt lắm!";
        return MentorResponse.builder().insight(insight).build();
    }

    private String formatResponseTime(Double seconds) {
        if (seconds == null || seconds == 0) return "< 1h";
        int totalMinutes = (int) (seconds / 60);
        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;
        if (hours == 0) return "< 1h";
        if (hours < 2) return "< 2h";
        if (hours < 4) return "< 4h";
        return hours + "h " + mins + "m";
    }

    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "UN";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return name.substring(0, Math.min(2, name.length())).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    private String getRelativeTime(LocalDateTime time) {
        if (time == null) return "Unknown";
        long days = Duration.between(time, LocalDateTime.now()).toDays();
        if (days == 0) return "Today";
        if (days == 1) return "1 day ago";
        return days + " days ago";
    }

    @Transactional
    @Override
    public Page<MentorPendingReviewResponse> getPendingReviews(Pageable pageable) {
        User mentor = getAuthenticatedMentor();
        Page<PortfolioReviewRequest> requests = reviewRequestRepository.findByMentor_UserIdAndStatus(mentor.getUserId(), ReviewStatus.PENDING, pageable);
        
        List<MentorPendingReviewResponse> dtos = requests.getContent().stream().map(req -> {
            Student student = studentRepository.findById(req.getStudent().getUserId()).orElse(null);
            User studentUser = userRepository.findById(req.getStudent().getUserId()).orElse(null);
            
            String name = studentUser != null ? studentUser.getFullName() : "Unknown";
            String yob = studentUser != null && studentUser.getYob() != null ? String.valueOf(studentUser.getYob()) : "Unknown";
            String university = student != null && student.getUniversityName() != null ? student.getUniversityName() : "Unknown";
            String career = student != null && student.getCareerRole() != null ? student.getCareerRole().getCareerName() : "Unknown";

            return MentorPendingReviewResponse.builder()
                    .id(req.getRequestId().toString())
                    .studentId(req.getStudent().getUserId().toString())
                    .portfolioSlug(student != null ? student.getPortfolioSlug() : null)
                    .studentName(name)
                    .yob(yob)
                    .targetCareer(career)
                    .university(university)
                    .build();
        }).collect(Collectors.toList());
        
        return new PageImpl<>(dtos, pageable, requests.getTotalElements());
    }

    @Transactional
    @Override
    public Page<MentorStudentResponse> getStudentInfos(Pageable pageable) {
        User mentor = getAuthenticatedMentor();
        Page<User> studentUsersPage = reviewRequestRepository.findDistinctStudentsByMentorId(mentor.getUserId(), pageable);
        
        List<UUID> userIds = studentUsersPage.getContent().stream().map(User::getUserId).collect(Collectors.toList());
        List<Student> students = studentRepository.findAllById(userIds);
        Map<UUID, Student> studentMap = students.stream().collect(Collectors.toMap(Student::getUserId, s -> s));

        List<MentorStudentResponse> dtos = new ArrayList<>();

        for(User userSt: studentUsersPage.getContent()) {
            if (userSt.getRole() != UserRole.STUDENT) continue;
            Student student = studentMap.get(userSt.getUserId());
            if (student == null) continue;

            String university = student.getUniversityName() != null ? student.getUniversityName() : "Unknown";
            String career = student.getCareerRole() != null ? student.getCareerRole().getCareerName() : "Unknown";

            dtos.add(MentorStudentResponse.builder()
                    .id(userSt.getUserId().toString())
                    .portfolioSlug(student.getPortfolioSlug())
                    .fullName(userSt.getFullName())
                    .email(userSt.getEmail())
                    .career(career)
                    .university(university)
                    .build());
        }

        return new PageImpl<>(dtos, pageable, studentUsersPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    @Override
    public Page<MentorDirectoryResponse> getMentorDirectory(Pageable pageable) {
        log.info("MentorServiceImpl: Get mentor directory request received");

        Page<User> mentorUsers = userRepository.findByRoleAndUserStatusOrderByFullNameAsc(
                UserRole.MENTOR, UserStatus.ACTIVE, pageable);

        // One lookup for the whole page rather than one per row: the profile is
        // optional here, so a mentor with no industry_mentor row still lists.
        List<UUID> ids = mentorUsers.getContent().stream().map(User::getUserId).toList();
        Map<UUID, IndustryMentor> profiles = industryMentorRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(IndustryMentor::getUserId, p -> p));

        List<MentorDirectoryResponse> dtos = mentorUsers.getContent().stream()
                .map(user -> {
                    IndustryMentor profile = profiles.get(user.getUserId());
                    return MentorDirectoryResponse.builder()
                            .userId(user.getUserId().toString())
                            .fullName(user.getFullName())
                            .email(user.getEmail())
                            .avatarUrl(user.getAvatarUrl())
                            .company(profile != null ? profile.getCompany() : null)
                            .industryFocus(profile != null ? profile.getIndustryFocus() : null)
                            .build();
                })
                .toList();

        return new PageImpl<>(dtos, pageable, mentorUsers.getTotalElements());
    }

    @Transactional
    @Override
    public List<MentorCareerDistributionResponse> getCareerDistribution() {
        User mentor = getAuthenticatedMentor();
        // Use unpaged to get all students for calculation
        Page<User> studentUsersPage = reviewRequestRepository.findDistinctStudentsByMentorId(mentor.getUserId(), Pageable.unpaged());
        
        List<UUID> userIds = studentUsersPage.getContent().stream().map(User::getUserId).collect(Collectors.toList());
        List<Student> students = studentRepository.findAllById(userIds);
        
        Map<String, Long> careerCounts = students.stream()
                .filter(s -> s.getCareerRole() != null)
                .collect(Collectors.groupingBy(s -> s.getCareerRole().getCareerName(), Collectors.counting()));
                
        String[] colors = {"#0088FE", "#00C49F", "#FFBB28", "#FF8042", "#8884d8", "#8dd1e1"};
        int colorIdx = 0;
        
        List<MentorCareerDistributionResponse> distribution = new ArrayList<>();
        long totalCount = students.stream().filter(s -> s.getCareerRole() != null).count();
        
        for (Map.Entry<String, Long> entry : careerCounts.entrySet()) {
            int percentage = totalCount == 0 ? 0 : (int) (entry.getValue() * 100 / totalCount);
            distribution.add(MentorCareerDistributionResponse.builder()
                    .name(entry.getKey())
                    .value(percentage)
                    .color(colors[colorIdx % colors.length])
                    .build());
            colorIdx++;
        }
        
        return distribution;
    }

    @Transactional
    @Override
    public MentorProfileResponse getMentorProfile() {
        User mentor = getAuthenticatedMentor();
        IndustryMentor industryMentor = industryMentorRepository.findById(mentor.getUserId()).orElse(null);
        return MentorProfileResponse.builder()
                .userId(mentor.getUserId())
                .email(mentor.getEmail())
                .fullName(mentor.getFullName())
                .yob(mentor.getYob())
                .bio(mentor.getBio())
                .avatar(mentor.getAvatarUrl())
                .role(mentor.getRole().name())
                .company(industryMentor != null ? industryMentor.getCompany() : null)
                .industryFocus(industryMentor != null ? industryMentor.getIndustryFocus() : null)
                .build();
    }

    @Transactional
    @Override
    public MentorProfileResponse updateMentorProfile(UpdateMentorProfileRequest request) {
        User mentor = getAuthenticatedMentor();
        IndustryMentor industryMentor = industryMentorRepository.findById(mentor.getUserId()).orElse(null);
        if (industryMentor == null) {
            industryMentor = IndustryMentor.builder()
                    .userId(mentor.getUserId())
                    .build();
        }
        industryMentor.setCompany(request.getCompany());
        industryMentor.setIndustryFocus(request.getIndustryFocus());
        industryMentorRepository.save(industryMentor);

        return getMentorProfile();
    }

    @Transactional
    @Override
    public Page<MentorFeedbackHistoryResponse> getFeedbackHistory(Pageable pageable) {
        User mentor = getAuthenticatedMentor();
        // Since FeedbackRepository doesn't have pagination by Sender yet, we'll fetch all and sublist, or just return top elements.
        List<Feedback> sentFeedbacks = feedbackRepository.findBySender_UserIdOrderByCreatedAtDesc(mentor.getUserId());
        
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), sentFeedbacks.size());
        List<Feedback> pageContent = start > sentFeedbacks.size() ? new ArrayList<>() : sentFeedbacks.subList(start, end);

        List<MentorFeedbackHistoryResponse> dtos = pageContent.stream().map(f -> {
            User receiver = f.getReceiver();
            String name = receiver != null ? receiver.getFullName() : "Unknown";
            return MentorFeedbackHistoryResponse.builder()
                    .id(f.getFeedbackId().toString())
                    .initials(getInitials(name))
                    .name(name)
                    .time(getRelativeTime(f.getCreatedAt()))
                    .tag(f.getType() != null ? f.getType().name() : "General")
                    .content(f.getContent())
                    .build();
        }).collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, sentFeedbacks.size());
    }

    @Transactional
    @Override
    public MentorProgressReportResponse getProgressReports() {
        log.info("MentorServiceImpl: Get mentor progress reports request received");
        User mentor = getAuthenticatedMentor();

        List<User> mentorStudents = reviewRequestRepository
                .findDistinctStudentsByMentorId(mentor.getUserId(), Pageable.unpaged())
                .getContent();

        List<com.inteliroadmap.backend.domain.dto.response.mentor.StudentProgress> studentsProgress = new ArrayList<>();
        List<NeedsAttention> needsAttention = new ArrayList<>();
        Map<String, Integer> missingSkillCounts = new HashMap<>();
        int totalCompletedNodes = 0;
        int totalInProgressNodes = 0;

        for (User studentUser : mentorStudents) {
            Student student = studentRepository.findByUserId(studentUser.getUserId());
            if (student == null || student.getCareerRole() == null) {
                continue;
            }

            UUID careerId = student.getCareerRole().getCareerId();
            List<StudentProgress> progressEntries = studentProgressRepository.findByStudent_UserId(studentUser.getUserId());

            int completed = (int) progressEntries.stream()
                    .filter(p -> p.getStatus() == RoadmapStepStatus.COMPLETED)
                    .count();
            int inProgress = (int) progressEntries.stream()
                    .filter(p -> p.getStatus() == RoadmapStepStatus.IN_PROGRESS)
                    .count();
            int totalNodes = skillNodeRepository.findTotalNodeOfRoadmap(careerId);
            int progressPercent = totalNodes == 0 ? 0 : (completed * 100) / totalNodes;

            totalCompletedNodes += completed;
            totalInProgressNodes += inProgress;

            studentsProgress.add(com.inteliroadmap.backend.domain.dto.response.mentor.StudentProgress.builder()
                    .name(studentUser.getFullName())
                    .role(student.getCareerRole().getCareerName())
                    .completed(completed)
                    .total(totalNodes)
                    .progress(progressPercent)
                    .build());

            progressEntries.stream()
                    .filter(p -> p.getStatus() == RoadmapStepStatus.IN_PROGRESS && p.getCreatedAt() != null)
                    .min(Comparator.comparing(StudentProgress::getCreatedAt))
                    .ifPresent(oldest -> {
                        long daysStuck = Duration.between(oldest.getCreatedAt(), LocalDateTime.now()).toDays();
                        if (daysStuck >= STUCK_THRESHOLD_DAYS) {
                            needsAttention.add(NeedsAttention.builder()
                                    .name(studentUser.getFullName())
                                    .issue("Stuck on " + oldest.getSkillNode().getNodeName() + " for " + daysStuck + " days")
                                    .days(daysStuck + "d")
                                    .build());
                        }
                    });

            studentSkillRepository.findMissingSkillsByStudentIdAndCareerId(studentUser.getUserId(), careerId)
                    .forEach(skillName -> missingSkillCounts.merge(skillName, 1, Integer::sum));
        }

        List<Metric> metrics = List.of(
                new Metric("COMPLETED NODES", String.valueOf(totalCompletedNodes), "text-[#00838f]"),
                new Metric("IN PROGRESS", String.valueOf(totalInProgressNodes), "text-[#0ea5e9]")
        );

        List<SkillGap> skillGaps = missingSkillCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(entry -> new SkillGap(
                        entry.getKey(),
                        entry.getValue(),
                        entry.getValue() >= SKILL_GAP_HIGH_THRESHOLD ? "High" : "Medium"))
                .collect(Collectors.toList());

        return MentorProgressReportResponse.builder()
                .metrics(metrics)
                .studentsProgress(studentsProgress)
                .needsAttention(needsAttention)
                .skillGaps(skillGaps)
                .build();
    }

    @Transactional
    @Override
    public MentorResponse submitFeedback(CreateFeedbackRequest request) {
        User sender = getAuthenticatedMentor();
        User receiver = userRepository.findByUserId(request.getReceiverId());
        if (receiver == null) {
            throw new ResourceNotFoundException("Student not found");
        }

        Feedback feedback = new Feedback();
        feedback.setSender(sender);
        feedback.setReceiver(receiver);
        feedback.setSenderName(sender.getFullName());
        feedback.setContent(request.getContent());
        feedback.setType(request.getType());
        feedback = feedbackRepository.save(feedback);

        // Notify the student by email, mirroring counselor feedback. Best-effort: a mail
        // failure must not roll back the saved feedback or the review-request resolution
        // below, so we swallow it here (the email helper otherwise throws).
        try {
            emailService.sendFeedbackNotificationEmail(
                    receiver.getEmail(),
                    receiver.getFullName(),
                    sender.getFullName(),
                    sender.getRole(),
                    request.getContent(),
                    null
            );
        } catch (Exception e) {
            log.error("MentorServiceImpl: failed to send feedback notification email to {}", receiver.getEmail(), e);
        }

        // Auto-resolve pending review requests from this student to this mentor
        List<PortfolioReviewRequest> pendingRequests = reviewRequestRepository.findByMentor_UserIdAndStatus(sender.getUserId(), ReviewStatus.PENDING, Pageable.unpaged()).getContent();
        for (PortfolioReviewRequest req : pendingRequests) {
            if (req.getStudent().getUserId().equals(receiver.getUserId())) {
                req.setStatus(ReviewStatus.REVIEWED);
                req.setResolvedAt(LocalDateTime.now());
                reviewRequestRepository.save(req);
            }
        }

        return MentorResponse.builder().feedback(feedback).build();
    }
}
