package com.inteliroadmap.backend.exceptions;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** Builds the shared error body so every handler returns the same shape. */
    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String message, WebRequest request) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
        return new ResponseEntity<>(body, status);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException exception, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", exception.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException exception, WebRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", exception.getMessage(), request);
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorResponse> handleForbidden(RuntimeException exception, WebRequest request) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action", request);
    }

    /**
     * Handle ResourceNotFoundException
     * <p>
     * Khi resource không tìm thấy (HTTP 404)
     *
     * @param exception ResourceNotFoundException
     * @param request   WebRequest
     * @return ErrorResponse với status 404
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException exception, WebRequest request) {

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("NOT_FOUND")
                .message(exception.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Handle validation exceptions
     * <p>
     * Khi request body validation thất bại (HTTP 400)
     *
     * @param exception MethodArgumentNotValidException
     * @param request   WebRequest
     * @return ErrorResponse với status 400 và chi tiết validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception, WebRequest request) {

        Map<String, String> validationErrors = new HashMap<>();
        exception.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            validationErrors.put(fieldName, errorMessage);
        });

        String message = "Validation failed";
        List<ObjectError> errors = exception.getBindingResult().getAllErrors();
        if (!errors.isEmpty() && errors.getFirst().getDefaultMessage() != null) {
            message = errors.getFirst().getDefaultMessage();
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("VALIDATION_ERROR")
                .message(message)
                .details(validationErrors.toString())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle invalid JSON field types such as UUID/date values sent as slugs.
     *
     * @param exception JSON parsing exception
     * @param request current request
     * @return JSON error response with HTTP 400
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception, WebRequest request) {
        // Log the parser detail server-side; never echo it back (it can reveal
        // internal field names / types).
        log.warn("GlobalExceptionHandler: Unreadable request body: {}", exception.getMostSpecificCause().getMessage());
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Request body contains invalid or malformed data", request);
    }

    /**
     * Handle constraint violations on path/query parameters (e.g. {@code @Min},
     * {@code @Max} on a {@code @RequestParam} in a {@code @Validated} controller).
     *
     * @param exception constraint violation exception
     * @param request current request
     * @return JSON error response with HTTP 400
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException exception, WebRequest request) {
        String message = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("Request parameter is invalid");
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
    }

    /**
     * Handle method-parameter validation failures raised by Spring 6.1+ for
     * constrained controller-method arguments.
     *
     * @param exception handler-method validation exception
     * @param request current request
     * @return JSON error response with HTTP 400
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request parameter is invalid", request);
    }

    /**
     * Handle uploads that exceed the configured multipart size limit.
     *
     * @param exception max upload size exception
     * @param request current request
     * @return JSON error response with HTTP 413
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception, WebRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                "Uploaded file is too large.", request);
    }

    /**
     * Handle bad client input surfaced as IllegalArgumentException (e.g. a
     * negative page index, or a required file that was missing).
     *
     * @param exception illegal argument exception
     * @param request current request
     * @return JSON error response with HTTP 400
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException exception, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", exception.getMessage(), request);
    }

    /**
     * Handle invalid path/query parameter types such as non-UUID IDs.
     *
     * @param exception type mismatch exception
     * @param request current request
     * @return JSON error response with HTTP 400
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception, WebRequest request) {
        // exception.getMessage() embeds the raw invalid value and the target Java type/class
        // name (internal implementation detail) — log it server-side only, same pattern as
        // handleHttpMessageNotReadableException above.
        log.warn("GlobalExceptionHandler: type mismatch on {}: {}",
                request.getDescription(false).replace("uri=", ""), exception.getMessage());
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                "Request parameter contains invalid data type", request);
    }

    /**
     * Handles invalid or expired refresh tokens.
     *
     * @param exception invalid refresh token exception
     * @param request current request
     * @return JSON error response with HTTP 401
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            ResponseStatusException exception, WebRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(exception.getStatusCode().value())
                .error(exception.getStatusCode().toString())
                .message(exception.getReason())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return new ResponseEntity<>(errorResponse, exception.getStatusCode());
    }

    /**
     * Handle generic exceptions
     * <p>
     * Catch-all handler cho những exception không được handle riêng
     *
     * @param exception Exception
     * @param request   WebRequest
     * @return ErrorResponse với status 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception exception, WebRequest request) {

        // Log the full stack trace server-side for debugging, but never leak the
        // internal message/details to the client.
        log.error("GlobalExceptionHandler: Unhandled exception on {}",
                request.getDescription(false).replace("uri=", ""), exception);

        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred", request);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ErrorResponse {

        /**
         * Thời gian error xảy ra
         */
        private LocalDateTime timestamp;

        /**
         * HTTP status code
         */
        private int status;

        /**
         * Error type (NOT_FOUND, VALIDATION_ERROR, v.v.)
         */
        private String error;

        /**
         * Error message
         */
        private String message;

        /**
         * Chi tiết lỗi (thường là validation errors)
         */
        private String details;

        /**
         * Request path
         */
        private String path;
    }
}
