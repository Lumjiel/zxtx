-- ============================================================
-- 真实库一次性迁移：旧 schema → 代码对齐 schema（2026-11-20）
--
-- 背景：应用代码与建表 DDL（sql/zhixing.sql）已完成业务表改名与字段对齐，
-- 但真实库（zxtx-mysql 的 zhixing 库）仍是旧命名。本包把真实库一次性迁到
-- 与代码/DDL 一致的形状，保证应用在迁移后的库上不再因表/列名不一致而报错。
--
-- 来源 commit：
--   ab440da refactor: 统一景点与游记业务表命名及字段语义
--     表改名×4：tb_shop→tb_attraction、tb_shop_type→tb_attraction_type、
--               tb_blog→tb_travel_note、tb_blog_comments→tb_travel_note_comment
--     列改名×2：tb_blog.shop_id→attraction_id、tb_blog_comments.blog_id→travel_note_id
--   cea1240 fix: 补齐 tb_travel_note 逻辑删除字段 is_deleted
--     补列×1：tb_travel_note.is_deleted（存量行自动落 0=未删除，与 @TableLogic 语义一致）
--
-- 探查留痕（2026-11-20，只读 SHOW/IS 查询）：
--   真实库 11 张表全为旧名；tb_blog 缺 is_deleted；tb_voucher_order 的
--   uk_user_voucher 唯一索引**已在位**——下方对应语句保留为幂等兜底，
--   在已建索引的库上执行会报 1061 Duplicate key name，跳过即可。
--   tb_sign.attraction_id、tb_voucher.shop_id 两处旧库即与 DDL 一致，无需迁移。
--
-- 用法：mysql --default-character-set=utf8mb4 -uroot -p zhixing < sql/migration-20261120-schema-align.sql
--       （charset 必须显式：2026-11-20 临时库实弹验证发现，缺省 latin1 通道会把
--        本文件 UTF-8 注释双重编码写入库——hex 实证 E6B8B8.. 变 C3A6C2B8..；
--        SET NAMES utf8mb4 为 server 侧双保险，两者都别省）
--
-- 执行前自查（应确认 4 张旧表存在）：
--   SELECT TABLE_NAME FROM information_schema.TABLES
--    WHERE TABLE_SCHEMA='zhixing'
--      AND TABLE_NAME IN ('tb_shop','tb_shop_type','tb_blog','tb_blog_comments');
--  b) NULL 存量预检——②/②b 共 7 列置 NOT NULL，若真实库与旧 DDL 有漂移、
--    存量行带 NULL（2026-11-20 探查只核了结构没核数据），严格模式下对应
--    语句报错中断；任一非零先处理再迁移：
--   SELECT SUM(shop_id IS NULL),SUM(images IS NULL),SUM(content IS NULL) FROM tb_blog;
--   SELECT SUM(blog_id IS NULL) FROM tb_blog_comments;
--   SELECT SUM(name IS NULL),SUM(type_id IS NULL),SUM(images IS NULL) FROM tb_shop;
--
-- 幂等语义（2026-11-21 升级，goal round 7）：整包可连跑多次不报错——
-- 每个变更步包一个守卫小过程（创建→调用→收尾即弃，不在库里留过程），
-- 守卫读 information_schema 三态裁决：旧态→执行 / 新态→已完成跳过（DO/SKIP 日志）
-- / 其它（新旧同时存在或同时缺失）→SIGNAL 报异常人工核查；
-- ②b 的 5 条 MODIFY 同定义重跑天然无副作用，不包守卫。中断后直接重跑整包即可续。
-- ============================================================

SET NAMES utf8mb4;

DELIMITER $$

-- ① 表改名 ×4（RENAME TABLE 原子，索引/数据随表走）
DROP PROCEDURE IF EXISTS mig_01_shop$$
CREATE PROCEDURE mig_01_shop()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_shop')
     AND NOT EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_attraction') THEN
    RENAME TABLE `tb_shop` TO `tb_attraction`;
    SELECT 'DO   ① tb_shop → tb_attraction' AS mig_log;
  ELSEIF NOT EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_shop')
     AND EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_attraction') THEN
    SELECT 'SKIP ① tb_shop → tb_attraction（已完成）' AS mig_log;
  ELSE
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'mig_01 异常：tb_shop/tb_attraction 同时存在或同时缺失，人工核查';
  END IF;
END$$

