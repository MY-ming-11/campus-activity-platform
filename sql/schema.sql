-- ============================================================
-- 校园活动报名与签到平台 —— 建表脚本
-- MySQL 8.0+ / utf8mb4
-- 执行方式：mysql -uroot -p < sql/schema.sql
-- 脚本可重复执行，不会清空已有数据
-- ============================================================

CREATE DATABASE IF NOT EXISTS activity_platform
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE activity_platform;

-- ------------------------------------------------------------
-- 1. 用户表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(32)  NOT NULL COMMENT '登录名',
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文，不存明文',
    nickname    VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '昵称',
    phone       VARCHAR(20)  NOT NULL DEFAULT '' COMMENT '手机号',
    role        VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT '角色：USER / ADMIN',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';

-- ------------------------------------------------------------
-- 2. 活动表
--    quota          名额上限，真实余量放在 Redis，以 Redis 为准
--    checkin_secret 动态签到码的签名密钥，每个活动一份
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS activity
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    title          VARCHAR(64)  NOT NULL COMMENT '活动标题',
    cover          VARCHAR(255) NOT NULL DEFAULT '' COMMENT '封面图地址',
    description    TEXT         NULL COMMENT '活动详情',
    location       VARCHAR(128) NOT NULL DEFAULT '' COMMENT '活动地点',
    start_time     DATETIME     NOT NULL COMMENT '活动开始时间',
    end_time       DATETIME     NOT NULL COMMENT '活动结束时间',
    signup_start   DATETIME     NOT NULL COMMENT '报名开始时间',
    signup_end     DATETIME     NOT NULL COMMENT '报名截止时间',
    quota          INT          NOT NULL DEFAULT 0 COMMENT '名额上限',
    checkin_secret VARCHAR(64)  NOT NULL COMMENT '签到码签名密钥',
    status         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT / PUBLISHED / CLOSED',
    creator_id     BIGINT       NOT NULL COMMENT '创建人 ID',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_status_signup_end (status, signup_end),
    KEY idx_creator (creator_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='活动表';

-- ------------------------------------------------------------
-- 3. 报名记录表
--    uk_activity_user 是幂等的最后一道防线：
--    即使 Redis 去重被绕过，同一个人也不可能插入两条报名
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS activity_signup
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    activity_id BIGINT      NOT NULL COMMENT '活动 ID',
    user_id     BIGINT      NOT NULL COMMENT '用户 ID',
    status      VARCHAR(16) NOT NULL DEFAULT 'SIGNED' COMMENT '状态：SIGNED / CANCELED / CHECKED',
    signup_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_user (activity_id, user_id),
    KEY idx_user_time (user_id, signup_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='报名记录表';

-- ------------------------------------------------------------
-- 4. 签到流水表
--    uk_signup 保证一次报名只能核销一次
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS activity_checkin
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    activity_id BIGINT      NOT NULL COMMENT '活动 ID',
    signup_id   BIGINT      NOT NULL COMMENT '报名记录 ID',
    user_id     BIGINT      NOT NULL COMMENT '用户 ID',
    check_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '核销时间',
    check_type  VARCHAR(16) NOT NULL DEFAULT 'QR' COMMENT '方式：QR 扫码 / MANUAL 手动',
    operator_id BIGINT      NOT NULL DEFAULT 0 COMMENT '核销操作人 ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_signup (signup_id),
    KEY idx_activity (activity_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='签到流水表';

-- ------------------------------------------------------------
-- 5. 本地消息表（名额扣减的可靠落地）
--    流程：Redis 扣减成功 -> 写这张表 -> 异步消费落库
--          失败由定时任务按 retry_count 补偿，超限回滚 Redis 名额
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS quota_outbox
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    activity_id BIGINT      NOT NULL COMMENT '活动 ID',
    user_id     BIGINT      NOT NULL COMMENT '用户 ID',
    status      VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING / DONE / FAILED',
    retry_count INT         NOT NULL DEFAULT 0 COMMENT '已重试次数',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_status_retry (status, retry_count)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='本地消息表';

-- ------------------------------------------------------------
-- 6. 操作日志表（压测取证 + 防刷排查）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_oper_log
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '用户 ID',
    action      VARCHAR(64)  NOT NULL COMMENT '操作名',
    detail      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '补充信息',
    ip          VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '客户端 IP',
    cost_ms     INT          NOT NULL DEFAULT 0 COMMENT '耗时(毫秒)',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user_time (user_id, create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='操作日志表';
