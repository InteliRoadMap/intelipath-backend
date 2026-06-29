package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.request.CreateFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.request.ModifyFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.response.FeedbackResponse;
import com.inteliroadmap.backend.domain.entity.Feedback;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.FeedbackStatus;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.FeedbackRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.services.EmailService;
import com.inteliroadmap.backend.services.FeedbackService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackServiceImpl implements FeedbackService {

    private final UserRepository userRepository;
    private final FeedbackRepository feedbackRepository;
    private final EmailService emailService;


    /**
     * Retrieves the currently authenticated user based on the security context.
     *
     * @return the authenticated User entity
     * @throws ResourceNotFoundException if the user cannot be found
     */
    private User getAuthenticatedUser() {
        // Get the authenticated email and look up the corresponding user entity
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found from token");
        }
        return user;
    }

    /**
     * Creates and sends a new feedback message from the counselor to a user (typically a student).
     *
     * @param request the request object containing the receiver ID and feedback content
     * @return the CounselorResponse containing the created feedback details
     */
    @Transactional
    @Override
    public FeedbackResponse createFeedback(CreateFeedbackRequest request) {
        log.info("Creating feedback...");

        User sender = getAuthenticatedUser();

        User receiver = userRepository.findByUserId(request.getReceiverId());

        // Initialize a new Feedback entity with sender, receiver, and content details
        Feedback feedback = Feedback.builder()
                .sender(com.inteliroadmap.backend.domain.entity.User.builder().userId(sender.getUserId()).build())
                .receiver(com.inteliroadmap.backend.domain.entity.User.builder().userId(receiver.getUserId()).build())
                .senderName(sender.getFullName())
                .content(request.getContent())
                .type(request.getType())
                .rating(request.getRating())
                .status(FeedbackStatus.NEW)
                .build();

        // Save the newly created feedback to the database
        feedback = feedbackRepository.save(feedback);

        // Send notification email to feedback receiver
        emailService.sendFeedbackNotificationEmail(
                receiver.getEmail(),
                receiver.getFullName(),
                sender.getFullName(),
                request.getContent()
        );

        log.info("New feedback created successfully");
        return toCrudFeedbackResponse(feedback);
    }

    /**
     * Modifies an existing feedback entry.
     *
     * @param request the payload containing the feedback ID and the modified content
     * @return FeedbackResponse containing the updated feedback
     * @throws ResourceNotFoundException if the feedback is not found
     */
    @Transactional
    @Override
    public FeedbackResponse modifyFeedback(ModifyFeedbackRequest request) {
        log.info("Modify feedback request received");
        // Verify user is authenticated
        getAuthenticatedUser();

        // Retrieve existing feedback by ID
        Feedback feedback = feedbackRepository.findByFeedbackId(request.getFeedbackId());
        if (feedback == null) {
            throw new ResourceNotFoundException("Feedback not found");
        }
        
        // Update feedback properties
        feedback.setContent(request.getContent());
        feedback.setType(request.getType());
        feedback.setRating(request.getRating());
        feedback.setStatus(FeedbackStatus.UPDATED);

        log.info("Feedback updated successfully");
        // Save the modifications
        feedback = feedbackRepository.save(feedback);
        return toCrudFeedbackResponse(feedback);
    }

    /**
     * Marks a specific feedback as READ.
     *
     * @param feedbackId the UUID of the feedback
     * @return FeedbackResponse containing the updated feedback
     */
    @Transactional
    @Override
    public FeedbackResponse markReadFeedback(UUID feedbackId) {
        log.info("Mark read feedback request received");
        // Verify user is authenticated
        getAuthenticatedUser();

        // Find the existing feedback
        Feedback feedback = feedbackRepository.findByFeedbackId(feedbackId);
        if (feedback == null) {
            throw new ResourceNotFoundException("Feedback not found");
        }
        
        // Update the status to READ
        feedback.setStatus(FeedbackStatus.READ);
        feedback = feedbackRepository.save(feedback);

        log.info("Feedback marked read successfully");
        return toCrudFeedbackResponse(feedback);
    }

    /**
     * Soft deletes a specific feedback by updating its status to DELETED.
     *
     * @param feedbackId the UUID of the feedback to delete
     * @throws ResourceNotFoundException if the feedback is not found
     */
    @Transactional
    @Override
    public void deleteFeedback(UUID feedbackId) {
        log.info("Delete feedback request received");
        // Verify user is authenticated
        getAuthenticatedUser();

        // Retrieve existing feedback by ID
        Feedback feedback = feedbackRepository.findByFeedbackId(feedbackId);
        if (feedback == null) {
            throw new ResourceNotFoundException("Feedback not found");
        }
        
        // Perform soft delete by setting status to DELETED
        feedback.setStatus(FeedbackStatus.DELETED);
        feedbackRepository.save(feedback);

        log.info("Feedback deleted successfully");
    }

    private FeedbackResponse toCrudFeedbackResponse(Feedback feedback) {
        return FeedbackResponse.builder()
                .feedbackId(feedback.getFeedbackId())
                .senderId(feedback.getSender().getUserId())
                .receiverId(feedback.getReceiver().getUserId())
                .senderName(feedback.getSenderName())
                .content(feedback.getContent())
                .type(feedback.getType())
                .rating(feedback.getRating())
                .status(feedback.getStatus())
                .createdAt(feedback.getCreatedAt())
                .updatedAt(feedback.getUpdatedAt())
                .build();
    }
}
