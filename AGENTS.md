# 智行天下 — Agent Guidelines

> 给编码 agent 的最小上下文。数字（类数、测试数）以命令实际输出为准。
> 最后核对：2026-11-12

## 1. 是什么

智能旅游服务平台：AI 线路规划 + 社交组队 + 攻略整合 + 门票秒杀。

- Spring Boot 3.5.6 / **Java 17** / Maven（2026-11 从 2.7.17/Java 8 升级，坑见 docs/interview/05）
- MyBatis-Plus 3.5.12（Boot3 starter）+ MySQL 8.0 + Redis（Lua 脚本）+ RabbitMQ + Spring Task
- LangChain4j 0.36.2 → 阿里云百炼（DashScope OpenAI 兼容模式，qwen-plus）
- Hutool / Lombok

## 2. 秒杀链路（核心，改动风险最高）

```
请求 → Redis Lua 原子脚本（判重 → 查库存 → 扣减，返回 1/0/-1）
     → 生成雪花订单号 → RabbitMQ（publisher-confirm，durable）
     → 消费者手动 ack（失败进死信队列）→ 落库
```

改这块必须：Lua 脚本与 Java 侧返回码语义对齐、幂等可重放、失败可追溯。

## 3. 目录

```
src/main/java/com/zhixing/
  config/      Redis / RabbitMQ / MyBatis-Plus / Web 配置
  controller/  REST 入口
  service/     业务接口      service/impl/ 实现
  mapper/      MyBatis-Plus Mapper
  entity/      数据库实体    dto/         传输对象
  utils/       工具（含 Redis 脚本封装）
src/main/resources/
  application.yaml · lua/（秒杀脚本）· mapper/（XML）
src/test/java/  单元测试（数量以 mvn test 为准）
sql/zhixing.sql 建表 + 初始数据
```

## 4. 常用命令

```bash
mvn -q -DskipTests package      # 编译打包
mvn -q test                     # 测试
mvn -q spring-boot:run          # 本地起服务（需 MySQL/Redis/RabbitMQ）
```

## 5. 约定

- 分层不跨层：controller 不直接调 mapper
- 统一返回体（改造中）：新接口用统一 `Result<T>`，老接口保持兼容
- 异常：业务异常与系统异常分开；不吞异常、不空 catch
- SQL 变更必须同时更新 `sql/zhixing.sql`
- 配置里的密码/密钥不许硬编码进代码

## 6. 红线

- 不改 Lua 脚本的原子语义来「试一下能不能过」
- 不删测试、不放宽断言
- 不把 Redis / MySQL 地址写死到代码里
- 自动化运行器注入的验收命令**优先于本文档**；改动范围以运行器 scope 为准
