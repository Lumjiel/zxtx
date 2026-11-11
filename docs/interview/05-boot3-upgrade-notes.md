# 05 · Boot 2.7 → 3.5 升级踩坑记录（2026-11-10 夜，单窗口完成）

升级跨度：Spring Boot 2.7.17/Java 8 → 3.5.6/Java 17，全程测试全绿。

## 变更清单（与踩坑一一对应）

| # | 变更 | 踩的坑 / 原因 |
|:--|:--|:--|
| 1 | parent 2.7.17 → 3.5.6，`java.version` 1.8 → 17 | Boot 3 起 Spring Framework 6 / Jakarta EE 9+，包名全面改名 |
| 2 | `javax.annotation.Resource/PostConstruct` → `jakarta.*`（11 文件） | 注解 API 随 Jakarta 迁移；Boot 3 starter 自带 jakarta.annotation-api 2.x，无需额外依赖 |
| 3 | `javax.servlet.http.*` → `jakarta.servlet.http.*`（4 文件：拦截器、Session） | Servlet API 同步迁移 |
| 4 | `spring.redis.*` → `spring.data.redis.*`（application.yaml） | Boot 3 属性命名空间合并进 spring.data |
| 5 | `mybatis-plus-boot-starter` 3.4.3 → `mybatis-plus-spring-boot3-starter` 3.5.12 | MP 的 Boot 3 适配是独立 starter，直接换坐标 |
| 6 | 追加 `mybatis-plus-jsqlparser` 3.5.12 | MP 3.5.9+ 把 PaginationInnerInterceptor 拆到独立构件，编译期才暴露 |
| 7 | `mysql-connector-java` → `com.mysql:mysql-connector-j` | 驱动坐标迁移 |
| 8 | `selectCount` 返回值 Integer → Long（SignServiceImpl.java:158） | MP 3.5 API 变更，编译期暴露 |
| 9 | 测试：Lettuce 连接改用 `RedisStandaloneConfiguration` + 环境变量密码 | 原 NOAUTH 根因：硬编码 localhost 无密码，连隧道后的带密码 Redis 必然失败 |

## 排序上的关键决定：先修 NOAUTH 再升级

先在 Boot 2.7 基线上把测试修到真跑（而不是 skip），再动 pom——
结果第一步就暴露了并发测试死锁（64 线程池装不下 500 个闩任务，见 docs/seckill-trace.md badcase 1）。
若先升级再修测试，两个问题会搅在一起，无法归因。

## 迁移期检查清单（可复用）

1. `grep -rl 'javax\.' src/` 必须为空（CI 已加此门禁，见 .github/workflows/ci.yml）
2. 编译期暴露 API 变更（MP Long 返回值、拆分构件），运行期暴露配置项改名——两层都要验
3. 升级后全量 `mvn -B test`，确认原本 skip 的用例真的执行了（NOAUTH 案例）

## 反例

- 未覆盖运行时行为验证（无集成测试起完整 Spring 上下文 + 真实 MySQL/MQ），
  Jackson 序列化、拦截器注册等 Boot 3 行为差异依赖下次联调验证——如实告知，不夸大「平滑」。
