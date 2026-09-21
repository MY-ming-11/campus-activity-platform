package com.luosh.activity.mapper;

import com.luosh.activity.vo.DashboardVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 看板用的聚合查询。
 * 全部交给数据库做聚合，不要把明细捞到 Java 里再循环统计——
 * 那是面试里最容易被追问"你这写法数据量大了怎么办"的地方。
 */
public interface DashboardMapper {

    @Select("SELECT COUNT(*) FROM activity")
    long countActivities();

    @Select("SELECT COUNT(*) FROM activity WHERE status = #{status}")
    long countActivitiesByStatus(@Param("status") String status);

    @Select("SELECT COUNT(*) FROM activity_signup WHERE status <> 'CANCELED'")
    long countSignups();

    @Select("SELECT COUNT(*) FROM activity_checkin")
    long countCheckins();

    /**
     * 按天统计报名数。返回的是稀疏结果，只有真有报名的那天才有行，
     * 缺的日子由 Service 补 0。
     */
    @Select("""
            SELECT DATE_FORMAT(signup_time, '%Y-%m-%d') AS date,
                   COUNT(*)                            AS count
            FROM activity_signup
            WHERE signup_time >= #{from}
              AND status <> 'CANCELED'
            GROUP BY DATE_FORMAT(signup_time, '%Y-%m-%d')
            ORDER BY date
            """)
    List<DashboardVO.TrendPoint> selectDailySignups(@Param("from") LocalDateTime from);

    /**
     * 各活动的报名与到场统计。
     *
     * LEFT JOIN 上带 s.status <> 'CANCELED' 这个条件，是为了让"已取消"的报名
     * 直接不参与 COUNT，不用在 SELECT 里再套一层 CASE WHEN。
     */
    @Select("""
            SELECT a.id            AS id,
                   a.title         AS title,
                   a.status        AS status,
                   a.start_time    AS start_time,
                   a.quota         AS quota,
                   COUNT(s.id)     AS signed_count,
                   COALESCE(SUM(CASE WHEN s.status = 'CHECKED' THEN 1 ELSE 0 END), 0) AS checked_count
            FROM activity a
            LEFT JOIN activity_signup s
                   ON s.activity_id = a.id AND s.status <> 'CANCELED'
            GROUP BY a.id, a.title, a.status, a.start_time, a.quota
            ORDER BY signed_count DESC, a.id DESC
            LIMIT 10
            """)
    List<DashboardVO.ActivityStatVO> selectActivityStats();
}
