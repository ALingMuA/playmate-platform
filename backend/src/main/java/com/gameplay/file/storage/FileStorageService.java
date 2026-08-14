package com.gameplay.file.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务接口（概要设计 4.7 文件与审计）。
 *
 * <p>校验文件扩展名、MIME 类型、大小和访问路径，负责头像、能力证明和投诉图片保存；
 * 本地实现 {@link LocalFileStorageService}，可平滑替换为对象存储实现。</p>
 */
public interface FileStorageService {

    /**
     * 保存文件并返回可访问 URL（如 /api/files/20260811/uuid.jpg）。
     *
     * @param file     上传文件（非空、大小与类型已校验）
     * @param category 业务分类（avatar/certification/evidence/general），用于目录区分
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
