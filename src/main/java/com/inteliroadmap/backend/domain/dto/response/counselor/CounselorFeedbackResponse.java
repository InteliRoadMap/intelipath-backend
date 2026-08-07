package com.inteliroadmap.backend.domain.dto.response.counselor;

import com.inteliroadmap.backend.domain.dto.response.FeedbackAttachmentResponse;
import com.inteliroadmap.backend.domain.enums.FeedbackStatus;
import com.inteliroadmap.backend.domain.enums.FeedbackType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CounselorFeedbackResponse {
    private List<Map<String, Object>> students;
    private List<String> careers;
    private Map<String, Object> studentInfo;
    private List<String> missingSkills;
    private List<FeedbackResponse> feedbacks;
    private int roadmapProgress;
    private int totalPages;
    private int currentPage;

}
