# 智行天下 - 智能旅游服务平台

## 项目简介

一个智能旅游服务平台，以「AI 规划 + 社交组队 + 攻略整合」为核心：景点点评、攻略分享、AI 线路规划、景区门票优惠券秒杀。

## 技术栈

- **后端框架**：Spring Boot 2.7.17
- **AI框架**：LangChain4j 0.36.2（对接阿里云百炼 DashScope OpenAI 兼容模式，qwen-plus）
- **持久层**：MyBatis-Plus 3.4.3
- **数据库**：MySQL 8.0
- **缓存**：Redis（StringRedisTemplate + Lua 脚本）
- **消息队列**：RabbitMQ（Spring AMQP，publisher-confirm / 手动 ack / 死信队列）
- **定时任务**：Spring Task
- **工具库**：Hutool、Lombok

## 架构总览：秒杀链路

```
用户请求
   │
   ▼
┌─────────────┐   ①时间校验    ┌────────────────────────────────┐
│ 秒杀入口     │──────────────▶│ Redis Lua 原子脚本              │
│ seckill/{id} │               │ SISMEMBER判重 → 查库存 → 扣减   │
└─────────────┘               │ 返回 1成功 / 0库存不足 / -1重复  │
                              └──────────┬─────────────────────┘
                                         │ ②Lua 通过：雪花订单号
                                         ▼
                              ┌────────────────────┐
                              │ RabbitMQ           │  publisher-confirm + return
                              │ seckill.order.queue│  durable，失败消息 → 死信队列
                              └──────────┬─────────┘
                                         │ ③消费者（手动 ack）
                                         ▼
                              ┌─────────────────────────────────┐
                              │ DB：UPDATE stock=stock-1        │
                              │     WHERE voucher_id=? AND      │  ④乐观锁兜底防超卖
                              │          stock > 0              │
                              │ INSERT tb_voucher_order         │  ⑤(user_id,voucher_id)
                              └─────────────────────────────────┘    唯一索引兜底一人一单
```

## 核心功能

### 1. 旅游景点管理
- 景点信息展示与搜索（详情带三态缓存：真实数据 / 空值防穿透 / 未命中回源）
- 景点分类管理
- 景点点评与评分

### 2. 攻略分享系统
- 旅游攻略发布与管理
- 攻略评论与点赞（Redis ZSet 存点赞明细：member=userId, score=点赞时间戳）
- 热门攻略推荐（分页结果缓存）与攻略详情（三态缓存）

### 3. AI智能线路规划
- 基于阿里云百炼平台大模型（LangChain4j 对接 DashScope OpenAI 兼容模式）
- 整合目的地、天数、同游人员、兴趣偏好、预算、出发地六维参数构造结构化 Prompt
- 输出约束：按天分早/中/晚三时段、地理顺路避折返、标注时长费用、Markdown 结构化展示
- 参数缺失自动兜底（如未填天数默认 3 天）

### 4. 优惠券秒杀系统
- 四层防超卖：时间校验 → Redis Lua 原子扣减（判重+查库存+扣减+记录一次性完成）→ MQ 异步落库削峰 → 数据库乐观锁 `stock > 0` 兜底
- 一人一单双保险：Lua 内 SISMEMBER 判重 + 数据库 (user_id, voucher_id) 唯一索引
- 消息可靠性：publisher-confirm / durable 队列 / 消费端手动 ack / 幂等消费 / Redis 库存回补 / 死信队列兜底

### 5. 社交组队功能
- 用户关注与粉丝系统
- 旅游组队信息发布
- 景点签到（SpringTask 巡检）

## 技术亮点

1. **高性能缓存设计**
   - 景点详情、攻略详情采用「三态缓存」：命中真实数据直接返回 / 命中空值标记直接拒绝（防穿透，2s 短 TTL）/ 未命中回源数据库
   - 缓存未命中走互斥锁重建：SETNX+EXPIRE 原子加锁，抢锁失败休眠自旋，拿到锁后双重检查再回源（防击穿）；只有抢到锁的线程在 finally 中释放锁 key
   - 缓存 TTL 统一叠加 ±10% 随机抖动（`CacheTtlJitter`），把过期时间点打散（防雪崩）

2. **AI 智能规划**
   - 基于结构化 Prompt 工程（人设 + 六维参数 + 输出格式硬约束）生成可渲染的 Markdown 行程

