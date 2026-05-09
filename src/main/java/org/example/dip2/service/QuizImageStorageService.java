package org.example.dip2.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.example.dip2.exception.ApiException;
import org.example.dip2.model.Question;
import org.example.dip2.model.Quiz;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class QuizImageStorageService {

    private static final Pattern DATA_URL_PATTERN = Pattern.compile("^data:(image/[-+a-zA-Z0-9.]+);base64,(.+)$");
    private static final String PUBLIC_PREFIX = "/uploads/quiz-images/";

    private final Path imageDirectory;

    public QuizImageStorageService(@Value("${app.storage.uploads-dir:uploads}") String uploadsDir) {
        this.imageDirectory = Paths.get(uploadsDir).toAbsolutePath().normalize().resolve("quiz-images");
    }

    public String normalizeAndStore(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        String trimmed = imageUrl.trim();
        if (trimmed.startsWith(PUBLIC_PREFIX) || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        Matcher matcher = DATA_URL_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported question image format");
        }
        String contentType = matcher.group(1).toLowerCase();
        String extension = extensionFor(contentType);
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(matcher.group(2));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Question image payload is not valid base64");
        }

        String filename = UUID.randomUUID() + "." + extension;
        Path outputPath = imageDirectory.resolve(filename).normalize();
        if (!outputPath.startsWith(imageDirectory)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Question image path is invalid");
        }
        try {
            Files.createDirectories(imageDirectory);
            Files.write(outputPath, decoded, StandardOpenOption.CREATE_NEW);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store question image");
        }
        return PUBLIC_PREFIX + filename;
    }

    public Set<String> collectManagedPaths(Quiz quiz) {
        return quiz.getQuestions().stream()
                .map(Question::getImageUrl)
                .filter(Objects::nonNull)
                .filter(this::isManagedPath)
                .collect(Collectors.toSet());
    }

    public void deleteAll(Set<String> publicPaths) {
        for (String publicPath : publicPaths) {
            delete(publicPath);
        }
    }

    private void delete(String publicPath) {
        if (!isManagedPath(publicPath)) {
            return;
        }
        String relativePath = publicPath.substring("/uploads/".length());
        Path resolved = Paths.get(imageDirectory.getParent().toString()).resolve(relativePath).normalize();
        if (!resolved.startsWith(imageDirectory.getParent())) {
            return;
        }
        try {
            Files.deleteIfExists(resolved);
        } catch (IOException ignored) {
            // Best-effort cleanup for orphaned files.
        }
    }

    private boolean isManagedPath(String publicPath) {
        return publicPath.startsWith(PUBLIC_PREFIX);
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/jpg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported question image type");
        };
    }
}
