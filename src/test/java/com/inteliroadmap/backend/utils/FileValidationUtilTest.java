package com.inteliroadmap.backend.utils;

import com.inteliroadmap.backend.exceptions.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Guardrail behaviour for uploaded files (type / size / presence). */
class FileValidationUtilTest {

    @Test
    void validateImage_acceptsPng() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[]{1, 2, 3});
        assertDoesNotThrow(() -> FileValidationUtil.validateImage(file));
    }

    @Test
    void validateImage_rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[0]);
        assertThrows(BadRequestException.class, () -> FileValidationUtil.validateImage(file));
    }

    @Test
    void validateImage_rejectsDisallowedExtension() {
        // An SVG/HTML avatar is a stored-XSS risk and must be rejected.
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.svg", "image/svg+xml", new byte[]{1, 2, 3});
        assertThrows(BadRequestException.class, () -> FileValidationUtil.validateImage(file));
    }

    @Test
    void validateImage_rejectsSpoofedContentTypeOnGoodExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "text/html", new byte[]{1, 2, 3});
        assertThrows(BadRequestException.class, () -> FileValidationUtil.validateImage(file));
    }

    @Test
    void validateImage_rejectsOversizeFile() {
        byte[] tooBig = new byte[(int) FileValidationUtil.MAX_IMAGE_BYTES + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", tooBig);
        assertThrows(BadRequestException.class, () -> FileValidationUtil.validateImage(file));
    }

    @Test
    void validatePdf_acceptsPdf() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "transcript.pdf", "application/pdf", new byte[]{1, 2, 3});
        assertDoesNotThrow(() -> FileValidationUtil.validatePdf(file));
    }

    @Test
    void validatePdf_rejectsImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "transcript.png", "image/png", new byte[]{1, 2, 3});
        assertThrows(BadRequestException.class, () -> FileValidationUtil.validatePdf(file));
    }

    @Test
    void validateAttachment_toleratesBlankContentTypeWithGoodExtension() {
        // Some clients omit the content type; the extension whitelist still guards it.
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.pdf", "", new byte[]{1, 2, 3});
        assertDoesNotThrow(() -> FileValidationUtil.validateAttachment(file));
    }
}
