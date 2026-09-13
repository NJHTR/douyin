package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.Follow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface FollowMapper extends BaseMapper<Follow> {

    /** Bounded mutual-follow IDs for the FRIENDS feed strategy. */
    @Select("SELECT a.follow_id FROM t_follow a INNER JOIN t_follow b " +
            "ON a.follow_id = b.user_id AND b.follow_id = a.user_id " +
            "WHERE a.user_id = #{userId} ORDER BY a.create_time DESC, a.id DESC LIMIT #{limit}")
    List<Long> findMutualFollowIds(@Param("userId") Long userId, @Param("limit") int limit);
}
