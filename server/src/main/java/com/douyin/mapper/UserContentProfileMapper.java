package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.UserContentProfile;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface UserContentProfileMapper extends BaseMapper<UserContentProfile> {

    /** Create the row before taking a transaction-scoped row lock. */
    @Insert("INSERT IGNORE INTO t_user_content_profile (user_id) VALUES (#{userId})")
    int insertIfAbsent(@Param("userId") Long userId);

    /** Cross-instance serialization boundary for read-modify-write projections. */
    @Select("SELECT * FROM t_user_content_profile WHERE user_id = #{userId} FOR UPDATE")
    UserContentProfile selectForUpdate(@Param("userId") Long userId);

    /** 批量获取用户的搜索查询记录 */
    @Select("<script>SELECT user_id, recent_search_queries FROM t_user_content_profile " +
            "WHERE user_id IN <foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "AND recent_search_queries IS NOT NULL AND recent_search_queries != ''</script>")
    List<Map<String, Object>> findSearchQueriesByUserIds(@Param("userIds") List<Long> userIds);
}
