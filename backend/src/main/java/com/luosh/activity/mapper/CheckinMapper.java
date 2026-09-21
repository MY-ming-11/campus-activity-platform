package com.luosh.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luosh.activity.entity.Checkin;
import com.luosh.activity.vo.CheckinRosterVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CheckinMapper extends BaseMapper<Checkin> {

    /**
     * 核销台名单：报名记录 + 用户信息 + 签到情况。
     * 用 LEFT JOIN 而不是 JOIN，是因为没签到的人也要出现在名单里——
     * 组织方要看的正是"谁还没来"。
     */
    @Select("""
            SELECT s.id         AS signup_id,
                   s.user_id    AS user_id,
                   s.status     AS status,
                   s.signup_time AS signup_time,
                   u.nickname   AS nickname,
                   u.username   AS username,
                   u.phone      AS phone,
                   c.check_time AS check_time,
                   c.check_type AS check_type
            FROM activity_signup s
            JOIN sys_user u ON u.id = s.user_id
            LEFT JOIN activity_checkin c ON c.signup_id = s.id
            WHERE s.activity_id = #{activityId}
              AND s.status <> 'CANCELED'
            ORDER BY s.signup_time
            """)
    List<CheckinRosterVO> selectRoster(@Param("activityId") Long activityId);
}
