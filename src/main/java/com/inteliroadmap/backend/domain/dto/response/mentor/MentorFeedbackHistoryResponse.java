package com.inteliroadmap.backend.domain.dto.response.mentor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentorFeedbackHistoryResponse {
    private String id;
    private String initials;
    private String name;
    private String time;
    private String tag;
    private String content;
}
