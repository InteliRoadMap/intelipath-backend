package com.inteliroadmap.backend.domain.dto.request;

import com.inteliroadmap.backend.domain.enums.UserRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRoleRequest {

    @NotNull(message = "Role is required")
    private UserRole role;
}
