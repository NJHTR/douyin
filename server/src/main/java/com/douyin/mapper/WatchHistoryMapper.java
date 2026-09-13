package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.WatchHistory;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;

public interface WatchHistoryMapper extends BaseMapper<WatchHistory> {

    /**
     * Record progress without a read-before-write race.  The unique
     * (user_id, video_id) key serializes concurrent reports and the monotonic
     * fields prevent an older packet from moving progress backwards.
     */
    @Insert("""
            INSERT INTO t_watch_history
                (user_id, video_id, author_user_id, watch_duration, video_duration,
                 finished, repeat_count, traffic_source, session_id, swipe_seconds, last_position)
            VALUES
                (#{userId}, #{videoId}, #{authorUserId}, #{watchDuration}, #{videoDuration},
                 #{finished}, 1, #{trafficSource}, #{sessionId}, #{swipeSeconds}, #{lastPosition})
            ON DUPLICATE KEY UPDATE
                author_user_id = COALESCE(VALUES(author_user_id), author_user_id),
                watch_duration = GREATEST(COALESCE(watch_duration, 0), COALESCE(VALUES(watch_duration), 0)),
                video_duration = CASE
                    WHEN COALESCE(VALUES(video_duration), 0) > 0 THEN VALUES(video_duration)
                    ELSE video_duration
                END,
                finished = GREATEST(COALESCE(finished, 0), COALESCE(VALUES(finished), 0)),
                repeat_count = COALESCE(repeat_count, 1) + CASE
                    WHEN NULLIF(VALUES(session_id), '') IS NOT NULL
                         AND (session_id IS NULL OR session_id <> VALUES(session_id))
                    THEN 1 ELSE 0
                END,
                traffic_source = COALESCE(NULLIF(VALUES(traffic_source), ''), traffic_source),
                session_id = COALESCE(NULLIF(VALUES(session_id), ''), session_id),
                swipe_seconds = GREATEST(COALESCE(swipe_seconds, 0), COALESCE(VALUES(swipe_seconds), 0)),
                last_position = GREATEST(COALESCE(last_position, 0), COALESCE(VALUES(last_position), 0))
            """)
    int upsertProgress(@Param("userId") Long userId,
                       @Param("videoId") Long videoId,
                       @Param("authorUserId") Long authorUserId,
                       @Param("watchDuration") double watchDuration,
                       @Param("videoDuration") double videoDuration,
                       @Param("finished") int finished,
                       @Param("trafficSource") String trafficSource,
                       @Param("sessionId") String sessionId,
                       @Param("swipeSeconds") double swipeSeconds,
                       @Param("lastPosition") double lastPosition);

    /** 获取用户观看历史视频ID，按最近观看时间排序 */
    @Select("SELECT video_id FROM t_watch_history WHERE user_id = #{userId} ORDER BY update_time DESC LIMIT #{offset}, #{limit}")
    List<Long> findHistoryVideoIds(@Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);

    /** Fetch one extra row so the service can determine hasMore without COUNT/OFFSET. */
    @Select("<script>SELECT h.id, h.user_id, h.video_id, h.author_user_id, h.watch_duration, h.video_duration, " +
            "h.finished, h.repeat_count, h.traffic_source, h.session_id, h.swipe_seconds, h.last_position, " +
            "h.create_time, h.update_time FROM t_watch_history h " +
            "INNER JOIN t_video v ON v.id = h.video_id AND v.status = 'APPROVED' AND v.is_delete = 0 " +
            "WHERE h.user_id = #{userId} " +
            "<if test='cursorTime != null and cursorId != null'>" +
            "AND (h.update_time &lt; #{cursorTime} OR (h.update_time = #{cursorTime} AND h.id &lt; #{cursorId})) " +
            "</if>" +
            "ORDER BY h.update_time DESC, h.id DESC LIMIT #{limit}</script>")
    List<WatchHistory> findHistoryCursor(@Param("userId") Long userId,
                                         @Param("cursorTime") LocalDateTime cursorTime,
                                         @Param("cursorId") Long cursorId,
                                         @Param("limit") int limit);

    /** Database-filtered film/TV history with the same stable cursor boundary. */
    @Select("<script>SELECT h.id, h.user_id, h.video_id, h.author_user_id, h.watch_duration, h.video_duration, " +
            "h.finished, h.repeat_count, h.traffic_source, h.session_id, h.swipe_seconds, h.last_position, " +
            "h.create_time, h.update_time FROM t_watch_history h " +
            "INNER JOIN t_video v ON v.id = h.video_id AND v.status = 'APPROVED' AND v.is_delete = 0 " +
            "INNER JOIN t_video_content c ON c.video_id = h.video_id " +
            "WHERE h.user_id = #{userId} " +
            "AND c.text_category IN " +
            "<foreach collection='categories' item='category' open='(' separator=',' close=')'>#{category}</foreach> " +
            "<if test='cursorTime != null and cursorId != null'>" +
            "AND (h.update_time &lt; #{cursorTime} OR (h.update_time = #{cursorTime} AND h.id &lt; #{cursorId})) " +
            "</if>" +
            "ORDER BY h.update_time DESC, h.id DESC LIMIT #{limit}</script>")
    List<WatchHistory> findHistoryOtherCursor(@Param("userId") Long userId,
                                              @Param("categories") Collection<String> categories,
                                              @Param("cursorTime") LocalDateTime cursorTime,
                                              @Param("cursorId") Long cursorId,
                                              @Param("limit") int limit);

