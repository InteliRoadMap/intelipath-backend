package com.inteliroadmap.backend.domain.dto.response.mentor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualMentorMessageResponse {
    private UUID messageId;
    private UUID sessionId;
    private String role;
    private String content;
    private LocalDateTime createAt;
}
