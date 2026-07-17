package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.response.mentor.MentorDirectoryResponse;
import com.inteliroadmap.backend.services.MentorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The mentor directory, read by students.
 *
 * It lives outside MentorController because that class is
 * {@code @PreAuthorize("hasRole('MENTOR')")} at class level — the exact role that
 * must not be the one calling this.
 *
 * Without it a student could not act on POST /portfolio/request-review at all:
 * that endpoint identifies its mentor by email, and nothing else in the API ever
 * told a student what those emails are.
 */
@RestController
@RequestMapping("/api/v1/mentors")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Mentor Directory", description = "Browse mentors to request a portfolio review from")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('STUDENT')")
public class MentorDirectoryController {

    private final MentorService mentorService;

    @GetMapping
    @Operation(
            summary = "List mentors",
            description = "Active mentors a student can send a portfolio review request to, ordered by name"
    )
    public ResponseEntity<Page<MentorDirectoryResponse>> listMentors(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page index must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100") int size) {
        log.info("MentorDirectoryController: List mentors request received");
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(mentorService.getMentorDirectory(pageable));
    }
}
