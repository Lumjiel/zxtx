# debrand-plan · 去「教程改造」痕迹方案（2026-11-12，M1 轮产出）

> 目标对应 GOAL 一年后第 4 条：「看不出是教程改造：代码、表名、文档叙事都是我自己的」。
> **本文只出方案，未动任何代码/数据**——等人拍板后才执行。
> 判据：面试官搜 `tb_blog` / `hmdp` / 看到美团点评真实餐厅数据时，会不会立刻问「这是不是黑马点评改的」。

## 1. 现状盘点（三层痕迹，全部实测取证）

### 1.1 已完成（不用再动）

| 痕迹面 | 现状 | 证据 |
|:--|:--|:--|
| 包名 | `com.zhixing` | src/main/java 目录树 |
| 实体类名 | 已改：`Attraction` / `TravelNote` / `Coupon` / `TeamUpFollow` 等 | entity/ 目录 |
| README 正文 | 无黑马/hmdp 字样（仅 GOAL.md 提及该问题本身，属合理） | `grep -rIn "黑马\|hmdp" --exclude-dir=.git .` 仅命中 GOAL.md:20 |
| 部分 Redis key | `cache:Attraction:` / `TravelNote:liked:` | utils/RedisConstants.java:11,20 |
| sign 表列名 | 已用 `attraction_id`（uniq_user_attraction_date） | sql/zhixing.sql:184 |

### 1.2 未动痕迹 A：表名（11 张，最显眼）

`@TableName` 与 sql/zhixing.sql 全部还是老 schema：

| 老表名 | 实体类（已是新名） | 牵连位置 |
|:--|:--|:--|
| tb_blog | TravelNote | @TableName(TravelNote.java:22)、BlogMapper.xml:7 |
| tb_blog_comments | TravelNoteComments | @TableName(TravelNoteComments.java:24) |
| tb_follow | TeamUpFollow | @TableName(TeamUpFollow.java:24) |
| tb_seckill_voucher | SeckillVoucher | @TableName(SeckillVoucher.java:24)、VoucherMapper.xml:10 |
| tb_shop | Attraction | @TableName(Attraction.java:25) |
| tb_shop_type | AttractionType | @TableName(AttractionType.java:25) |
| tb_sign | Sign | @TableName(Sign.java:24) |
| tb_user | User | @TableName(User.java:24) |
| tb_user_info | UserInfo | @TableName(UserInfo.java:25) |
| tb_voucher | Coupon | @TableName(Coupon.java:25)、VoucherMapper.xml:9 |
| tb_voucher_order | CouponOrder | @TableName(CouponOrder.java:24)、README.md:41（架构图）、sql 尾部 ALTER(:1288) |

`sql/zhixing.sql` 内含 `tb_` 的行共 **1080 行**（绝大多数是 INSERT 种子数据，机械替换即可）。

### 1.3 未动痕迹 B：种子数据（比表名更致命）

`sql/zhixing.sql` 的 INSERT 数据是**美团/大众点评真实杭州餐饮数据**：
- `dpfile.com` / `meituan.net` 图片 URL **14 处**（sql/zhixing.sql）
- `tb_blog` 内容是「人均30💰杭州这家港式茶餐厅」这类真实点评文案
- `tb_shop` 是「海底捞火锅(水晶城购物中心店)」等真实商户

面试官只要 `grep -r meituan sql/` 一次就露馅。**这是全仓库最强的教程指纹。**

### 1.4 未动痕迹 C：Java 类名半成品（实体改了，服务层没改）

entity 叫 `TravelNote`，service/controller 仍叫 `Blog*`：
`BlogController` / `IBlogService` / `BlogServiceImpl` / `BlogCommentsController` / `BlogCommentsServiceImpl` / `BlogMapper(.xml)`；
同理 `Shop*`（6 个类）、`Voucher*`（5 个类）、`Follow*`（2 个类）。
另有常量名残留：`RedisConstants.BLOG_LIKED_KEY`(:20)、`RabbitMQConfig.BLOG_LIKED_QUEUE`(:41)、
`ShopCacheServiceTest`、注释里的 `tb_voucher_order`（SeckillOrderConsumer.java:31）等。
测试引用：SeckillOrderConsumerTest:7,41（VoucherOrderMapper）、ShopCacheServiceTest:38,46（ShopServiceImpl）。

## 2. 建议改名映射（批次 1，若拍板执行）

