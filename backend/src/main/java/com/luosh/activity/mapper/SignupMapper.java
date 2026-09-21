package com.luosh.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luosh.activity.entity.Signup;
import com.luosh.activity.vo.MySignupVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SignupMapper extends BaseMapper<Signup> {

    /**
     * 报名落库。
     *
     * 这里故意不用"先查再判断再插入"，因为并发下那三步之间别人可能已经插进去了。
     * 直接交给数据库的唯一索引 uk_activity_user 去判重：
     * 第一次插入成功，重复插入时走 ON DUPLICATE KEY UPDATE 把状态改回 SIGNED
     * （用户取消过再报名就是走这条分支）。
     *
     * 这个方法是幂等的，所以消息表重试时重复调用也不会产生脏数据。
     */
    @Insert("""
            INSERT INTO activity_signup (activity_id, user_id, status, signup_time)
            VALUES (#{activityId}, #{userId}, 'SIGNED', NOW())
            ON DUPLICATE KEY UPDATE status = 'SIGNED', signup_time = NOW()
            """)
    int upsertSignup(@Param("activityId") Long activityId, @Param("userId") Long userId);

    /** 已占用的名额数（已取消的不算）。 */
    @Select("SELECT COUNT(*) FROM activity_signup WHERE activity_id = #{activityId} AND status <> 'CANCELED'")
    long countOccupied(@Param("activityId") Long activityId);

    /** 我的报名，带上活动信息。用一条 JOIN 拿完，避免前端再按 id 逐个查活动。 */
    @Select("""
            SELECT s.id AS signup_id,
                   s.activity_id,
                   s.status,
                   s.signup_time,
                   a.title,
                   a.cover,
                   a.location,
                   a.start_time,
                   a.end_time
            FROM activity_signup s
            JOIN activity a ON a.id = s.activity_id
            WHERE s.user_id = #{userId}
            ORDER BY s.signup_time DESC
            LIMIT 100
            """)
    List<MySignupVO> selectMySignups(@Param("userId") Long userId);
}
