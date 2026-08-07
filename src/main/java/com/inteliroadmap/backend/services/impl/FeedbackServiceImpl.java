package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.response.counselor.FeedbackResponse;

import com.inteliroadmap.backend.domain.dto.request.CreateFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.request.ModifyFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.response.FeedbackAttachmentResponse;
import com.inteliroadmap.backend.domain.dto.response.counselor.CounselorFeedbackResponse;
import com.inteliroadmap.backend.domain.entity.Feedback;
import com.inteliroadmap.backend.domain.entity.FeedbackAttachment;
import com.inteliroadmap.backend.utils.FileValidationUtil;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.FeedbackStatus;
import com.inteliroadmap.backend.exceptions.BadRequestException;
import com.inteliroadmap.backend.exceptions.ForbiddenException;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.exceptions.UnauthorizedException;
import com.inteliroadmap.backend.repositories.FeedbackRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.services.EmailService;
import com.inteliroadmap.backend.services.FeedbackService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
            throw new UnauthorizedException("User not found from token");
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
    public FeedbackResponse createFeedback(CreateFeedbackRequest request, List<MultipartFile> files) {
        log.info("FeedbackServiceImpl: Creating feedback...");

        User sender = getAuthenticatedUser();

        User receiver = userRepository.findByUserId(request.getReceiverId());

        // Initialize a new Feedback entity with sender, receiver, and content details
        Feedback feedback = Feedback.builder()
                .sender(User.builder().userId(sender.getUserId()).build())
                .receiver(User.builder().userId(receiver.getUserId()).build())
                .senderName(sender.getFullName())
                .content(request.getContent())
                .type(request.getType())
                .status(FeedbackStatus.NEW)
                .attachments(new ArrayList<>())
                .build();

        if (files != null && !files.isEmpty()) {
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    FileValidationUtil.validateAttachment(file);
                    try {
                        FeedbackAttachment attachment = FeedbackAttachment.builder()
                                .feedback(feedback)
                                .fileName(file.getOriginalFilename())
                                .fileType(file.getContentType())
                                .fileSize(file.getSize())
                                .data(file.getBytes())
                                .build();
                        feedback.getAttachments().add(attachment);
                    } catch (IOException e) {
                        log.error("Failed to read attached file: {}", file.getOriginalFilename(), e);
                        throw new RuntimeException("Failed to read attached file");
                    }
                }
            }
        }

        // Save the newly created feedback to the database
        feedback = feedbackRepository.save(feedback);

        // Send notification email to feedback receiver
        emailService.sendFeedbackNotificationEmail(
                receiver.getEmail(),
                receiver.getFullName(),
                sender.getFullName(),
                sender.getRole(),
                request.getContent(),
                files
        );

        log.info("FeedbackServiceImpl: New feedback created successfully");
        return toCrudFeedbackResponse(feedback);
    }

    /**
     * Replies to a message the authenticated user received.
     *
     * <p>Stored as an ordinary feedback row in the opposite direction rather than
     * a nested thread: the inbox, unread state, notification email and attachment
     * handling then all apply to a reply for free, and nothing needed a new table.
     */
    @Transactional
    @Override
    public FeedbackResponse replyToFeedback(UUID feedbackId, String content) {
        log.info("FeedbackServiceImpl: Reply to feedback {} requested", feedbackId);

        if (content == null || content.isBlank()) {
            throw new BadRequestException("Reply content must not be empty.");
        }

        User sender = getAuthenticatedUser();

        Feedback original = feedbackRepository.findByFeedbackId(feedbackId);
        if (original == null) {
            throw new ResourceNotFoundException("Feedback not found: " + feedbackId);
        }

        // Only the person the message was addressed to may answer it. Without this
        // any authenticated user could post into someone else's conversation just
        // by guessing a feedback id.
        if (original.getReceiver() == null
                || !original.getReceiver().getUserId().equals(sender.getUserId())) {
            throw new ForbiddenException("You can only reply to feedback addressed to you.");
        }

        User receiver = userRepository.findByUserId(original.getSender().getUserId());
        if (receiver == null) {
            throw new ResourceNotFoundException("The original sender no longer exists.");
        }

        Feedback replyRow = feedbackRepository.save(Feedback.builder()
                .sender(User.builder().userId(sender.getUserId()).build())
                .receiver(User.builder().userId(receiver.getUserId()).build())
                .senderName(sender.getFullName())
                .content(content.trim())
                // Same type as the message being answered, so a reply stays in the
                // same filter as the conversation it belongs to.
                .type(original.getType())
                .status(FeedbackStatus.NEW)
                .attachments(new ArrayList<>())
                .build());

        // Replying also settles the original: you have read what you answered.
        if (original.getStatus() == FeedbackStatus.NEW) {
            original.setStatus(FeedbackStatus.READ);
            feedbackRepository.save(original);
        }

        try {
            emailService.sendFeedbackNotificationEmail(
                    receiver.getEmail(),
                    receiver.getFullName(),
                    sender.getFullName(),
                    sender.getRole(),
                    content,
                    null
            );
        } catch (Exception e) {
            // The reply is saved; a mail outage must not lose it or fail the request.
            log.warn("FeedbackServiceImpl: reply {} saved but notification email failed: {}",
                    replyRow.getFeedbackId(), e.getMessage());
        }

        log.info("FeedbackServiceImpl: Reply to feedback {} saved", feedbackId);
        return toCrudFeedbackResponse(replyRow);
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
    public FeedbackResponse modifyFeedback(ModifyFeedbackRequest request, List<MultipartFile> files) {
        log.info("FeedbackServiceImpl: Modify feedback request received");
        // Verify user is authenticated
        getAuthenticatedUser();

        // Retrieve existing feedback by ID
        Feedback feedback = feedbackRepository.findByFeedbackId(request.getFeedbackId());
        if (feedback == null) {
            throw new ResourceNotFoundException("Feedback not found");
        }
        
        // Update feedback properties (updated_at is bumped automatically; the
        // read/unread status is preserved).
        feedback.setContent(request.getContent());
        feedback.setType(request.getType());

        if (files != null && !files.isEmpty()) {
            if (feedback.getAttachments() == null) {
                feedback.setAttachments(new ArrayList<>());
            }
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    FileValidationUtil.validateAttachment(file);
                    try {
                        FeedbackAttachment attachment = FeedbackAttachment.builder()
                                .feedback(feedback)
                                .fileName(file.getOriginalFilename())
                                .fileType(file.getContentType())
                                .fileSize(file.getSize())
                                .data(file.getBytes())
                                .build();
                        feedback.getAttachments().add(attachment);
                    } catch (IOException e) {
                        log.error("Failed to read attached file: {}", file.getOriginalFilename(), e);
                        throw new RuntimeException("Failed to read attached file");
                    }
                }
            }
        }

        log.info("FeedbackServiceImpl: Feedback updated successfully");
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
        log.info("FeedbackServiceImpl: Mark read feedback request received");
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

        log.info("FeedbackServiceImpl: Feedback marked read successfully");
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
        log.info("FeedbackServiceImpl: Delete feedback request received");
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

        log.info("FeedbackServiceImpl: Feedback deleted successfully");
    }

    private FeedbackResponse toCrudFeedbackResponse(Feedback feedback) {
        return FeedbackResponse.builder()
                .feedbackId(feedback.getFeedbackId())
                .senderId(feedback.getSender().getUserId())
                .receiverId(feedback.getReceiver().getUserId())
                .senderName(feedback.getSenderName())
                .receiverName(feedback.getReceiver().getFullName())
                .content(feedback.getContent())
                .type(feedback.getType())
                .status(feedback.getStatus())
                .attachments(
                        feedback.getAttachments() != null ? feedback.getAttachments().stream()
                                .map(att -> FeedbackAttachmentResponse.builder()
                                        .attachmentId(att.getAttachmentId())
                                        .fileName(att.getFileName())
                                        .fileType(att.getFileType())
                                        .fileSize(att.getFileSize())
                                        .build())
                                .collect(java.util.stream.Collectors.toList()) : new java.util.ArrayList<>()
                )
                .createdAt(feedback.getCreatedAt())
                .updatedAt(feedback.getUpdatedAt())
                .build();
    }
}
