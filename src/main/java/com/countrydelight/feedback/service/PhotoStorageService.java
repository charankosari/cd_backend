package com.countrydelight.feedback.service;

import com.countrydelight.feedback.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Stores issue photos on local disk and serves them under /uploads/**.
 * In production this would be a pre-signed S3/GCS upload; the API shape stays the same.
 */
@Service
public class PhotoStorageService {

    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/heic", "heic");

    private final Path root;

    public PhotoStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create upload dir " + root, e);
        }
    }

    public String store(Long feedbackId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "Photo file is empty");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String ext = ALLOWED.get(contentType);
        if (ext == null) {
            throw ApiException.badRequest("UNSUPPORTED_FILE", "Photo must be JPEG, PNG, WEBP or HEIC");
        }
        String name = "feedback-" + feedbackId + "-" + UUID.randomUUID() + "." + ext;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, root.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save photo", e);
        }
        return "/uploads/" + name;
    }
}
