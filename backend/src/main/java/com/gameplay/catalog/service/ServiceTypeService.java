package com.gameplay.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gameplay.catalog.domain.ServiceType;
import com.gameplay.catalog.dto.ServiceTypeRequest;
import com.gameplay.catalog.mapper.ServiceTypeMapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 陪玩服务类型服务（FR-M11 服务类型管理）。
 *
 * <p>服务类型描述陪玩服务的品类（开黑陪伴、娱乐陪玩、游戏教学等），
 * 供陪玩师发布服务时选择；前台只展示启用类型。</p>
 */
@Service
@RequiredArgsConstructor
public class ServiceTypeService {

    private final ServiceTypeMapper serviceTypeMapper;

    // ==================== 公开查询（前台，仅启用） ====================

    /** 已启用服务类型列表，按排序号、ID 升序 */
    public List<ServiceType> listEnabledTypes() {
        return serviceTypeMapper.selectList(new LambdaQueryWrapper<ServiceType>()
                .eq(ServiceType::getEnabled, 1)
                .orderByAsc(ServiceType::getSortNo)
                .orderByAsc(ServiceType::getId));
    }

    // ==================== 管理操作（FR-M11） ====================

    /** 管理列表：可按名称关键词模糊查询、按启用状态过滤 */
    public List<ServiceType> listTypes(String keyword, Integer enabled) {
        return serviceTypeMapper.selectList(new LambdaQueryWrapper<ServiceType>()
                .like(keyword != null && !keyword.isBlank(), ServiceType::getTypeName, keyword)
                .eq(enabled != null, ServiceType::getEnabled, enabled)
                .orderByAsc(ServiceType::getSortNo)
                .orderByAsc(ServiceType::getId));
    }

    /** 新增服务类型（FR-M11）：名称与编码均需唯一 */
    @Transactional
    public ServiceType createType(ServiceTypeRequest request) {
        String name = request.getTypeName().trim();
        String code = request.getTypeCode().trim().toUpperCase();
        checkNameUnique(name, null);
        checkCodeUnique(code, null);
        ServiceType type = new ServiceType();
        type.setTypeName(name);
        type.setTypeCode(code);
        type.setDescription(request.getDescription() != null ? request.getDescription() : "");
        type.setSortNo(request.getSortNo() != null ? request.getSortNo() : 0);
        type.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        serviceTypeMapper.insert(type);
        return type;
    }

    /** 编辑服务类型（FR-M11）：名称与编码唯一性校验（排除自身）后全量更新 */
    @Transactional
    public ServiceType updateType(Long id, ServiceTypeRequest request) {
        ServiceType type = requireType(id);
        String name = request.getTypeName().trim();
        String code = request.getTypeCode().trim().toUpperCase();
        checkNameUnique(name, id);
        checkCodeUnique(code, id);
        type.setTypeName(name);
        type.setTypeCode(code);
        type.setDescription(request.getDescription() != null ? request.getDescription() : "");
        type.setSortNo(request.getSortNo() != null ? request.getSortNo() : 0);
        type.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        serviceTypeMapper.updateById(type);
        return type;
    }

    /** 删除服务类型（FR-M11）。后续服务模块接入后需校验关联服务引用 */
    @Transactional
    public void deleteType(Long id) {
        requireType(id);
        serviceTypeMapper.deleteById(id);
    }

    /** 按 ID 查询服务类型，不存在时抛出 SERVICE_TYPE_NOT_FOUND */
    public ServiceType requireType(Long id) {
        ServiceType type = serviceTypeMapper.selectById(id);
        if (type == null) {
            throw new BusinessException(ErrorCode.SERVICE_TYPE_NOT_FOUND);
        }
        return type;
    }

    /** 名称唯一性校验 */
    private void checkNameUnique(String name, Long excludeId) {
        Long count = serviceTypeMapper.selectCount(new LambdaQueryWrapper<ServiceType>()
                .eq(ServiceType::getTypeName, name)
                .ne(excludeId != null, ServiceType::getId, excludeId));
        if (count > 0) {
            throw new BusinessException(ErrorCode.SERVICE_TYPE_NAME_EXISTS);
        }
    }

    /** 编码唯一性校验（编码统一转为大写存储） */
    private void checkCodeUnique(String code, Long excludeId) {
        Long count = serviceTypeMapper.selectCount(new LambdaQueryWrapper<ServiceType>()
                .eq(ServiceType::getTypeCode, code)
                .ne(excludeId != null, ServiceType::getId, excludeId));
        if (count > 0) {
            throw new BusinessException(ErrorCode.SERVICE_TYPE_CODE_EXISTS);
        }
    }
}
