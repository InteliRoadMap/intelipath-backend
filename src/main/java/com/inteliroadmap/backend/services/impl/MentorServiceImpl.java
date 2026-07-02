package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.request.CreateFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateMentorProfileRequest;
import com.inteliroadmap.backend.domain.dto.response.MentorResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.*;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.domain.enums.ReviewStatus;
import com.inteliroadmap.backend.domain.enums.UserRole;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.*;
import com.inteliroadmap.backend.services.MentorService;
import com.inteliroadmap.backend.services.RoadmapService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MentorServiceImpl implements MentorService {

    private final UserRepository userRepository;
    private final FeedbackRepository feedbackRepository;
    private final PortfolioReviewRequestRepository reviewRequestRepository;
    private final StudentRepository studentRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final SkillNodeRepository skillNodeRepository;
    private final IndustryMentorRepository industryMentorRepository;
    private final RoadmapService roadmapService;

    private User getAuthenticatedMentor() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email);
        if (user == null || user.getRole() != UserRole.MENTOR) {
            throw new ResourceNotFoundException("Mentor not found from token or invalid role");
        }
        return user;
    }

    @Transactional
    @Override
    public MentorDashboardMetrics getDashboardMetrics() {
        log.info("Get mentor dashboard metrics request received");
        User mentor = getAuthenticatedMentor();

        Double avgRating = feedbackRepository.getAverageRatingBySenderId(mentor.getUserId());
        double rating = avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0;

        Double avgTimeSec = reviewRequestRepository.getAverageResponseTimeInSecondsByMentorId(mentor.getUserId());
        String responseTime = formatResponseTime(avgTimeSec);

        long pendingReviews = reviewRequestRepository.countByMentor_UserIdAndStatus(mentor.getUserId(), ReviewStatus.PENDING);
        
        LocalDateTime startOfWeek = LocalDateTime.now().minusDays(7);
        long feedbacksThisWeek = feedbackRepository.countFeedbacksBySenderIdSince(mentor.getUserId(), startOfWeek);

        long menteesCount = reviewRequestRepository.countDistinctStudentsByMentorId(mentor.getUserId());

        return MentorDashboardMetrics.builder()
                .rating(rating + "/5.0")
                .responseTime(responseTime)
                .mentees(menteesCount)
                .pendingReviews(pendingReviews)
                .feedbacks(feedbacksThisWeek)
                .build();
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
        long days = java.time.Duration.between(time, LocalDateTime.now()).toDays();
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
            String yob = studentUser != null && studentUser.getYob() != null ? String.valueOf(studentUser.getYob().getYear()) : "Unknown";
            String major = student != null && student.getUniversity() != null ? student.getUniversity().getName() : "Unknown";
            String career = student != null && student.getCareerRole() != null ? student.getCareerRole().getCareerName() : "Unknown";
            
            return MentorPendingReviewResponse.builder()
                    .id(req.getRequestId().toString())
                    .studentId(req.getStudent().getUserId().toString())
                    .studentName(name)
                    .yob(yob)
                    .targetCareer(career)
                    .university(major)
                    .build();
        }).collect(Collectors.toList());
        
        return new PageImpl<>(dtos, pageable, requests.getTotalElements());
    }

    @Transactional
    @Override
    public Page<MentorStudentDto> getStudentInfos(Pageable pageable) {
        User mentor = getAuthenticatedMentor();
        Page<User> studentUsersPage = reviewRequestRepository.findDistinctStudentsByMentorId(mentor.getUserId(), pageable);
        
        List<UUID> userIds = studentUsersPage.getContent().stream().map(User::getUserId).collect(Collectors.toList());
        List<Student> students = studentRepository.findAllById(userIds);
        Map<UUID, Student> studentMap = students.stream().collect(Collectors.toMap(Student::getUserId, s -> s));

        List<MentorStudentDto> dtos = new ArrayList<>();

        for(User userSt: studentUsersPage.getContent()) {
            if (userSt.getRole() != UserRole.STUDENT) continue;
            Student student = studentMap.get(userSt.getUserId());
            if (student == null) continue;

            String university = student.getUniversity() != null ? student.getUniversity().getName() : "Unknown";
            String career = student.getCareerRole() != null ? student.getCareerRole().getCareerName() : "Unknown";

            dtos.add(MentorStudentDto.builder()
                    .id(userSt.getUserId().toString())
                    .fullName(userSt.getFullName())
                    .email(userSt.getEmail())
                    .career(career)
                    .university(university)
                    .build());
        }

        return new PageImpl<>(dtos, pageable, studentUsersPage.getTotalElements());
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
    public Page<MentorFeedbackHistoryDto> getFeedbackHistory(Pageable pageable) {
        User mentor = getAuthenticatedMentor();
        // Since FeedbackRepository doesn't have pagination by Sender yet, we'll fetch all and sublist, or just return top elements.
        List<Feedback> sentFeedbacks = feedbackRepository.findBySender_UserIdOrderByCreatedAtDesc(mentor.getUserId());
        
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), sentFeedbacks.size());
        List<Feedback> pageContent = start > sentFeedbacks.size() ? new ArrayList<>() : sentFeedbacks.subList(start, end);

        List<MentorFeedbackHistoryDto> dtos = pageContent.stream().map(f -> {
            User receiver = f.getReceiver();
            String name = receiver != null ? receiver.getFullName() : "Unknown";
            return MentorFeedbackHistoryDto.builder()
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
    public MentorProgressReportDto getProgressReports() {
        User mentor = getAuthenticatedMentor();
        
        // Mock data for Progress Reports as requested by frontend requirements
        List<MentorProgressReportDto.Metric> metrics = Arrays.asList(
                new MentorProgressReportDto.Metric("COMPLETED NODES", "128", "text-[#00838f]"),
                new MentorProgressReportDto.Metric("IN PROGRESS", "45", "text-[#0ea5e9]")
        );

        List<MentorProgressReportDto.StudentProgress> studentsProgress = Arrays.asList(
                new MentorProgressReportDto.StudentProgress("Nguyen The A", "Frontend", 12, 24, 50),
                new MentorProgressReportDto.StudentProgress("Tran B", "Backend", 20, 24, 83)
        );

        List<MentorProgressReportDto.NeedsAttention> needsAttention = Arrays.asList(
                new MentorProgressReportDto.NeedsAttention("Vu Xuan B", "Stuck on React Basics for 2 weeks", "14d")
        );

        List<MentorProgressReportDto.SkillGap> skillGaps = Arrays.asList(
                new MentorProgressReportDto.SkillGap("Docker", 8, "High"),
                new MentorProgressReportDto.SkillGap("Kubernetes", 5, "Medium")
        );

        return MentorProgressReportDto.builder()
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
