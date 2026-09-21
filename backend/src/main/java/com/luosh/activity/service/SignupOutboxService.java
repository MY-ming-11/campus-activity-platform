package com.luosh.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luosh.activity.entity.QuotaOutbox;
import com.luosh.activity.mapper.QuotaOutboxMapper;
import com.luosh.activity.mapper.SignupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 本地消息表：把"Redis 已经扣掉名额"和"数据库最终写入报名记录"这两件事解耦。
 *
 * 为什么要这一步？抢名额的瞬间写请求是尖峰，如果每次都同步写数据库，
 * 数据库就成了瓶颈。这里先把消息写下来，由后台按自己的节奏落库，把尖峰摊平。
 * 代价是最终一致——用户点完报名到"我的报名"里能看到，中间有几百毫秒的窗口。
 *
 * 补偿逻辑：一条消息重试 5 次还失败，就把 Redis 名额退回去，
 * 避免出现"名额被扣了但永远没报名成功"这种最坏情况。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignupOutboxService {

    private static final String PENDING = "PENDING";
    private static final String DONE = "DONE";
    private static final String FAILED = "FAILED";
    private static final int MAX_RETRY = 5;
    private static final int BATCH_SIZE = 200;

    private final QuotaOutboxMapper outboxMapper;
    private final SignupMapper signupMapper;
    private final QuotaService quotaService;

    /** 名额预扣成功后立刻记一条待落库的消息。 */
    public void enqueue(Long activityId, Long userId) {
        QuotaOutbox outbox = new QuotaOutbox();
        outbox.setActivityId(activityId);
        outbox.setUserId(userId);
        outbox.setStatus(PENDING);
        outbox.setRetryCount(0);
        outboxMapper.insert(outbox);
    }

    /**
     * 消费待落库的消息。
     * 用固定间隔轮询而不是 @Async 立即执行，是为了让落库只有一条代码路径，
     * 好排查也好压测——压测时能直接从这张表看出积压情况。
     */
    @Scheduled(fixedDelay = 200)
    public void consume() {
        List<QuotaOutbox> pending = outboxMapper.selectList(new LambdaQueryWrapper<QuotaOutbox>()
                .eq(QuotaOutbox::getStatus, PENDING)
                .lt(QuotaOutbox::getRetryCount, MAX_RETRY)
                .orderByAsc(QuotaOutbox::getId)
                .last("LIMIT " + BATCH_SIZE));

        for (QuotaOutbox outbox : pending) {
            try {
                signupMapper.upsertSignup(outbox.getActivityId(), outbox.getUserId());
                updateStatus(outbox.getId(), DONE, outbox.getRetryCount());
            } catch (Exception e) {
                handleFailure(outbox, e);
            }
        }
    }

    private void handleFailure(QuotaOutbox outbox, Exception e) {
        int retry = outbox.getRetryCount() + 1;
        if (retry >= MAX_RETRY) {
            boolean released = quotaService.release(outbox.getActivityId(), outbox.getUserId());
            updateStatus(outbox.getId(), FAILED, retry);
            log.error("落库重试 {} 次仍失败，已回滚名额 activityId={} userId={} released={}",
                    MAX_RETRY, outbox.getActivityId(), outbox.getUserId(), released, e);
        } else {
            updateStatus(outbox.getId(), PENDING, retry);
            log.warn("落库失败，第 {} 次重试 activityId={} userId={}",
                    retry, outbox.getActivityId(), outbox.getUserId(), e);
        }
    }

    private void updateStatus(Long id, String status, int retryCount) {
        QuotaOutbox update = new QuotaOutbox();
        update.setId(id);
        update.setStatus(status);
        update.setRetryCount(retryCount);
        outboxMapper.updateById(update);
    }
}
