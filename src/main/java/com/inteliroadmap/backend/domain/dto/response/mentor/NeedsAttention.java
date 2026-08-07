package com.inteliroadmap.backend.domain.dto.response.mentor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NeedsAttention {
    private String name;
    private String issue;
    private String days;
}
