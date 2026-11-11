# 智行天下 路线图（自规划用的特性号表）

> 新特性只能先写进本表，卡里引用的 F 号必须在这里存在。

## 工程化
- F-001 CI 流水线：GitHub Actions + JDK17 + Redis service
- F-002 测试覆盖：行覆盖 14.9% → 40%
- F-003 Controller 层测试（MockMvc）
- F-004 统一返回体 Result<T> + 全局异常处理
- F-005 可观测性：actuator + 关键接口耗时

## 技术升级
- F-006 Spring Boot 2.7.17 → 3.5，Java 8 → 21
- F-007 配置项迁移（spring.redis.* → spring.data.redis.*）
- F-008 容器化部署（compose + Dockerfile）

- F-010 配置外置与密钥管理：密码/密钥走环境变量，仓库只留占位

## 文档
- F-009 README 数据回填（覆盖率、压测、版本）

## 基线（2026-11-09）
11 测试 · 行覆盖 14.9% · checkstyle 1570 违规
