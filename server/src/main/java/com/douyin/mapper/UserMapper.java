package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface UserMapper extends BaseMapper<User> {

    /** Atomically adjust the creator's aggregate likes. */
    @Update("UPDATE t_user SET total_favorited = " +
            "GREATEST(0, COALESCE(total_favorited, 0) + #{delta}) " +
            "WHERE uid = #{userId} AND is_delete = 0")
    int incrementTotalFavorited(@Param("userId") Long userId, @Param("delta") int delta);

    @Select("SELECT * FROM t_user WHERE is_delete = 0 ORDER BY RAND() LIMIT 30")
    List<User> selectRandomFriends();

    @Select("SELECT * FROM t_user WHERE is_delete = 0 AND (nickname LIKE CONCAT('%', #{keyword}, '%') OR unique_id LIKE CONCAT('%', #{keyword}, '%')) LIMIT 20")
    List<User> searchByKeyword(String keyword);
}
