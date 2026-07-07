package com.inteliroadmap.backend.domain.dto.request;

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
    private String message;

    /** Optional URL of a document the user attached, to be read into the prompt. */
    private String fileUrl;
}
