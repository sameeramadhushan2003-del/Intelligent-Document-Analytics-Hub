
package com.documentanalytics.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

@Service
public class PrivateFileStorageService {

    private static final long MAX_FILE_SIZE =
            10L * 1024 * 1024;

    private final Path storageRoot;

    public PrivateFileStorageService(
            @Value("${app.storage.root}") String storageDirectory
    ) throws IOException {

        this.storageRoot = Path.of(storageDirectory)
                .toAbsolutePath()
                .normalize();

        Files.createDirectories(storageRoot);
    }

    public StoredFile store(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please select a non-empty document."
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Maximum file size is 10 MB."
            );
        }

        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null) {
            throw badRequest("Invalid filename.");
        }

        // Remove any directory information from the client filename.
        originalFilename = originalFilename
                .replace('\\', '/');

        originalFilename = originalFilename.substring(
                originalFilename.lastIndexOf('/') + 1
        ).trim();

        if (originalFilename.isBlank()
                || originalFilename.length() > 255
                || originalFilename.chars().anyMatch(
                    Character::isISOControl
                )) {
            throw badRequest("Invalid filename.");
        }

        String lowerName =
                originalFilename.toLowerCase(Locale.ROOT);

        String extension;
        String contentType;

        if (lowerName.endsWith(".pdf")) {
            extension = ".pdf";
            contentType = "application/pdf";

        } else if (lowerName.endsWith(".docx")) {
            extension = ".docx";
            contentType =
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

        } else if (lowerName.endsWith(".txt")) {
            extension = ".txt";
            contentType = "text/plain";

        } else {
            throw badRequest(
                    "Only PDF, DOCX and TXT files are supported."
            );
        }

        Path temporaryFile = null;

        try {
            temporaryFile = Files.createTempFile(
                    storageRoot,
                    "upload-",
                    ".tmp"
            );

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            long totalBytes = 0;

            try (
                InputStream input = file.getInputStream();
                var output = Files.newOutputStream(
                        temporaryFile,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                )
            ) {
                byte[] buffer = new byte[8192];
                int bytesRead;

                while ((bytesRead = input.read(buffer)) != -1) {

                    totalBytes += bytesRead;

                    if (totalBytes > MAX_FILE_SIZE) {
                        throw new ResponseStatusException(
                                HttpStatus.PAYLOAD_TOO_LARGE,
                                "Maximum file size is 10 MB."
                        );
                    }

                    output.write(buffer, 0, bytesRead);
                    digest.update(buffer, 0, bytesRead);
                }
            }

            if (totalBytes == 0) {
                throw badRequest("The uploaded file is empty.");
            }

            validateContents(temporaryFile, extension);

            String storageKey = UUID.randomUUID().toString();

            Path destination = storageRoot.resolve(storageKey);

            Files.move(temporaryFile, destination);

            String sha256Hex =
                    HexFormat.of().formatHex(digest.digest());

            return new StoredFile(
                    storageKey,
                    originalFilename,
                    contentType,
                    totalBytes,
                    sha256Hex
            );

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception
            );

        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to save the uploaded document.",
                    exception
            );

        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException ignored) {
                    // Temporary-file cleanup can be retried later.
                }
            }
        }
    }

    
    public void deleteStoredFile(String storageKey) {

        if (storageKey == null) {
            throw new IllegalArgumentException(
                    "Storage key cannot be null."
            );
        }

        try {
            String normalizedKey =
                    UUID.fromString(storageKey).toString();

            if (!normalizedKey.equals(storageKey)) {
                throw new IllegalArgumentException(
                        "Invalid storage key."
                );
            }

        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid storage key.",
                    exception
            );
        }

        Path filePath = storageRoot
                .resolve(storageKey)
                .normalize();

        if (!storageRoot.equals(filePath.getParent())) {
            throw new IllegalArgumentException(
                    "Storage path is invalid."
            );
        }

        try {
            Files.deleteIfExists(filePath);

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to delete stored document.",
                    exception
            );
        }
    }


    private void validateContents(
            Path path,
            String extension
    ) throws IOException {

        switch (extension) {
            case ".pdf" -> validatePdf(path);
            case ".docx" -> validateDocx(path);
            case ".txt" -> validateText(path);
            default -> throw badRequest(
                    "Unsupported document format."
            );
        }
    }

    private void validatePdf(Path path) throws IOException {

        byte[] expected = "%PDF-".getBytes(
                StandardCharsets.US_ASCII
        );

        try (InputStream input = Files.newInputStream(path)) {

            byte[] actual = input.readNBytes(expected.length);

            if (!Arrays.equals(expected, actual)) {
                throw badRequest(
                        "File content is not a valid PDF signature."
                );
            }
        }
    }

    private void validateDocx(Path path) throws IOException {

        try (ZipFile zip = new ZipFile(path.toFile())) {

            boolean hasContentTypes =
                    zip.getEntry("[Content_Types].xml") != null;

            boolean hasWordDocument =
                    zip.getEntry("word/document.xml") != null;

            boolean hasMacro =
                    zip.getEntry("word/vbaProject.bin") != null;

            if (!hasContentTypes || !hasWordDocument || hasMacro) {
                throw badRequest(
                        "Invalid or unsupported DOCX document."
                );
            }

        } catch (ZipException exception) {
            throw badRequest(
                    "The uploaded file is not a valid DOCX archive."
            );
        }
    }

    private void validateText(Path path) throws IOException {

        var decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try (
            Reader reader = new InputStreamReader(
                    Files.newInputStream(path),
                    decoder
            )
        ) {
            char[] buffer = new char[4096];
            int count;

            while ((count = reader.read(buffer)) != -1) {

                for (int i = 0; i < count; i++) {

                    char character = buffer[i];

                    if (character == '\0'
                            || (character < 32
                            && character != '\n'
                            && character != '\r'
                            && character != '\t')) {

                        throw badRequest(
                                "TXT file contains invalid characters."
                        );
                    }
                }
            }

        } catch (CharacterCodingException exception) {
            throw badRequest(
                    "TXT files must contain valid UTF-8 text."
            );
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    public record StoredFile(
            String storageKey,
            String originalFilename,
            String contentType,
            long fileSizeBytes,
            String sha256Hex
    ) {}
}
