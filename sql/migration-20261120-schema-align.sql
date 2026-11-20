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
--
-- 中断续跑：每条语句独立生效；中断后按 SHOW TABLES 现状跳过已完成步骤，
-- 从第一条未生效的语句继续（RENAME 报 Unknown table = 该步已完成）。
-- ============================================================

SET NAMES utf8mb4;

-- ① 表改名 ×4（RENAME TABLE 原子，索引/数据随表走）
RENAME TABLE `tb_shop` TO `tb_attraction`;
RENAME TABLE `tb_shop_type` TO `tb_attraction_type`;
RENAME TABLE `tb_blog` TO `tb_travel_note`;
RENAME TABLE `tb_blog_comments` TO `tb_travel_note_comment`;

-- ② 列改名 ×2（与实体字段、mapper XML、DDL 注释对齐，来源 ab440da；
--    CHANGE COLUMN 一步完成改名+注释，避免 RENAME COLUMN 把旧注释「商户id/探店id」留在新列名下）
ALTER TABLE `tb_travel_note`
  CHANGE COLUMN `shop_id` `attraction_id` bigint(20) NOT NULL COMMENT '景点id';
ALTER TABLE `tb_travel_note_comment`
  CHANGE COLUMN `blog_id` `travel_note_id` bigint(20) UNSIGNED NOT NULL COMMENT '游记id';

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

-- ③ 补逻辑删除列（来源 cea1240；列位在 update_time 之后，与 DDL 一致）
ALTER TABLE `tb_travel_note`
  ADD COLUMN `is_deleted` tinyint(1) UNSIGNED NOT NULL DEFAULT 0
  COMMENT '逻辑删除标记（0-未删除，1-已删除）';

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
--   ALTER TABLE `tb_travel_note_comment` RENAME COLUMN `travel_note_id` TO `blog_id`;
--   ALTER TABLE `tb_travel_note` RENAME COLUMN `attraction_id` TO `shop_id`;
--   RENAME TABLE `tb_travel_note_comment` TO `tb_blog_comments`;
--   RENAME TABLE `tb_travel_note` TO `tb_blog`;
--   RENAME TABLE `tb_attraction_type` TO `tb_shop_type`;
--   RENAME TABLE `tb_attraction` TO `tb_shop`;
-- ============================================================
