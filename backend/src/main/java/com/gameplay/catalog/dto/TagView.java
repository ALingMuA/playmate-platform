package com.gameplay.catalog.dto;

import com.gameplay.catalog.domain.Tag;
import lombok.Data;

/**
 * 标签视图（公开查询返回，供陪玩师入驻、服务编辑时选择能力标签）。
 */
@Data
public class TagView {

    /** 主键ID */
    private Long id;

    /** 标签名称 */
    private String tagName;

    /** 标签分类：POSITION、STYLE、HERO、OTHER */
    private String tagCategory;

    /** 所属游戏ID，0表示通用标签 */
    private Long gameId;

    /** 是否通用标签（gameId=0） */
    private Boolean common;

    /** 排序号，越小越靠前 */
    private Integer sortNo;

    /** 由实体转换为视图对象 */
    public static TagView from(Tag tag) {
        TagView view = new TagView();
        view.setId(tag.getId());
        view.setTagName(tag.getTagName());
        view.setTagCategory(tag.getTagCategory());
        view.setGameId(tag.getGameId());
        view.setCommon(tag.getGameId() != null && tag.getGameId() == 0L);
        view.setSortNo(tag.getSortNo());
        return view;
    }
}
