package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.VideoCollect;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface VideoCollectMapper extends BaseMapper<VideoCollect> {

    /** Insert a collection relation exactly once under uk_user_collect. */
    @Insert("INSERT IGNORE INTO t_video_collect (user_id, video_id, create_time) " +
            "VALUES (#{userId}, #{videoId}, CURRENT_TIMESTAMP)")
    int insertIgnore(@Param("userId") Long userId, @Param("videoId") Long videoId);

    @Select("SELECT video_id, create_time FROM t_video_collect WHERE user_id = #{userId} " +
            "ORDER BY create_time DESC, id DESC LIMIT #{limit}")
    List<VideoCollect> findRecentCollects(@Param("userId") Long userId,
                                          @Param("limit") int limit);

    @org.apache.ibatis.annotations.Delete("DELETE FROM t_video_collect WHERE user_id = #{userId} AND video_id = #{videoId}")
    int deleteByUserAndVideo(@Param("userId") Long userId,
                              @Param("videoId") Long videoId);
}
