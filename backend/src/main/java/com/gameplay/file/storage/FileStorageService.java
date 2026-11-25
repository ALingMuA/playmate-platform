package com.gameplay.file.storage;

import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 文件存储服务接口（概要设计 4.7 文件与审计）。
 *
 * <p>校验文件扩展名、MIME 类型、大小、魔数和访问路径，负责头像、能力证明和投诉图片保存；
 * 本地实现 {@link LocalFileStorageService}，可平滑替换为对象存储实现。</p>
 */
public interface FileStorageService {

    /** 显式允许的业务分类白名单 */
    Set<String> ALLOWED_CATEGORIES = Set.of(
            "general", "avatar", "certification", "evidence", "delivery", "activity");

    /** 校验并规范化文件分类：空白默认 general，不在白名单中直接拒绝 */
    static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "general";
        }
        String trimmed = category.trim();
        if (!ALLOWED_CATEGORIES.contains(trimmed)) {
            throw new BusinessException(ErrorCode.FILE_PATH_INVALID, "不支持的文件分类");
        }
        return trimmed;
    }

    /**
     * 保存文件并返回可访问 URL（如 /api/files/general/20260811/uuid.jpg）。
     *
     * @param file     上传文件（非空、大小与类型已校验）
     * @param category 业务分类（general/avatar/certification/evidence/delivery/activity）
     * @return 可访问 URL
     */
    String store(MultipartFile file, String category);

    /**
     * 按 URL 读取文件资源（受控访问；路径穿越已防护）。
     *
     * @param url store 返回的 URL
     * @return 文件资源
     */
    Resource load(String url);
}
