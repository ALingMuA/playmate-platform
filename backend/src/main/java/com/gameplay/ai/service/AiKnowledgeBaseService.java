package com.gameplay.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.ai.domain.AiKnowledgeBase;
import com.gameplay.ai.dto.KnowledgeBaseRequest;
import com.gameplay.ai.dto.KnowledgeBaseView;
import com.gameplay.ai.mapper.AiKnowledgeBaseMapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * AI 知识库领域服务（FR-C23 管理、FR-C17 检索匹配）。
 *
 * <p>匹配算法：按优先级从高到低扫描启用条目，统计用户消息中命中的关键词比例，
 * 命中比例折算置信度，返回置信度最高的条目；无命中返回 null。</p>
 */
@Service
@RequiredArgsConstructor
public class AiKnowledgeBaseService {

    private final AiKnowledgeBaseMapper knowledgeBaseMapper;

    /** 关键词匹配结果 */
    public record MatchResult(AiKnowledgeBase entry, double confidence) {
    }

    /** 新增知识条目（FR-C23） */
    @Transactional
    public KnowledgeBaseView create(Long adminId, KnowledgeBaseRequest req) {
        AiKnowledgeBase entry = new AiKnowledgeBase();
        apply(entry, req);
        entry.setEnabled(1);
        entry.setMaintainedBy(adminId);
        try {
            knowledgeBaseMapper.insert(entry);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_TITLE_EXISTS);
        }
        return toView(entry);
    }

    /** 编辑知识条目 */
    @Transactional
    public KnowledgeBaseView update(Long id, KnowledgeBaseRequest req) {
        AiKnowledgeBase entry = requireEntry(id);
        apply(entry, req);
        entry.setMaintainedBy(adminIdOf(entry, req));
        try {
            knowledgeBaseMapper.updateById(entry);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_TITLE_EXISTS);
        }
        return toView(entry);
    }

    private Long adminIdOf(AiKnowledgeBase entry, KnowledgeBaseRequest req) {
        return entry.getMaintainedBy();
    }

    private void apply(AiKnowledgeBase entry, KnowledgeBaseRequest req) {
        entry.setCategory(req.getCategory());
        entry.setTitle(req.getTitle());
        entry.setKeywords(req.getKeywords());
        entry.setStandardAnswer(req.getStandardAnswer());
        entry.setPriority(req.getPriority() == null ? 0 : req.getPriority());
    }

    /** 启用/停用（FR-C23） */
    @Transactional
    public KnowledgeBaseView setEnabled(Long id, boolean enabled) {
        AiKnowledgeBase entry = requireEntry(id);
        entry.setEnabled(enabled ? 1 : 0);
        knowledgeBaseMapper.updateById(entry);
        return toView(entry);
    }

    /** 删除知识条目 */
    @Transactional
    public void delete(Long id) {
        requireEntry(id);
        knowledgeBaseMapper.deleteById(id);
    }

    /** 分页查询（管理员，全部状态） */
    public Page<KnowledgeBaseView> list(Long adminId, String category, Integer enabled, long page, long size) {
        Page<AiKnowledgeBase> p = knowledgeBaseMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<AiKnowledgeBase>()
                        .eq(StringUtils.hasText(category), AiKnowledgeBase::getCategory, category)
                        .eq(enabled != null, AiKnowledgeBase::getEnabled, enabled)
                        .orderByDesc(AiKnowledgeBase::getPriority)
                        .orderByDesc(AiKnowledgeBase::getId));
        Page<KnowledgeBaseView> vp = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        vp.setRecords(p.getRecords().stream().map(this::toView).toList());
        return vp;
    }

    /**
     * 关键词匹配（FR-C17：优先使用管理员审核生效的知识库内容）。
     *
     * @param content 用户消息内容
     * @return 命中条目与置信度；无可靠命中返回 null
     */
    public MatchResult match(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        List<AiKnowledgeBase> entries = knowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<AiKnowledgeBase>()
                        .eq(AiKnowledgeBase::getEnabled, 1)
                        .orderByDesc(AiKnowledgeBase::getPriority)
                        .orderByDesc(AiKnowledgeBase::getId));
        MatchResult best = null;
        for (AiKnowledgeBase entry : entries) {
            double confidence = matchConfidence(entry, content);
            if (confidence <= 0) {
                continue;
            }
            if (best == null || confidence > best.confidence()) {
                best = new MatchResult(entry, confidence);
            }
        }
        return best;
    }

    /** 命中关键词比例折算置信度：0.55 + 0.40 × 命中比例，封顶 0.95 */
    private double matchConfidence(AiKnowledgeBase entry, String content) {
        List<String> keywords = Arrays.stream(entry.getKeywords().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (keywords.isEmpty()) {
            return 0;
        }
        long hits = keywords.stream().filter(content::contains).count();
        if (hits == 0) {
            return 0;
        }
        return Math.min(0.95D, 0.55D + 0.40D * ((double) hits / keywords.size()));
    }

    private AiKnowledgeBase requireEntry(Long id) {
        AiKnowledgeBase entry = knowledgeBaseMapper.selectById(id);
        if (entry == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }
        return entry;
    }

    private KnowledgeBaseView toView(AiKnowledgeBase entry) {
        return KnowledgeBaseView.builder()
                .id(entry.getId())
                .category(entry.getCategory())
                .title(entry.getTitle())
                .keywords(entry.getKeywords())
                .standardAnswer(entry.getStandardAnswer())
                .priority(entry.getPriority())
                .enabled(entry.getEnabled())
                .maintainedBy(entry.getMaintainedBy())
                .createdAt(entry.getCreatedAt())
                .updatedAt(entry.getUpdatedAt())
                .build();
    }
}