3. **高并发秒杀保障**
   - Redis Lua 原子扣减，100 库存 / 500 并发压测不超卖不少卖（见 `SeckillLuaScriptTest`）
   - 消费端幂等双保险（订单号查重 + 唯一索引），Redis 预扣失败自动回补（incr + SREM）

4. **消息可靠性**
   - 生产端 publisher-confirm + return（秒杀消息携带 CorrelationData=订单号，失败可定位）
   - broker 端 durable 队列 + 持久化消息
   - 消费端手动 ack；失败消息 nack 进死信队列 `seckill.order.dlx.queue`，不无限重投

5. **点赞数据巡检**
   - 点赞明细存 Redis ZSet，DB 只存计数
   - 每天凌晨 2 点定时任务：SCAN 渐进遍历（不用 KEYS，防阻塞主线程），已删除攻略的残留 ZSet 直接清理（管控内存驻留）；存量攻略的 Redis 计数与 DB 计数不一致时经 MQ 异步对账落库（绝对值覆盖，天然幂等）

## 快速开始

### 环境要求

- JDK 8+（用 JDK 21 编译也已验证通过）
- MySQL 8.0+
- Redis 6.0+
- RabbitMQ 3.8+
- Maven 3.6+

### 数据库配置

1. 执行初始化脚本（自动建库 `zhixing` + 全部表 + 演示数据）：
   ```bash
   mysql -uroot -p < sql/zhixing.sql
   ```
2. 修改 `src/main/resources/application.yaml` 中的 MySQL / Redis / RabbitMQ 连接信息
3. AI 功能需配置环境变量 `DASHSCOPE_API_KEY`（阿里云百炼），不配置不影响其他功能启动

### ⚠️ RabbitMQ 注意事项

本项目为秒杀队列配置了死信参数（`x-dead-letter-routing-key`）。如果 broker 中存在**旧版本创建的同名队列**，声明时会发生 PRECONDITION_FAILED 冲突。解决：打开 RabbitMQ 管理控制台（http://localhost:15672）删除旧的 `seckill.order.queue` / `TravelNote:liked.queue`，重启应用即可自动重建。

### 项目启动

```bash
git clone <项目地址>
cd zxtx
mvn spring-boot:run
# 访问 http://localhost:8081
```

### 运行测试

```bash
# 单元测试（纯 Mockito，无需任何外部服务）
mvn test -Dtest='SeckillOrderConsumerTest,ShopCacheServiceTest'

# Lua 脚本并发测试（需要本地 Redis，连不上会自动跳过）
mvn test -Dtest=SeckillLuaScriptTest
```

## 项目结构

```
src/main/java/com/zhixing/
├── config/          # 配置类（RabbitMQ 可靠性配置、秒杀消费者、点赞巡检任务等）
├── controller/      # 控制器
├── dto/             # 数据传输对象
├── entity/          # 实体类
├── mapper/          # 数据访问层
├── service/         # 业务逻辑层
│   └── impl/        # 业务逻辑实现（秒杀、缓存三态、AI 行程规划等）
├── utils/           # 工具类（Redis key 常量、TTL 抖动等）
└── ZhiXingTianXiaApplication.java  # 启动类
src/main/resources/
├── lua/seckill.lua  # 秒杀 Lua 脚本（判重/查库存/扣减/记录 原子完成）
└── mapper/          # MyBatis XML
sql/zhixing.sql      # 数据库初始化脚本
```

## 核心 API

| 模块 | 接口 | 说明 |
|------|------|------|
| 秒杀 | `POST /Coupon-order/seckill/{id}` | 秒杀下单，返回订单号 |
| 优惠券 | `GET /Coupon/shop/{shopId}` | 景点优惠券列表 |
| 攻略 | `GET /TravelNote/{id}` | 攻略详情（缓存） |
| 攻略 | `GET /TravelNote/hot?current=1` | 热榜分页（缓存） |
| 点赞 | `PUT /TravelNote/like/{id}` | 点赞 / 取消点赞 |
| 点赞 | `GET /TravelNote/likes/{id}` | 点赞用户 top5 |
| AI | `POST /api/route/plan` | AI 行程规划（六维参数） |
| 景点 | `GET /Attraction/{id}` | 景点详情（缓存） |
| 签到 | `POST /sign/{attractionId}` | 景点打卡签到 |

## 许可证

本项目采用 MIT 许可证 - 详见 LICENSE 文件

---

**智行天下** - 让大学生的旅行更智能、更便捷、更有趣！
