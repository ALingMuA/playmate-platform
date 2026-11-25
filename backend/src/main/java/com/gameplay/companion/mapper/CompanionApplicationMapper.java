package com.gameplay.companion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gameplay.companion.domain.CompanionApplication;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 陪玩师入驻申请 Mapper（FR-P01~P04）。
 */
public interface CompanionApplicationMapper extends BaseMapper<CompanionApplication> {

    @Select("SELECT * FROM companion_application WHERE id = #{id} FOR UPDATE")
    CompanionApplication selectByIdForUpdate(@Param("id") Long id);
}
