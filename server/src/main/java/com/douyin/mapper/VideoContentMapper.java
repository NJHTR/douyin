package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.VideoContent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface VideoContentMapper extends BaseMapper<VideoContent> {

    @Insert("INSERT IGNORE INTO t_video_content (video_id, extract_status) VALUES (#{videoId}, 0)")
    int insertPendingIfAbsent(@Param("videoId") Long videoId);

    @Select("""
            SELECT video_id
            FROM t_video_content
            WHERE extract_status IN (0, 2)
               OR (extract_status = 3
                   AND update_time < DATE_SUB(NOW(), INTERVAL #{leaseSeconds} SECOND))
            ORDER BY update_time ASC, video_id ASC
            LIMIT #{limit}
            """)
    List<Long> findRecoverableVideoIds(@Param("leaseSeconds") int leaseSeconds,
                                       @Param("limit") int limit);

    @Select("""
            SELECT v.id
            FROM t_video v
            LEFT JOIN t_video_content vc ON vc.video_id = v.id
            WHERE vc.video_id IS NULL AND v.is_delete = 0
            ORDER BY v.id ASC
            LIMIT #{limit}
            """)
    List<Long> findVideoIdsMissingContent(@Param("limit") int limit);

    /** Only one instance can move a ready or expired task into processing. */
    @Update("""
            UPDATE t_video_content
            SET extract_status = 3, update_time = NOW()
            WHERE video_id = #{videoId}
              AND (extract_status IN (0, 2)
                   OR (extract_status = 3
                       AND update_time < DATE_SUB(NOW(), INTERVAL #{leaseSeconds} SECOND)))
            """)
    int tryClaim(@Param("videoId") Long videoId,
                 @Param("leaseSeconds") int leaseSeconds);
}
