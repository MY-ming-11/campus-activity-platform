-- ============================================================
-- 清理压测产生的数据
-- 压测会新建活动、并把 bench_0 ~ bench_N 这批用户留在库里，
-- 不影响功能，但会污染真实数据，演示前跑一次这个脚本。
-- ============================================================

USE activity_platform;

-- 先删子表，再删主表，避免留下孤儿数据
DELETE s
FROM activity_signup s
         JOIN activity a ON a.id = s.activity_id
WHERE a.title LIKE '压测活动%'
   OR a.title = '限流验证活动';

DELETE c
FROM activity_checkin c
         JOIN activity a ON a.id = c.activity_id
WHERE a.title LIKE '压测活动%'
   OR a.title = '限流验证活动';

DELETE o
FROM quota_outbox o
         JOIN activity a ON a.id = o.activity_id
WHERE a.title LIKE '压测活动%'
   OR a.title = '限流验证活动';

DELETE FROM activity
WHERE title LIKE '压测活动%'
   OR title = '限流验证活动';

DELETE FROM sys_user WHERE username LIKE 'bench\_%';

-- Redis 里的名额 key 跟着活动一起清。
-- MySQL 删不掉 Redis 的 key，需要单独执行下面两条（把 id 换成实际值）：
--   redis-cli DEL activity:stock:<id> activity:users:<id>
-- 或者开发环境直接清库（仅限本机开发，别在服务器上跑）：
--   redis-cli FLUSHDB

SELECT '剩余压测活动' AS 项, COUNT(*) AS 数量 FROM activity WHERE title LIKE '压测活动%'
UNION ALL
SELECT '剩余压测用户', COUNT(*) FROM sys_user WHERE username LIKE 'bench\_%';
