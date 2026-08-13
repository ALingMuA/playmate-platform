package com.gameplay.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gameplay.auth.entity.Role;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色表 Mapper。
 */
public interface RoleMapper extends BaseMapper<Role> {

    /** 查询用户持有的启用角色 */
    @Select("SELECT r.* FROM role r JOIN user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId} AND r.enabled = 1")
    List<Role> selectRolesByUserId(@Param("userId") Long userId);

    /** 按角色编码查询 */
    @Select("SELECT * FROM role WHERE role_code = #{roleCode} AND enabled = 1 LIMIT 1")
    Role selectByRoleCode(@Param("roleCode") String roleCode);
}
