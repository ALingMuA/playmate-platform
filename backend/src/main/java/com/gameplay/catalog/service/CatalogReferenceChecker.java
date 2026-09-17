package com.gameplay.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.domain.CompanionProfile;
import com.gameplay.companion.domain.CompanionService;
import com.gameplay.companion.dto.GameCapabilityDto;
import com.gameplay.companion.mapper.CompanionProfileMapper;
import com.gameplay.companion.mapper.CompanionServiceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 目录引用校验（FR-M10~M12 删除前护栏）。
 *
 * <p><b>校验范围 = 当前态数据</b>：陪玩服务（{@code companion_service}）与陪玩师资料
 * （{@code companion_profile}）。这两处会直接影响用户端/陪玩师端当下看到的页面。</p>
 *
 * <p><b>刻意排除 = 历史快照</b>：入驻申请（{@code companion_application}）与订单
 * （{@code play_order}）不做校验——否则任何被申请过的游戏/标签将永久不可删除；
 * 历史记录里的名称回落为 ID 属可接受的展示降级。订单维度另有传递性覆盖：
 * 任何下过单的游戏必然存在存活的陪玩服务（服务只支持下架、不支持删除）。
 * 完整取舍见 {@code docs/服务目录与基础数据管理方案.md} §6.4.1。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogReferenceChecker {

    private final CompanionServiceMapper companionServiceMapper;
    private final CompanionProfileMapper companionProfileMapper;
    private final ObjectMapper objectMapper;

    /** 引用该游戏的陪玩服务数量 */
    public long countServicesByGame(Long gameId) {
        return nullToZero(companionServiceMapper.selectCount(new LambdaQueryWrapper<CompanionService>()
                .eq(CompanionService::getGameId, gameId)));
    }

    /** 引用该服务类型的陪玩服务数量 */
    public long countServicesByType(Long serviceTypeId) {
        return nullToZero(companionServiceMapper.selectCount(new LambdaQueryWrapper<CompanionService>()
                .eq(CompanionService::getServiceTypeId, serviceTypeId)));
    }

    /**
     * 引用该标签的陪玩服务数量。
     *
     * <p>{@code tag_ids_json} 是真正的 MySQL JSON 列，用 {@code JSON_CONTAINS} 精确匹配数组元素，
     * 不会出现"12 命中 112"这类子串误判。</p>
     */
    public long countServicesByTag(Long tagId) {
        return nullToZero(companionServiceMapper.selectCount(new LambdaQueryWrapper<CompanionService>()
                .apply("JSON_CONTAINS(tag_ids_json, {0})", String.valueOf(tagId))));
    }

    /** 在资料中认证了该游戏的陪玩师数量（capability_json 为嵌套 JSON，Java 侧解析更精确） */
    public long countProfilesByGame(Long gameId) {
        return countProfiles(cap -> gameId.equals(cap.getGameId()));
    }

    /** 在资料中勾选了该标签的陪玩师数量 */
    public long countProfilesByTag(Long tagId) {
        return countProfiles(cap -> cap.getPositionTagIds() != null && cap.getPositionTagIds().contains(tagId));
    }

    /** 存在引用时抛出 409 CATALOG_IN_USE（消息按对象定制，便于前端直接展示） */
    public void assertDeletable(long references, String message) {
        if (references > 0) {
            throw new BusinessException(ErrorCode.CATALOG_IN_USE, message);
        }
    }

    /** 逐条解析资料的能力 JSON 做判定：陪玩师数量有限，换取精确匹配是划算的 */
    private long countProfiles(java.util.function.Predicate<GameCapabilityDto> match) {
        List<CompanionProfile> profiles = companionProfileMapper.selectList(null);
        long count = 0;
        for (CompanionProfile profile : profiles) {
            if (parseCapabilities(profile.getCapabilityJson()).stream().anyMatch(match)) {
                count++;
            }
        }
        return count;
    }

    private List<GameCapabilityDto> parseCapabilities(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<GameCapabilityDto>>() {});
        } catch (Exception e) {
            // 历史脏数据不应阻断删除操作，记录后按"无引用"处理
            log.warn("陪玩师资料能力JSON解析失败，已跳过引用校验: {}", e.getMessage());
            return List.of();
        }
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
