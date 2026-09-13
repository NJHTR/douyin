package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.VideoExposure;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface VideoExposureMapper extends BaseMapper<VideoExposure> {

    @Select("SELECT video_id FROM t_video_exposure WHERE user_id = #{userId} " +
            "AND exposure_time >= #{since} ORDER BY exposure_time DESC, id DESC LIMIT #{limit}")
    List<Long> findRecentVideoIds(@Param("userId") Long userId,
                                  @Param("since") LocalDateTime since,
                                  @Param("limit") int limit);
}
