package com.inteliroadmap.backend.domain.dto.response.admin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserListItemResponse {
    private String id;
    private String email;
    private String name;
    private String role;
    private String status;
    private String joinedDate;
}
