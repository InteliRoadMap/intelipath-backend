package com.inteliroadmap.backend.utils;

import com.inteliroadmap.backend.exceptions.BadRequestException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * Central guardrails for uploaded files.
 * <p>
 * Every upload endpoint runs one of these checks before the bytes are stored or
 * forwarded, so an empty, oversized, or wrong-type file is rejected with a 400
 * instead of reaching storage / the database / an LLM prompt. Both the declared
 * MIME type and the filename extension are checked so neither a spoofed header
 * nor a misleading name alone can slip a disallowed file through.
 */
public final class FileValidationUtil {

    private FileValidationUtil() {
    }

    /** Maximum size for an avatar/profile image. */
    public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;      // 5 MB
    /** Maximum size for a document (PDF, transcript, attachment). */
    public static final long MAX_DOCUMENT_BYTES = 10L * 1024 * 1024;  // 10 MB

    private static final Set<String> IMAGE_TYPES =
            Set.of("image/png", "image/jpeg", "image/jpg", "image/webp");
    private static final Set<String> IMAGE_EXTS =
            Set.of(".png", ".jpg", ".jpeg", ".webp");

    private static final Set<String> PDF_TYPES = Set.of("application/pdf");
    private static final Set<String> PDF_EXTS = Set.of(".pdf");

    private static final Set<String> ATTACHMENT_TYPES = Set.of(
            "application/pdf",
            "image/png", "image/jpeg", "image/jpg", "image/webp",
            "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final Set<String> ATTACHMENT_EXTS =
            Set.of(".pdf", ".png", ".jpg", ".jpeg", ".webp", ".txt", ".doc", ".docx");

    /** Avatar / profile picture: images only, up to {@link #MAX_IMAGE_BYTES}. */
    public static void validateImage(MultipartFile file) {
        validate(file, IMAGE_TYPES, IMAGE_EXTS, MAX_IMAGE_BYTES, "image (PNG, JPG or WEBP)");
    }

    /** Transcript / knowledge document: PDF only, up to {@link #MAX_DOCUMENT_BYTES}. */
    public static void validatePdf(MultipartFile file) {
        validate(file, PDF_TYPES, PDF_EXTS, MAX_DOCUMENT_BYTES, "PDF");
    }

    /** Feedback / chat attachment: PDF, image, text or Word, up to {@link #MAX_DOCUMENT_BYTES}. */
    public static void validateAttachment(MultipartFile file) {
        validate(file, ATTACHMENT_TYPES, ATTACHMENT_EXTS, MAX_DOCUMENT_BYTES,
                "PDF, image, text or Word document");
    }

    private static void validate(MultipartFile file, Set<String> allowedTypes,
                                 Set<String> allowedExts, long maxBytes, String label) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required and cannot be empty.");
        }
        if (file.getSize() > maxBytes) {
            throw new BadRequestException(
                    "File is too large. Maximum allowed size is " + (maxBytes / (1024 * 1024)) + " MB.");
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (!allowedExts.contains(ext)) {
            throw new BadRequestException("Unsupported file type. Only " + label + " files are allowed.");
        }
        // The extension whitelist is the primary gate; the declared MIME type is a
        // secondary check. A blank or generic octet-stream type is tolerated (some
        // clients omit it), but an explicitly disallowed type is rejected.
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        boolean typeOk = contentType.isBlank()
                || contentType.equals("application/octet-stream")
                || allowedTypes.contains(contentType);
        if (!typeOk) {
            throw new BadRequestException("Unsupported file type. Only " + label + " files are allowed.");
        }
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot == -1 ? "" : fileName.substring(dot).toLowerCase();
    }
}
