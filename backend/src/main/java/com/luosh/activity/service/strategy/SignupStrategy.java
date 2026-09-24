package com.luosh.activity.service.strategy;

import com.luosh.activity.entity.Activity;
import com.luosh.activity.vo.SignupResultVO;

import java.util.List;

/**
 * 报名时"怎么保证不超卖"的三种实现。
 *
 * 抽成策略是为了做对比实验：同一套业务逻辑（限流、活动校验、返回结构都一样），
 * 只换并发控制手段，测出来的差异才归因于方案本身。
 *
 * 也可以理解成毕设里的一章：三种方案 + 一组对比数据。
 */
public interface SignupStrategy {

    /** 配置里用的标识 */
    String key();

    /** 中文名，给页面和实验报告用 */
    String label();

    /**
     * 占名额 + 落库。
     * 名额已满抛 BizException；同一用户重复提交返回 success=false（幂等，不算失败）。
     */
    SignupResultVO signup(Activity activity, Long userId);

    /** 取消报名：释放名额 + 把报名记录置为已取消 */
    void cancel(Activity activity, Long userId);

    /** 批量回填剩余名额，给活动列表用 */
    void fillRemaining(List<Activity> activities);
}