DROP PROCEDURE IF EXISTS mig_02_shop_type$$
CREATE PROCEDURE mig_02_shop_type()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_shop_type')
     AND NOT EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_attraction_type') THEN
    RENAME TABLE `tb_shop_type` TO `tb_attraction_type`;
    SELECT 'DO   ① tb_shop_type → tb_attraction_type' AS mig_log;
  ELSEIF NOT EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_shop_type')
     AND EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_attraction_type') THEN
    SELECT 'SKIP ① tb_shop_type → tb_attraction_type（已完成）' AS mig_log;
  ELSE
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'mig_02 异常：tb_shop_type/tb_attraction_type 同时存在或同时缺失，人工核查';
  END IF;
END$$

DROP PROCEDURE IF EXISTS mig_03_blog$$
CREATE PROCEDURE mig_03_blog()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_blog')
     AND NOT EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_travel_note') THEN
    RENAME TABLE `tb_blog` TO `tb_travel_note`;
    SELECT 'DO   ① tb_blog → tb_travel_note' AS mig_log;
  ELSEIF NOT EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_blog')
     AND EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_travel_note') THEN
    SELECT 'SKIP ① tb_blog → tb_travel_note（已完成）' AS mig_log;
  ELSE
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'mig_03 异常：tb_blog/tb_travel_note 同时存在或同时缺失，人工核查';
  END IF;
END$$

DROP PROCEDURE IF EXISTS mig_04_blog_comments$$
CREATE PROCEDURE mig_04_blog_comments()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_blog_comments')
     AND NOT EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_travel_note_comment') THEN
    RENAME TABLE `tb_blog_comments` TO `tb_travel_note_comment`;
    SELECT 'DO   ① tb_blog_comments → tb_travel_note_comment' AS mig_log;
  ELSEIF NOT EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_blog_comments')
     AND EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_travel_note_comment') THEN
    SELECT 'SKIP ① tb_blog_comments → tb_travel_note_comment（已完成）' AS mig_log;
  ELSE
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'mig_04 异常：tb_blog_comments/tb_travel_note_comment 同时存在或同时缺失，人工核查';
  END IF;
END$$

-- ② 列改名 ×2（与实体字段、mapper XML、DDL 注释对齐，来源 ab440da；
--    CHANGE COLUMN 一步完成改名+注释，避免 RENAME COLUMN 把旧注释「商户id/探店id」留在新列名下）
DROP PROCEDURE IF EXISTS mig_05_note_shop_id$$
CREATE PROCEDURE mig_05_note_shop_id()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_travel_note' AND COLUMN_NAME='shop_id')
     AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_travel_note' AND COLUMN_NAME='attraction_id') THEN
    ALTER TABLE `tb_travel_note`
      CHANGE COLUMN `shop_id` `attraction_id` bigint(20) NOT NULL COMMENT '景点id';
    SELECT 'DO   ② tb_travel_note.shop_id → attraction_id' AS mig_log;
  ELSEIF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_travel_note' AND COLUMN_NAME='shop_id')
     AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_travel_note' AND COLUMN_NAME='attraction_id') THEN
    SELECT 'SKIP ② tb_travel_note.shop_id → attraction_id（已完成）' AS mig_log;
  ELSE
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'mig_05 异常：shop_id/attraction_id 同时存在或同时缺失，人工核查';
  END IF;
END$$

DROP PROCEDURE IF EXISTS mig_06_comment_blog_id$$
CREATE PROCEDURE mig_06_comment_blog_id()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_travel_note_comment' AND COLUMN_NAME='blog_id')
     AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_travel_note_comment' AND COLUMN_NAME='travel_note_id') THEN
    ALTER TABLE `tb_travel_note_comment`
      CHANGE COLUMN `blog_id` `travel_note_id` bigint(20) UNSIGNED NOT NULL COMMENT '游记id';
    SELECT 'DO   ② tb_travel_note_comment.blog_id → travel_note_id' AS mig_log;
  ELSEIF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_travel_note_comment' AND COLUMN_NAME='blog_id')
     AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_travel_note_comment' AND COLUMN_NAME='travel_note_id') THEN
    SELECT 'SKIP ② tb_travel_note_comment.blog_id → travel_note_id（已完成）' AS mig_log;
  ELSE
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'mig_06 异常：blog_id/travel_note_id 同时存在或同时缺失，人工核查';
  END IF;
END$$

-- ③ 补逻辑删除列（来源 cea1240；列位在 update_time 之后，与 DDL 一致）
DROP PROCEDURE IF EXISTS mig_07_is_deleted$$
CREATE PROCEDURE mig_07_is_deleted()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_travel_note')
     AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_travel_note' AND COLUMN_NAME='is_deleted') THEN
    ALTER TABLE `tb_travel_note`
      ADD COLUMN `is_deleted` tinyint(1) UNSIGNED NOT NULL DEFAULT 0
      COMMENT '逻辑删除标记（0-未删除，1-已删除）';
    SELECT 'DO   ③ tb_travel_note ADD is_deleted' AS mig_log;
  ELSEIF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_travel_note' AND COLUMN_NAME='is_deleted') THEN
    SELECT 'SKIP ③ tb_travel_note ADD is_deleted（已完成）' AS mig_log;
  ELSE
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'mig_07 异常：tb_travel_note 不存在，人工核查';
  END IF;
