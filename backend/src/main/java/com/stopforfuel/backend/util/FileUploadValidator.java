package com.stopforfuel.backend.util;

import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * Centralized file upload validation to prevent malicious uploads.
 */
public final class FileUploadValidator {

    private FileUploadValidator() {}

    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");

    private static final Set<String> DOCUMENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif",
            "application/pdf");

    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;    // 5 MB
    private static final long MAX_DOCUMENT_SIZE = 10 * 1024 * 1024; // 10 MB

    /**
     * Validate image uploads (logos, proofs, bill photos).
     */
    public static void validateImage(MultipartFile file) {
        validateFile(file, IMAGE_TYPES, MAX_IMAGE_SIZE, "image");
    }

    /**
     * Validate document uploads (PDFs, images).
     */
    public static void validateDocument(MultipartFile file) {
        validateFile(file, DOCUMENT_TYPES, MAX_DOCUMENT_SIZE, "document");
    }

    /**
     * Validate PDF-only uploads.
     */
    public static void validatePdf(MultipartFile file) {
        validateFile(file, Set.of("application/pdf"), MAX_DOCUMENT_SIZE, "PDF");
    }

    private static void validateFile(MultipartFile file, Set<String> allowedTypes, long maxSize, String label) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(
                    String.format("File too large: %d bytes (max %d MB)", file.getSize(), maxSize / (1024 * 1024)));
        }
        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    String.format("Invalid file type: %s. Allowed: %s", contentType, String.join(", ", allowedTypes)));
        }
    }
}