    @Select("SELECT video_id FROM t_watch_history WHERE user_id = #{userId} " +
            "AND update_time >= #{since} AND finished = 1 " +
            "ORDER BY update_time DESC, id DESC LIMIT #{limit}")
    List<Long> findRecentFinishedVideoIds(@Param("userId") Long userId,
                                          @Param("since") LocalDateTime since,
                                          @Param("limit") int limit);

    /** Bounded recent watch signals used to calculate per-category feedback. */
    @Select("SELECT video_id, watch_duration, video_duration, swipe_seconds " +
            "FROM t_watch_history WHERE user_id = #{userId} AND update_time >= #{since} " +
            "ORDER BY update_time DESC, id DESC LIMIT #{limit}")
    List<WatchHistory> findRecentCategoryFeedback(@Param("userId") Long userId,
                                                   @Param("since") LocalDateTime since,
                                                   @Param("limit") int limit);

    /** 批量统计候选视频的近期观看人次 */
    @Select("<script>SELECT video_id, COUNT(*) as cnt FROM t_watch_history " +
            "WHERE create_time >= #{since} " +
            "AND video_id IN <foreach collection='videoIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY video_id</script>")
    List<java.util.Map<String, Object>> countRecentWatches(@Param("videoIds") List<Long> videoIds,
                                                            @Param("since") java.time.LocalDateTime since);

    /** 用户近期快速划走的视频ID (用于品类负反馈) */
    @Select("SELECT video_id FROM t_watch_history " +
            "WHERE user_id = #{userId} AND swipe_seconds < #{maxSwipeSeconds} " +
            "AND update_time >= #{since}")
    List<Long> findQuickSkipVideoIds(@Param("userId") Long userId,
                                      @Param("maxSwipeSeconds") double maxSwipeSeconds,
                                      @Param("since") java.time.LocalDateTime since);

    /** 批量计算候选视频的全站平均完播率 */
    @Select("<script>SELECT video_id, " +
            "AVG(CASE WHEN video_duration > 0 THEN watch_duration / video_duration ELSE 0 END) as avg_comp " +
            "FROM t_watch_history " +
            "WHERE video_id IN <foreach collection='videoIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY video_id</script>")
    List<java.util.Map<String, Object>> avgCompletionRate(@Param("videoIds") List<Long> videoIds);

    /** 找到最近看过指定视频的用户ID (协同搜索) */
    @Select("SELECT DISTINCT user_id FROM t_watch_history " +
            "WHERE video_id = #{videoId} AND create_time >= #{since} " +
            "LIMIT #{limit}")
    List<Long> findRecentWatchersOfVideo(@Param("videoId") Long videoId,
                                          @Param("since") java.time.LocalDateTime since,
                                          @Param("limit") int limit);

    /** 计算单个视频的行为指标 (用于标签置信度校准) */
    @Select("SELECT " +
            "AVG(CASE WHEN video_duration > 0 THEN LEAST(1.0, watch_duration / video_duration) ELSE 0 END) as avg_completion, " +
            "SUM(CASE WHEN watch_duration < 3 THEN 1 ELSE 0 END) * 1.0 / COUNT(*) as bounce_rate, " +
            "COUNT(*) as view_count " +
            "FROM t_watch_history " +
            "WHERE video_id = #{videoId} AND create_time >= #{since}")
    java.util.Map<String, Object> computeVideoBehaviorMetrics(@Param("videoId") Long videoId,
                                                               @Param("since") java.time.LocalDateTime since);

    /** 批量统计共观对 (videoA → videoB): 同一session内先后观看的用户数 */
    @Select("<script>SELECT prev_video_id as video_a, next_video_id as video_b, " +
            "COUNT(DISTINCT user_id) as pair_count " +
            "FROM t_watch_history " +
            "WHERE create_time >= #{since} " +
            "AND prev_video_id IS NOT NULL AND prev_video_id != 0 " +
            "AND video_id IN <foreach collection='videoIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY prev_video_id, video_id " +
            "HAVING pair_count >= #{minPairs} " +
            "ORDER BY pair_count DESC LIMIT #{limit}</script>")
    List<java.util.Map<String, Object>> findCoWatchPairs(@Param("videoIds") List<Long> videoIds,
                                                           @Param("since") java.time.LocalDateTime since,
                                                           @Param("minPairs") int minPairs,
                                                           @Param("limit") int limit);
}