END$$

DELIMITER ;

CALL mig_01_shop();
CALL mig_02_shop_type();
CALL mig_03_blog();
CALL mig_04_blog_comments();
CALL mig_05_note_shop_id();
CALL mig_06_comment_blog_id();

-- ②b 注释语义对齐 ×5（ab440da 同步把商铺/探店语义注释改为景点/游记语义；
--    MODIFY 逐字保真新 DDL 定义，含 CHARACTER SET/COLLATE——tb_travel_note.content
--    的列级 collate 是 utf8mb4_unicode_ci，与表默认不同，不可省）
ALTER TABLE `tb_attraction`
  MODIFY COLUMN `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '景点名称';
ALTER TABLE `tb_attraction`
  MODIFY COLUMN `type_id` bigint(20) UNSIGNED NOT NULL COMMENT '景点类型的id';
ALTER TABLE `tb_attraction`
  MODIFY COLUMN `images` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '景点图片，多个图片以\',\'隔开';
ALTER TABLE `tb_travel_note`
  MODIFY COLUMN `images` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '游记配图，最多9张，多张以","隔开';
ALTER TABLE `tb_travel_note`
  MODIFY COLUMN `content` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '游记正文';

CALL mig_07_is_deleted();

-- 收尾即弃：守卫过程不留在库里（中断残留时重跑本包，DROP IF EXISTS + 重建覆盖）
DROP PROCEDURE IF EXISTS mig_01_shop;
DROP PROCEDURE IF EXISTS mig_02_shop_type;
DROP PROCEDURE IF EXISTS mig_03_blog;
DROP PROCEDURE IF EXISTS mig_04_blog_comments;
DROP PROCEDURE IF EXISTS mig_05_note_shop_id;
DROP PROCEDURE IF EXISTS mig_06_comment_blog_id;
DROP PROCEDURE IF EXISTS mig_07_is_deleted;

-- ④ 一人一单唯一索引：旧 DDL（ab440da~1 之前）本就含
--    ALTER TABLE `tb_voucher_order` ADD UNIQUE KEY `uk_user_voucher` (`user_id`, `voucher_id`);
--    真实库 2026-11-20 探查已在位，故不入主流程——需要的环境（索引缺失的库）手工执行上面这句，
--    已在位时执行会报 1061 Duplicate key name，跳过即可。

-- ============================================================
-- 迁移后验证：
--   SELECT TABLE_NAME FROM information_schema.TABLES
--    WHERE TABLE_SCHEMA='zhixing' AND TABLE_NAME LIKE 'tb_%';
--   → 11 张表应全为新命名（tb_attraction / tb_attraction_type / tb_follow /
--     tb_seckill_voucher / tb_sign / tb_travel_note / tb_travel_note_comment /
--     tb_user / tb_user_info / tb_voucher / tb_voucher_order）
--   SHOW COLUMNS FROM `tb_travel_note` LIKE 'is_deleted';
--   → tinyint(1) unsigned NOT NULL DEFAULT 0
--   SHOW INDEX FROM `tb_voucher_order` WHERE Key_name='uk_user_voucher';
--   → 两列复合唯一索引 (user_id, voucher_id)
--
-- 回滚（整段反向，仅迁错时执行）：
--   ALTER TABLE `tb_voucher_order` DROP INDEX `uk_user_voucher`;  -- 原本就在位的库不要回滚此条
--   ALTER TABLE `tb_travel_note` DROP COLUMN `is_deleted`;
--   ALTER TABLE `tb_travel_note_comment`
--     CHANGE COLUMN `travel_note_id` `blog_id` bigint(20) UNSIGNED NOT NULL COMMENT '探店id';
--   ALTER TABLE `tb_travel_note`
--     CHANGE COLUMN `attraction_id` `shop_id` bigint(20) NOT NULL COMMENT '商户id';
--   RENAME TABLE `tb_travel_note_comment` TO `tb_blog_comments`;
--   RENAME TABLE `tb_travel_note` TO `tb_blog`;
--   RENAME TABLE `tb_attraction_type` TO `tb_shop_type`;
--   RENAME TABLE `tb_attraction` TO `tb_shop`;
--   注：②b 的 5 条注释语义对齐不反向还原（回滚后这 5 列注释仍是景点/游记语义）；
--       本回滚还原的是表/列名与改名列的旧注释（定义取自 ab440da~1 旧 DDL），结构与数据不受影响。
-- ============================================================
