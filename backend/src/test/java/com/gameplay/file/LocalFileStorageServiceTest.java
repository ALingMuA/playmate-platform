package com.gameplay.file;

import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.file.storage.LocalFileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsPathTraversalAndUnexpectedFileNames() {
        LocalFileStorageService storage = new LocalFileStorageService(tempDir.toString(), 5);

        assertInvalid(storage, "/api/files/general/20260905/../../secret.txt");
        assertInvalid(storage, "/api/files/general/2026-09-05/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.png");
        assertInvalid(storage, "/api/files/general/20260905/not-a-uuid.png");
    }

    @Test
    void acceptsUuidBasedImagePathFormat() {
        LocalFileStorageService storage = new LocalFileStorageService(tempDir.toString(), 5);

        assertThatThrownBy(() -> storage.load(
                "/api/files/avatar/20260905/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.png"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_NOT_FOUND);
    }

    private void assertInvalid(LocalFileStorageService storage, String url) {
        assertThatThrownBy(() -> storage.load(url))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_PATH_INVALID);
    }
}
