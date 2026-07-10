package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.dto.response.admin.AdminCourseMetricResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminUserListItemResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminUserMetricResponse;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.UserStatus;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
public class AdminMapper {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public AdminUserMetricResponse toUserMetricResponse(long total, int growth) {
        return AdminUserMetricResponse.builder()
                .total(total)
                .growth(growth)
                .build();
    }

    public AdminCourseMetricResponse toCourseMetricResponse(long total, String status, int progress) {
        return AdminCourseMetricResponse.builder()
                .total(total)
                .status(status)
                .progress(progress)
                .build();
    }

    public AdminUserListItemResponse toUserListItem(User user) {
        return AdminUserListItemResponse.builder()
                .id(user.getUserId().toString())
                .name(user.getFullName())
                .role(user.getRole().name())
                .status(user.getUserStatus() != null ? user.getUserStatus().name() : UserStatus.ACTIVE.name())
                .joinedDate(user.getCreatedAt().format(DATE_FORMATTER))
                .build();
    }
}
