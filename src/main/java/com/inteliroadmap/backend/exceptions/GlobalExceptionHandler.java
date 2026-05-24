package com.inteliroadmap.backend.exceptions;

import com.inteliroadmap.backend.domain.dto.response.ApiResponse;
import com.inteliroadmap.backend.exceptions.enums.ErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Object>> handleAppException(AppException ex) {

        ErrorCode errorCode = ex.getErrorCode();

        ApiResponse<Object> response =
                ApiResponse.error(
                        errorCode.getStatus(),
                        errorCode.getMessage()
                );

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(response);
    }
}
