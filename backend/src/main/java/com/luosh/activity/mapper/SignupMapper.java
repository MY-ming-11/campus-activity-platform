package com.luosh.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luosh.activity.entity.Signup;
import com.luosh.activity.vo.MySignupVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 纯插入，冲突时忽略（不抛异常，返回影响行数 0）。
     *
     * 乐观锁策略靠它做同一用户的幂等：并发双击时唯一索引只让一条进来，
     * 另一条返回 0，就不会出现"名额扣了两次但只有一条报名"的情况。
     */
    @Insert("""
            INSERT IGNORE INTO activity_signup (activity_id, user_id, status, signup_time)
            VALUES (#{activityId}, #{userId}, 'SIGNED', NOW())
            """)
    int insertIgnore(@Param("activityId") Long activityId, @Param("userId") Long userId);

    /** 取消过的报名重新激活 */
    @Update("""
            UPDATE activity_signup
            SET status = 'SIGNED', signup_time = NOW()
            WHERE id = #{signupId}
            """)
    int markSigned(@Param("signupId") Long signupId);

    /** 加锁读某条报名，用来拿到最新的提交状态 */
    @Select("""
            SELECT * FROM activity_signup
            WHERE activity_id = #{activityId} AND user_id = #{userId}
            FOR UPDATE
            """)
    Signup selectForUpdate(@Param("activityId") Long activityId, @Param("userId") Long userId);

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
