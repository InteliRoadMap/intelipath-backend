package com.inteliroadmap.backend.domain.dto.response.student;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * What happened to one item, shown after submission.
 *
 * <p>The explanation is returned whether the student was right or wrong. An
 * assessment that hands back a level and no reasoning is a grade; the sentence
 * explaining why the wrong option was wrong is the only part of this a student
 * learns anything from.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradedItemResultResponse {
    private String id;
    private String topic;
    private int tier;
    /** Null for rubric-graded items, where "correct" is not a yes/no. */
    private Boolean correct;
    private int awarded;
    private int possible;
    /** The key(s) that were right — released only now that the paper is submitted. */
    private List<String> correctKeys;
    private String explanation;
    /** The model's one-sentence note on a written or code answer. */
    private String feedback;
}
