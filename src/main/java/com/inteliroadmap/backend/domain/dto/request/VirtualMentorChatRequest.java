package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualMentorChatRequest {
    /** The user's message. May be blank when only a document is attached. */
    @Size(max = 8000, message = "Message must not exceed 8000 characters")
    private String message;

    /** Optional URL of a document the user attached, to be read into the prompt. */
    @Size(max = 2048, message = "File URL must not exceed 2048 characters")
    private String fileUrl;
}
