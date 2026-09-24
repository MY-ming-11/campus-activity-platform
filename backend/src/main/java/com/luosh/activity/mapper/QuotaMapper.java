package com.luosh.activity.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 并发扣减名额用的原子 SQL。
 *
 * 这几条语句是整个对比实验的核心：必须让数据库或 Redis 来保证原子性。
 * 「先查再改」在并发下一定出错，超卖就是这么来的。
 */
public interface QuotaMapper {

    /**
     * 悲观锁：把活动行锁住，后面的统计和插入都在同一把锁下面完成。
     * 同一时刻只有一个事务能持有这把锁，其余排队等待——代价就是吞吐被串行化。
     */
    @Select("SELECT id FROM activity WHERE id = #{activityId} FOR UPDATE")
    Long lockActivity(@Param("activityId") Long activityId);

    /** 悲观锁方案判断名额用：当前有效报名数 */
    @Select("SELECT COUNT(*) FROM activity_signup WHERE activity_id = #{activityId} AND status <> 'CANCELED'")
    long countOccupiedBySignup(@Param("activityId") Long activityId);

    /**
     * 乐观锁：不锁行，直接做条件更新。
     * 数据库对同一行的 UPDATE 本身是串行的，所以这条语句天然互斥；
     * occupied &lt; quota 放在 WHERE 里，抢满了就更新不到，返回 0 行。
     *
     * @return 影响行数，1 表示抢到，0 表示已满
     */
    @Update("""
            UPDATE activity
            SET occupied = occupied + 1
            WHERE id = #{activityId} AND occupied < quota
            """)
    int tryOccupy(@Param("activityId") Long activityId);

    /** 乐观锁方案的释放：取消报名时把占用数还回去 */
    @Update("""
            UPDATE activity
            SET occupied = occupied - 1
            WHERE id = #{activityId} AND occupied > 0
            """)
    int releaseOccupy(@Param("activityId") Long activityId);

    /**
     * 读当前的已占用数。
     * 必须在 tryOccupy 之后读——事务内能看到自己刚做的更新，读到的是扣减后的值。
     */
    @Select("SELECT occupied FROM activity WHERE id = #{activityId}")
    Integer selectOccupied(@Param("activityId") Long activityId);

    /** 批量取已占用数，给活动列表回填剩余名额用，避免 N 次查询 */
    @Select("""
            <script>
            SELECT activity_id AS activityId, COUNT(*) AS cnt
            FROM activity_signup
            WHERE status &lt;&gt; 'CANCELED' AND activity_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY activity_id
            </script>
            """)
    List<Map<String, Object>> countOccupiedGroupBy(@Param("ids") List<Long> ids);
}
