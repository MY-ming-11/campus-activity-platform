-- 给已存在的库补 activity.occupied 字段
--
-- 为什么需要单独一个脚本：容器里的 MySQL 只在数据目录为空时执行 schema.sql。
-- 库建好之后再改 schema.sql 是没用的，必须手动 ALTER。
-- 这正是我们在部署文档里写过的那个坑，这里是一个真实例子。
--
-- 执行方式：
--   docker compose exec -T mysql mysql -uroot -p activity_platform < sql/migration-2026-09-add-occupied.sql
--
-- 脚本可重复执行：字段已存在时不会报错，会打印一行提示后跳过。

USE activity_platform;

SET @exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'activity'
      AND COLUMN_NAME = 'occupied'
);

SET @ddl := IF(@exists = 0,
    'ALTER TABLE activity ADD COLUMN occupied INT NOT NULL DEFAULT 0 COMMENT ''已占用名额，乐观锁策略用它做原子扣减'' AFTER quota',
    'SELECT ''occupied 字段已存在，跳过'' AS msg');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 把已有活动的 occupied 对齐成真实的已报名数，避免新字段带着 0 上路
UPDATE activity a
SET a.occupied = (
    SELECT COUNT(*)
    FROM activity_signup s
    WHERE s.activity_id = a.id AND s.status <> 'CANCELED'
);
