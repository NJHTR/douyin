package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * Returns one latest-message row per direct-message partner. The recent
     * window matches the previous service behavior, while unread counts are
     * aggregated in the same statement to avoid three queries per partner.
     */
    @Select("""
            WITH recent AS (
                SELECT id, from_user_id, to_user_id, content, msg_type, is_read, create_time,
                       CASE WHEN from_user_id = #{userId} THEN to_user_id ELSE from_user_id END AS target_user_id
                FROM t_message
                WHERE from_user_id = #{userId} OR to_user_id = #{userId}
                ORDER BY id DESC
                LIMIT #{limit}
            ), ranked AS (
                SELECT id, target_user_id, content, msg_type, is_read, create_time, from_user_id,
                       ROW_NUMBER() OVER (PARTITION BY target_user_id ORDER BY id DESC) AS row_num
                FROM recent
            ), unread AS (
                SELECT from_user_id AS target_user_id, COUNT(*) AS unread_count
                FROM t_message
                WHERE to_user_id = #{userId} AND is_read = 0
                GROUP BY from_user_id
            )
            SELECT r.target_user_id,
                   r.content AS last_message,
                   r.msg_type AS last_msg_type,
                   r.create_time AS last_time,
                   COALESCE(u.unread_count, 0) AS unread_count,
                   r.from_user_id AS last_msg_from_user_id,
                   r.is_read AS last_msg_is_read
            FROM ranked r
            LEFT JOIN unread u ON u.target_user_id = r.target_user_id
            WHERE r.row_num = 1
            ORDER BY r.id DESC
            """)
    List<ConversationSummaryRow> selectConversationSummaries(Long userId, int limit);

    /** 搜索当前用户的消息记录，返回有匹配消息的对方用户ID（去重） */
    @Select("SELECT DISTINCT CASE WHEN from_user_id = #{userId} THEN to_user_id ELSE from_user_id END as uid " +
            "FROM t_message WHERE (from_user_id = #{userId} OR to_user_id = #{userId}) " +
            "AND content LIKE CONCAT('%', #{keyword}, '%') LIMIT 20")
    List<Long> searchChatPartners(Long userId, String keyword);

    /** 搜索通知记录，返回匹配的通知发送者用户ID（去重） */
    @Select("SELECT DISTINCT from_user_id FROM t_notification WHERE user_id = #{userId} " +
            "AND content LIKE CONCAT('%', #{keyword}, '%') LIMIT 20")
    List<Long> searchNotifSenders(Long userId, String keyword);
}
