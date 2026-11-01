package com.documentanalytics.backend;

import com.documentanalytics.backend.service.PrivateFileStorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrivateFileStorageServiceTest {
    @TempDir Path root;

    @Test
    void storesTraversalFilenameOnlyUnderRandomUuidInPrivateRoot() throws Exception {
        var service = new PrivateFileStorageService(root.toString());
        var result = service.store(new MockMultipartFile("file", "../../outside.txt", "text/plain", "Synthetic text".getBytes()));
        assertEquals("outside.txt", result.originalFilename());
        assertEquals(root, root.resolve(result.storageKey()).normalize().getParent());
        assertEquals("Synthetic text", Files.readString(root.resolve(result.storageKey())));
        assertDoesNotThrow(() -> java.util.UUID.fromString(result.storageKey()));
    }

    @Test
    void rejectsEmptyUnsupportedSpoofedAndMalformedUploads() throws Exception {
        var service = new PrivateFileStorageService(root.toString());
        var files = new MockMultipartFile[] {
            new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]),
            new MockMultipartFile("file", "script.exe", "application/pdf", new byte[]{65}),
            new MockMultipartFile("file", "fake.pdf", "application/pdf", "Not a PDF".getBytes()),
            new MockMultipartFile("file", "fake.docx", "application/zip", "Not a ZIP".getBytes()),
            new MockMultipartFile("file", "invalid.txt", "text/plain", new byte[]{(byte)0xff}),
            new MockMultipartFile("file", "binary.txt", "text/plain", new byte[]{0, 1})
        };
        for (var file : files) {
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.store(file)).getStatusCode().value());
        }
        try (var paths = Files.list(root)) { assertEquals(0, paths.count()); }
    }

    @Test
    void rejectsOversizeBeforeOpeningUploadStream() throws Exception {
        var file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getSize()).thenReturn(10L * 1024 * 1024 + 1);
        var service = new PrivateFileStorageService(root.toString());
        assertEquals(413, assertThrows(ResponseStatusException.class, () -> service.store(file)).getStatusCode().value());
        verify(file, never()).getInputStream();
    }
}