| 老 | 新（snake_case，与实体类对齐） |
|:--|:--|
| tb_blog | t_travel_note |
| tb_blog_comments | t_travel_note_comment |
| tb_follow | t_team_up_follow |
| tb_seckill_voucher | t_seckill_voucher |
| tb_shop | t_attraction |
| tb_shop_type | t_attraction_type |
| tb_sign | t_sign |
| tb_user | t_user |
| tb_user_info | t_user_info |
| tb_voucher | t_coupon |
| tb_voucher_order | t_coupon_order |

改动面：sql/zhixing.sql（~1080 行机械替换 + 11 个 CREATE）· 11 个 @TableName · 2 个 XML（3 处）· README 架构图 1 处 · Java 注释 ~4 处 · docs 引用若干。
**不需要动**：Lua 脚本（key 是通用 `seckill:*`）、RedisConstants、RabbitMQ 声明、application.yaml。

## 3. 三个方案与改动量

| 方案 | 内容 | 工作量 | 风险 |
|:--|:--|:--|:--|
| **A 最小** | 只改表名（§2 全部） | 1.5–2h（含 mvn test 验证） | 低：单测全 Mockito，不依赖真库表名；Lua 测试用 Redis 不用 MySQL |
| **B 中等** | A + 种子数据重造（虚构旅游场景数据，缩到 5–8 家景点/几十条游记，全自写） | +3–4h | 中：数据要经得起业务逻辑跑通（外键/类型对齐）；消灭 14 处 meituan URL |
| **C 最大** | B + 类名统一（Blog*→TravelNote* 等 ~18 个类 + 测试同步改） | +2–3h | 中高：纯机械但面广，容易漏 import；收益最低（面试官不翻 service 类名） |

## 4. 推荐（可被什么推翻）

**推荐 B，分两次夜间执行：第一夜方案 A（表名），第二夜种子数据重造；C 不做。**

理由：
- B 消灭两个最强指纹（表名 + 真实点评数据）；A 单独做只解决一半，`grep meituan` 仍露馅。
- C 是纯改名，面试官关注点在架构与秒杀链路，不在 `BlogServiceImpl` 这个类名；
  且实体类名与类名不一致反而可以在面试里讲「演进过程」（先改领域概念、遗留类名是历史债）——但这条理由偏弱，属个人取舍。

**什么会推翻本推荐**：
1. 若存在已初始化的真实数据库（不是重建 sql/zhixing.sql 得来的），表名要用 `ALTER TABLE … RENAME` 迁移脚本而非改建表文件——需先确认部署方式（未验证：本机是否有 MySQL 实例在跑 zxtx 库）。
2. 若后续要接教程原版前端（nginx 静态页），接口路径/字段名联动面会扩大，C 的成本上升。
3. 若时间预算（剩余夜间轮次）不足，A 优先保底：表名指纹 > 数据指纹的暴露面（表名出现在所有文档与代码里）。

## 5. 建议不改清单

| 项 | 理由 |
|:--|:--|
| `seckill:stock:` / `seckill:user:` Redis key | 通用命名无品牌痕迹；Lua 脚本红线不许碰原子语义 |
| `login:code:` / `cache:` / `sign:` 等 key | 同上，无痕迹 |
| GOAL.md / requirements.md 里的「黑马」字样 | 是对问题本身的记录，不是痕迹 |
| `zhixing-tianxia` 应用名 | 与「智行天下」一致，无问题 |
| LICENSE / CI 文件 | 无痕迹 |
| UPDATE/INSERT 的 `\r\n` 数据细节 | 属种子数据问题，随批次 2 一并处理 |

## 6. 待拍板问题（等人决定，本轮不执行）

1. 选 A / B / C？（我的建议：B，分两夜）
2. 表名前缀用 `t_` 还是裸名（如 `travel_note`）？（建议 `t_`，与老 `tb_` 风格延续，diff 可读）
3. 种子数据重造的规模：全量对齐原行数，还是缩编（建议缩编到能演示业务的最小集，约 200 行）
4. 执行窗口：表名批次做完必须跑 `mvn test` + check_regress 验证，选夜间哪一轮

## 7. 回滚方式（拍板执行时适用）

- 单一 commit 承载批次 A（表名），出问题 `git revert <sha>` 一次性回滚（runner 自动 commit，符合约定）
- 种子数据批次 B 独立 commit，互不纠缠
- 不动 DB 实例：本方案只改仓库内文件，建库由 `sql/zhixing.sql` 重建完成
