package com.gameplay.file;

import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.file.storage.LocalFileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
        assertInvalid(storage, "/api/files/unknown/20260905/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.png");
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

    @Test
    void storesAndLoadsValidImagesAcrossSupportedFormats() throws IOException {
        LocalFileStorageService storage = new LocalFileStorageService(tempDir.toString(), 5);

        String pngUrl = storage.store(new MockMultipartFile(
                "file", "avatar.png", "image/png", encodeImage("png")), "avatar");
        assertThat(pngUrl).startsWith("/api/files/avatar/").endsWith(".png");
        assertThat(storage.load(pngUrl).exists()).isTrue();

        String jpgUrl = storage.store(new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", encodeImage("jpg")), "   ");
        assertThat(jpgUrl).startsWith("/api/files/general/").endsWith(".jpg");
        assertThat(storage.load(jpgUrl).exists()).isTrue();

        String gifUrl = storage.store(new MockMultipartFile(
                "file", "anim.gif", "image/gif", encodeImage("gif")), "evidence");
        assertThat(gifUrl).startsWith("/api/files/evidence/").endsWith(".gif");
        assertThat(storage.load(gifUrl).exists()).isTrue();

        byte[] minimalWebp = new byte[]{
                'R', 'I', 'F', 'F', 0x0C, 0x00, 0x00, 0x00,
                'W', 'E', 'B', 'P', 'V', 'P', '8', ' ',
                0x00, 0x00, 0x00, 0x00
        };
        String webpUrl = storage.store(new MockMultipartFile(
                "file", "banner.webp", "image/webp", minimalWebp), "activity");
        assertThat(webpUrl).startsWith("/api/files/activity/").endsWith(".webp");
        assertThat(storage.load(webpUrl).exists()).isTrue();
    }

    @Test
    void rejectsSpoofedHtmlSvgAndInvalidMagicBytes() throws IOException {
        LocalFileStorageService storage = new LocalFileStorageService(tempDir.toString(), 5);

        byte[] htmlPayload = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        assertRejectedType(storage, new MockMultipartFile("file", "xss.png", "image/png", htmlPayload), "general");
        assertRejectedType(storage, new MockMultipartFile("file", "xss.html", "text/html", htmlPayload), "general");

        byte[] svgPayload = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        assertRejectedType(storage, new MockMultipartFile("file", "icon.jpg", "image/jpeg", svgPayload), "general");
        assertRejectedType(storage, new MockMultipartFile("file", "icon.svg", "image/svg+xml", svgPayload), "general");

        byte[] truncatedPngMagic = new byte[]{(byte) 0x89, 'P', 'N', 'G'};
        assertRejectedType(storage, new MockMultipartFile("file", "fake.png", "image/png", truncatedPngMagic), "general");

        byte[] corruptPngWithValidHeader = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x01, 0x02, 0x03
        };
        assertRejectedType(storage, new MockMultipartFile("file", "bad.png", "image/png", corruptPngWithValidHeader), "general");

        byte[] validPng = encodeImage("png");
        assertRejectedType(storage, new MockMultipartFile("file", "a.png", null, validPng), "general");
        assertRejectedType(storage, new MockMultipartFile("file", "a.png", "image/jpeg", validPng), "general");
    }

    @Test
    void rejectsInvalidCategoriesAndOversizedFiles() throws IOException {
        LocalFileStorageService storage = new LocalFileStorageService(tempDir.toString(), 1);
        byte[] validPng = encodeImage("png");

        for (String badCategory : new String[]{"delivery/", "delivery.", "../avatar", "unknown", "a/b"}) {
            assertThatThrownBy(() -> storage.store(
                    new MockMultipartFile("file", "ok.png", "image/png", validPng), badCategory))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FILE_PATH_INVALID);
        }

        byte[] tooBig = new byte[1024 * 1024 + 10];
        assertThatThrownBy(() -> storage.store(
                new MockMultipartFile("file", "big.png", "image/png", tooBig), "general"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_TOO_LARGE);
    }

    private byte[] encodeImage(String format) throws IOException {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(img, format, out)).isTrue();
        return out.toByteArray();
    }

    private void assertRejectedType(LocalFileStorageService storage, MockMultipartFile file, String category) {
        assertThatThrownBy(() -> storage.store(file, category))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_TYPE_NOT_ALLOWED);
    }

    private void assertInvalid(LocalFileStorageService storage, String url) {
        assertThatThrownBy(() -> storage.load(url))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_PATH_INVALID);
    }
}
