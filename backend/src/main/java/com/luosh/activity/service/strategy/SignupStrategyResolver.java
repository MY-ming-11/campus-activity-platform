package com.luosh.activity.service.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 选当前用哪套报名策略。
 *
 * 正常部署时由配置 signup.strategy 决定，启动后不变。
 * 做对比实验时开 signup.allow-override，压测程序就能用请求头临时切换策略——
 * 三种方案在同一个 JVM、同一个数据库上连续跑，外部条件完全一致，数据才可比。
 *
 * 生产环境记得把 allow-override 关掉，不然等于给报名接口开了个后门。
 */
@Slf4j
@Component
public class SignupStrategyResolver {

    private final Map<String, SignupStrategy> strategies;
    private final String activeKey;
    private final boolean allowOverride;

    public SignupStrategyResolver(List<SignupStrategy> beans,
                                  @Value("${signup.strategy:redis}") String activeKey,
                                  @Value("${signup.allow-override:false}") boolean allowOverride) {
        this.strategies = beans.stream()
                .sorted(Comparator.comparing(SignupStrategy::key))
                .collect(Collectors.toMap(SignupStrategy::key, Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));
        if (!strategies.containsKey(activeKey)) {
            throw new IllegalStateException(
                    "未知的报名策略 " + activeKey + "，可选值：" + strategies.keySet());
        }
        this.activeKey = activeKey;
        this.allowOverride = allowOverride;

        log.info("报名并发策略 = {} ({})，请求头切换 {}",
                activeKey, strategies.get(activeKey).label(), allowOverride ? "已开启" : "已关闭");
    }

    public SignupStrategy active() {
        return strategies.get(activeKey);
    }

    public SignupStrategy resolve(String overrideKey) {
        if (allowOverride && StringUtils.hasText(overrideKey)) {
            SignupStrategy override = strategies.get(overrideKey);
            if (override == null) {
                throw new IllegalStateException("未知的报名策略 " + overrideKey
                        + "，可选值：" + strategies.keySet());
            }
            return override;
        }
        return active();
    }

    public String activeKey() {
        return activeKey;
    }

    /** 给管理端展示用 */
    public List<Map<String, String>> describe() {
        return strategies.values().stream()
                .map(item -> Map.of("key", item.key(), "label", item.label()))
                .toList();
    }
}
