package com.gameplay.file.storage;

import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本地磁盘文件存储实现（概要设计 4.7）。
 *
 * <p>安全约定：仅允许白名单分类、白名单扩展名、精确匹配的 MIME 与魔数，
 * PNG/JPEG/GIF 还须通过 {@link ImageIO#read} 解码校验；文件名使用 UUID；
 * 读取时对 URL 规范化校验，防止路径穿越。</p>
 */
@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    /** 允许的扩展名白名单 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp");

    private final Path rootDir;
    private final long maxSizeBytes;

    public LocalFileStorageService(
            @Value("${file.storage.dir:./uploads}") String dir,
            @Value("${file.storage.max-size-mb:5}") long maxSizeMb) {
        this.rootDir = Paths.get(dir).toAbsolutePath().normalize();
        this.maxSizeBytes = maxSizeMb * 1024 * 1024;
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建文件存储目录: " + rootDir, e);
        }
    }

    @Override
    public String store(MultipartFile file, String category) {
        String safeCategory = FileStorageService.normalizeCategory(category);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > maxSizeBytes) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    "文件大小不能超过 " + maxSizeBytes / 1024 / 1024 + "MB");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extensionOf(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "仅支持 " + String.join("/", ALLOWED_EXTENSIONS) + " 格式图片");
        }
        String expectedMime = expectedMimeOf(extension);
        String contentType = normalizeContentType(file.getContentType());
        if (contentType == null || !contentType.equals(expectedMime)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "文件类型与扩展名不匹配");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("读取上传文件内容失败: {}", originalName, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件保存失败");
        }
        if (bytes.length == 0) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        if (bytes.length > maxSizeBytes) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    "文件大小不能超过 " + maxSizeBytes / 1024 / 1024 + "MB");
        }
        verifyImageBytes(bytes, extension);

        String day = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path dir = rootDir.resolve(safeCategory).resolve(day).normalize();
        Path target = dir.resolve(fileName).normalize();
        if (!target.startsWith(rootDir)) {
            throw new BusinessException(ErrorCode.FILE_PATH_INVALID);
        }
        try {
            Files.createDirectories(dir);
            Files.write(target, bytes);
        } catch (IOException e) {
            log.error("文件保存失败: {}", originalName, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件保存失败");
        }
        String url = "/api/files/" + safeCategory + "/" + day + "/" + fileName;
        log.info("文件已保存: {} (分类 {}, 大小 {}B)", url, safeCategory, bytes.length);
        return url;
    }

    @Override
    public Resource load(String url) {
        Path file = resolveUrl(url);
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
            }
            return resource;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
    }

    /** URL -> 磁盘路径：仅允许 /api/files/{category}/{date}/{uuid}.{ext}，杜绝路径穿越 */
    private Path resolveUrl(String url) {
        if (url == null || !url.startsWith("/api/files/")) {
            throw new BusinessException(ErrorCode.FILE_PATH_INVALID);
        }
        String relative = url.substring("/api/files/".length());
        String[] segments = relative.split("/", -1);
        if (segments.length != 3
                || !FileStorageService.ALLOWED_CATEGORIES.contains(segments[0])
                || !segments[1].matches("\\d{8}")
                || !segments[2].matches("[0-9a-fA-F-]{32,36}\\.(jpg|jpeg|png|gif|webp)")) {
            throw new BusinessException(ErrorCode.FILE_PATH_INVALID);
        }
        Path target = rootDir.resolve(relative).normalize();
        if (!target.startsWith(rootDir)) {
            throw new BusinessException(ErrorCode.FILE_PATH_INVALID);
        }
        return target;
    }

    private void verifyImageBytes(byte[] bytes, String extension) {
        if (!matchesMagicBytes(bytes, extension)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "文件内容与图片格式不匹配");
        }
        if ("webp".equals(extension)) {
            return;
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "图片内容无效或已损坏");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "图片内容无效或已损坏");
        }
    }

    private boolean matchesMagicBytes(byte[] bytes, String extension) {
        return switch (extension) {
            case "jpg", "jpeg" -> bytes.length >= 3
                    && (bytes[0] & 0xFF) == 0xFF
                    && (bytes[1] & 0xFF) == 0xD8
                    && (bytes[2] & 0xFF) == 0xFF;
            case "png" -> bytes.length >= 8
                    && (bytes[0] & 0xFF) == 0x89
                    && bytes[1] == 'P'
                    && bytes[2] == 'N'
                    && bytes[3] == 'G'
                    && (bytes[4] & 0xFF) == 0x0D
                    && (bytes[5] & 0xFF) == 0x0A
                    && (bytes[6] & 0xFF) == 0x1A
                    && (bytes[7] & 0xFF) == 0x0A;
            case "gif" -> bytes.length >= 6
                    && bytes[0] == 'G'
                    && bytes[1] == 'I'
                    && bytes[2] == 'F'
                    && bytes[3] == '8'
                    && (bytes[4] == '7' || bytes[4] == '9')
                    && bytes[5] == 'a';
            case "webp" -> bytes.length >= 12
                    && bytes[0] == 'R'
                    && bytes[1] == 'I'
                    && bytes[2] == 'F'
                    && bytes[3] == 'F'
                    && bytes[8] == 'W'
                    && bytes[9] == 'E'
                    && bytes[10] == 'B'
                    && bytes[11] == 'P';
            default -> false;
        };
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        int semi = contentType.indexOf(';');
        String base = (semi >= 0 ? contentType.substring(0, semi) : contentType).trim();
        return base.isEmpty() ? null : base.toLowerCase(Locale.ROOT);
    }

    private String expectedMimeOf(String extension) {
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "";
        };
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
