package com.gameplay.file.storage;

import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
 * <p>安全约定：只允许白名单扩展名与对应 MIME；文件名使用 UUID，不保留原始文件名；
 * 按日期分目录；读取时对 URL 规范化校验，防止路径穿越。</p>
 */
@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    /** 允许的扩展名 -> MIME 前缀白名单 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp");

    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");

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
        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_MIME.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "文件类型与扩展名不匹配");
        }

        String safeCategory = (category == null || category.isBlank())
                ? "general" : category.replaceAll("[^a-zA-Z0-9_-]", "");
        String day = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path dir = rootDir.resolve(safeCategory).resolve(day);
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(fileName).toFile());
        } catch (IOException e) {
            log.error("文件保存失败: {}", originalName, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件保存失败");
        }
        String url = "/api/files/" + safeCategory + "/" + day + "/" + fileName;
        log.info("文件已保存: {} (分类 {}, 大小 {}B)", url, safeCategory, file.getSize());
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
        Path target = rootDir.resolve(relative).normalize();
        if (!target.startsWith(rootDir)) {
            throw new BusinessException(ErrorCode.FILE_PATH_INVALID);
        }
        return target;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
