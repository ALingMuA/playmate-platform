package com.gameplay.file.controller;

import com.gameplay.common.api.ApiResponse;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.file.dto.FileView;
import com.gameplay.file.storage.FileStorageService;
import com.gameplay.order.domain.OrderDeliveryEvidence;
import com.gameplay.order.domain.PlayOrder;
import com.gameplay.order.mapper.OrderDeliveryEvidenceMapper;
import com.gameplay.order.mapper.PlayOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件接口（概要设计 8.2 文件分组 /api/files）。
 *
 * <p>上传需登录；访问（GET）为演示环境公开读取，路径含 UUID 不可枚举，
 * 头像/能力证明/评价图片等公开内容可直接展示。投诉证据等敏感文件由业务接口按角色返回 URL。</p>
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;
    private final OrderDeliveryEvidenceMapper deliveryEvidenceMapper;
    private final PlayOrderMapper orderMapper;

    /** 图片上传（FR-P02 能力证明、FR-U18 投诉证据、头像等） */
    @PostMapping("/upload")
    public ApiResponse<FileView> upload(@RequestParam("file") MultipartFile file,
                                        @RequestParam(defaultValue = "general") String category,
                                        Authentication authentication) {
        if ("delivery".equals(category)
                && (authentication == null
                || !(authentication.getPrincipal() instanceof JwtPrincipal principal)
                || (!principal.roles().contains("COMPANION") && !principal.roles().contains("ADMIN")))) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        String url = fileStorageService.store(file, category);
        return ApiResponse.ok(FileView.builder()
                .url(url)
                .fileName(file.getOriginalFilename())
                .sizeBytes(file.getSize())
                .build());
    }

    /** 文件访问（受控读取：路径校验 + UUID 防枚举） */
    @GetMapping("/{category}/{date}/{fileName}")
    public ResponseEntity<Resource> access(@PathVariable String category,
                                           @PathVariable String date,
                                           @PathVariable String fileName,
                                           Authentication authentication) {
        String url = "/api/files/" + category + "/" + date + "/" + fileName;
        if ("delivery".equals(category)) {
            requireDeliveryEvidenceAccess(url, authentication);
        }
        Resource resource = fileStorageService.load(url);
        String contentType = contentTypeOf(fileName);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    private void requireDeliveryEvidenceAccess(String url, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_MISSING);
        }
        List<OrderDeliveryEvidence> evidences = deliveryEvidenceMapper.selectList(
                new LambdaQueryWrapper<OrderDeliveryEvidence>()
                        .eq(OrderDeliveryEvidence::getFileUrl, url)
        );
        if (evidences.isEmpty()) {
            throw new BusinessException(ErrorCode.PERMISSION_DATA_SCOPE_DENIED);
        }
        if (principal.roles().contains("ADMIN")) {
            return;
        }
        for (OrderDeliveryEvidence evidence : evidences) {
            if (evidence.getCompanionUserId().equals(principal.userId())) {
                return;
            }
            PlayOrder order = orderMapper.selectById(evidence.getOrderId());
            if (order != null && order.getUserId().equals(principal.userId())) {
                return;
            }
        }
        throw new BusinessException(ErrorCode.PERMISSION_DATA_SCOPE_DENIED);
    }

    private String contentTypeOf(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
