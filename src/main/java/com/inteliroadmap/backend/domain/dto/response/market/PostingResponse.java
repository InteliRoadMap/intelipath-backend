package com.inteliroadmap.backend.domain.dto.response.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostingResponse {
    private String id;
    private String title;
    private String location;
    private String salary;
    /** As the posting words it, e.g. "10 months" — not normalised, not invented. */
    private String experience;
    /** Out to the original ad. The whole point: the claim is checkable at source. */
    private String link;
    /** ISO date, or null when the scrape did not capture one. */
    private String postedDate;
    /** FRESHER | JUNIOR | MID | SENIOR | UNKNOWN, or null before classification. */
    private String seniority;
}
