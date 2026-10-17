# 拣货任务下发系统 — 实施计划

> 状态：已实施（2026-10-17），实施结果见文末
> 日期：2026-10-17
> 目标仓库：当前仓库（公开）

---

## 1. 需求理解

### 1.1 业务目标

在多仓协同的电商物流履约网络中，中央调度系统根据各分拣中心同步的库存数据生成拣货波次。当前问题是：拣货员到达货位后才发现实物缺货，产生往返空跑和工时浪费。

系统需要解决的不是“事后统计缺货”，而是**在下发任务前判断库存是否可信、是否足够**，把缺货拦截在拣货员出发之前；库存不足的任务进入待下发队列，等补货/库存同步后再自动下发。

### 1.2 你的解决方案（本计划的核心流程）

```
创建拣货任务
   │
   ├─ 查询该仓库 + 货物的库存快照
   │
   ├─ 库存足够 ──→ 创建任务行（状态=已下发）
   │                 └─ 调用通知接口
   │
   └─ 库存不足 ──→ 创建任务行（状态=待下发）
                     │
                     └─ 补货/库存同步事件
                           │
                           └─ 扫描同仓库 + 同货物的待下发任务（按创建时间先进先出）
                                 │
                                 ├─ 库存已够 ──→ 更新为已下发，扣减库存，调用通知接口
                                 └─ 库存仍不够 ──→ 保持待下发
```

### 1.3 本计划对方案的补充（需你确认）

1. **快照新鲜度校验（建议默认开启，可配置关闭）**
   - 仅看数量不够：如果快照已经很久没同步，账面库存可能是错的。
   - 规则：快照年龄超过阈值（默认 10 分钟）时，即使数量足够也进入待下发，原因记为 `SNAPSHOT_STALE`；补货/同步后自动重扫。
   - 依据：历史数据分析显示快照 <10 分钟缺货率约 3%，≥10 分钟约 34%；该阈值作为默认配置，可在 `application.yml` 调整或关闭。
2. **任务状态闭环**
   - 待下发 `PENDING` → 已下发 `DISPATCHED` → 已拣出 `FULFILLED` / 缺货 `STOCKOUT`。
   - 预留结果回传接口，用于后续统计实际缺货；本期前端提供简单回传操作。
3. **通知接口留桩**
   - 定义 `TaskNotificationService` 接口，本期用日志实现；下发成功后调用。
   - 通过事务提交后事件调用，通知失败不影响任务下发事务。

---

## 2. 系统设计

### 2.1 总体架构

```
┌─────────────────────────────┐
│  Vue 3 + Vite + Element Plus │
│  仪表盘 / 任务 / 待下发 / 补货 / 库存 │
└──────────────┬──────────────┘
               │ HTTP /api（Vite 代理）
┌──────────────▼──────────────┐
│ Spring Boot 3 (Java 17)      │
│ Controller → Service → JPA   │
│  ├─ 任务下发服务              │
│  ├─ 补货/同步服务             │
│  ├─ 库存查询服务              │
│  └─ 通知接口（日志桩）        │
└──────────────┬──────────────┘
               │ JDBC
┌──────────────▼──────────────┐
│ MySQL 8.0                    │
│ 主数据 / 库存 / 任务 / 补货 / 通知日志 │
└─────────────────────────────┘
```

### 2.2 领域模型（ER 概要）

| 表 | 关键字段 | 说明 |
|---|---|---|
| `warehouse` | id, code, name | 仓库主数据 |
| `goods` | id, code, name, category | 货物主数据（MVP 以品类作为货物粒度，预留 SKU 扩展） |
| `inventory` | id, warehouse_id, goods_id, available_qty, synced_at, version | 当前可用库存；唯一键 (warehouse_id, goods_id) |
| `inventory_snapshot` | id, warehouse_id, goods_id, qty, source, synced_at | 每次补货/同步的库存快照历史，用于审计 |
| `pick_task` | id, task_no, warehouse_id, goods_id, required_qty, status, reason, snapshot_id, snapshot_qty, snapshot_synced_at, request_no, created_at, dispatched_at, completed_at, result | 拣货任务 |
| `replenishment` | id, request_no, warehouse_id, source, created_at | 补货单头（支持一次补多种货物） |
| `replenishment_item` | id, replenishment_id, goods_id, quantity | 补货明细 |
| `notification_log` | id, task_id, event_type, channel, payload, status, error, created_at | 通知调用记录（桩实现也落库） |

关键约束：

- `pick_task.request_no` 唯一，用于创建任务幂等（可选但建议保留）。
- `replenishment.request_no` 唯一，防止重复补货导致库存多加。
- `inventory` 唯一键 `(warehouse_id, goods_id)`。
- 所有状态变更记录时间戳；任务保留创建时使用的快照信息，便于复盘。

### 2.3 任务状态机

```
                库存足够且快照新鲜
   PENDING ──────────────────────────→ DISPATCHED ──→ FULFILLED
      ▲                                   │
      │                                   └──────────→ STOCKOUT
      │
      └── 补货/同步后重扫，仍不足则保持 PENDING
```

- `PENDING`：待下发，不占用库存。
- `DISPATCHED`：已下发，扣减 `available_qty`（预占）。
- `FULFILLED`：拣货成功。
- `STOCKOUT`：到位缺货；记录结果，本期不自动回补库存，留待对账。
- 可选：`CANCELLED`（取消待下发任务），本期可不实现。

### 2.4 核心流程

#### 流程 A：创建拣货任务

1. 校验仓库、货物、数量（数量 > 0）。
2. 开启事务，按 `(warehouse_id, goods_id)` 对 `inventory` 行加悲观锁。
3. 读取当前库存与 `synced_at`。
4. 判断：
   - 有库存行、数量足够、快照年龄 ≤ 阈值 → 扣减库存，创建 `DISPATCHED` 任务，记录快照信息；
   - 否则 → 创建 `PENDING` 任务，记录原因：`INSUFFICIENT_STOCK` / `SNAPSHOT_STALE` / `NO_INVENTORY`。
5. 提交事务。
6. 事务提交后发布 `TaskDispatchedEvent`，通知接口记录日志（本期桩）。

#### 流程 B：补货/库存同步

1. 请求体支持一个仓库、多条货物明细（对应“补货补了哪些”）。
2. 事务内按 `goods_id` 升序锁定库存行，避免死锁。
3. 每条明细：增加 `available_qty`，更新 `synced_at`，写入 `inventory_snapshot`。
4. 对每条明细，查询同仓库 + 同货物的 `PENDING` 任务，按 `created_at` 升序：
   - 库存足够 → 扣减、更新为 `DISPATCHED`、记录下发时间、收集事件；
   - 库存不足 → 停止该货物的扫描。
5. 提交事务；事务提交后统一发送通知。
6. 返回本次补货触发的已下发任务列表和剩余库存。

#### 流程 C：结果回传（本期简化）

1. `POST /api/pick-tasks/{id}/result`，`result = FULFILLED | STOCKOUT`。
2. 仅允许 `DISPATCHED` 任务回传。
3. 更新状态、完成时间；`STOCKOUT` 记录差异，不做库存自动调整。

### 2.5 事务与并发

- 任务创建、补货扫描都在 `@Transactional` 中完成。
- 对 `inventory` 行使用 `@Lock(PESSIMISTIC_WRITE)`，同一仓库 + 货物的并发下发串行化，避免超卖。
- 补货多货物时按 `goods_id` 排序加锁，避免交叉死锁。
- 通知在事务提交后执行（`@TransactionalEventListener(phase = AFTER_COMMIT)`），失败只记录日志，不回滚任务。
- 可选增强：补货 `request_no` 幂等；任务创建 `request_no` 幂等。

### 2.6 快照新鲜度

- 配置项：
  ```yaml
  dispatch:
    max-snapshot-age-minutes: 10   # <=0 表示关闭新鲜度校验
  ```
- 判定：`now - inventory.synced_at > max-snapshot-age-minutes`。
- 过期时不直接下发，任务进入 `PENDING`，原因 `SNAPSHOT_STALE`；补货/同步刷新 `synced_at` 后自动重扫。
- 该规则是建议增强项；如果希望严格按“数量够就下发”，把阈值设为 `0` 即可关闭。

### 2.7 通知接口

```java
public interface TaskNotificationService {
    void onTaskDispatched(PickTask task);
}
```

- 本期实现：`LoggingTaskNotificationService`，写应用日志并落 `notification_log` 表。
- 未来可替换为短信、企业微信、站内信等实现，不影响业务代码。
- 只在下发成功时调用；待下发/回传结果暂不通知。

### 2.8 REST API（后端）

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/meta/warehouses` | 仓库下拉 |
| GET | `/api/meta/goods` | 货物下拉 |
| POST | `/api/pick-tasks` | 创建任务（自动判断下发/待下发） |
| GET | `/api/pick-tasks` | 任务列表（status/warehouseId/goodsId/分页） |
| GET | `/api/pick-tasks/{id}` | 任务详情 |
| POST | `/api/pick-tasks/{id}/result` | 回传拣货结果 |
| GET | `/api/inventory` | 库存列表（仓库/货物筛选） |
| POST | `/api/replenishments` | 补货/同步，触发待下发扫描 |
| GET | `/api/replenishments` | 补货记录 |
| GET | `/api/dashboard/summary` | 仪表盘统计 |
| GET | `/api/notifications?taskId=` | 通知记录（可选） |

统一响应：

```json
{ "code": 0, "message": "ok", "data": { } }
```

错误使用明确的业务错误码（如库存不足不是错误，而是任务进入 PENDING；参数错误/资源不存在返回 4xx）。

请求示例：

```json
POST /api/pick-tasks
{ "warehouseId": 1, "goodsId": 3, "requiredQty": 5, "requestNo": "REQ-001" }

POST /api/replenishments
{
  "requestNo": "REP-001",
  "warehouseId": 1,
  "source": "manual",
  "items": [
    { "goodsId": 3, "quantity": 20 },
    { "goodsId": 5, "quantity": 10 }
  ]
}
```

### 2.9 前端页面（Vue 3）

| 路由 | 页面 | 主要内容 |
|---|---|---|
| `/` | 仪表盘 | KPI 卡片（任务总数、待下发、已下发、缺货）、待下发队列、库存新鲜度 |
| `/tasks` | 任务管理 | 创建任务表单、结果反馈、任务列表与筛选 |
| `/pending` | 待下发队列 | 按仓库/货物筛选，显示等待时长、原因、创建时快照 |
| `/replenishment` | 补货/同步 | 多明细补货表单，提交后展示触发的已下发任务 |
| `/inventory` | 库存 | 仓库/货物库存、同步时间、新鲜度标识、低库存高亮 |

交互要点：

- 创建任务后立即展示结果：`DISPATCHED` 或 `PENDING`（含原因）。
- 补货提交后展示“本次自动下发了哪些任务”，让扫描逻辑可见。
- 状态用颜色标签区分；待下发任务显示等待时间。
- 不做登录/权限，保持演示聚焦；后续可加。

---

## 3. 技术选型与工程结构

### 3.1 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 17、Spring Boot 3.2.x、Spring Web、Spring Data JPA、Validation、MySQL Connector/J |
| 数据库 | MySQL 8.0（本机已安装并运行在 3306） |
| 测试 | JUnit 5、Spring Boot Test、H2（测试库，MySQL 兼容模式） |
| 前端 | Vue 3、Vite、Vue Router、Element Plus、Axios |
| 构建 | Maven 3.8.6、npm 11 |

本机工具链注意：

- `mvn -version` 显示 Maven 使用 `D:\java\jdk`（Java 17），但 PATH 里的 `java` 是 1.8。
- 运行后端时需设置 `JAVA_HOME=D:\java\jdk`，或直接使用 `D:\java\jdk\bin\java`。
- 本机没有 Docker；MySQL 使用本机 8.0.33，不提供 Docker Compose。
- MySQL 账号密码需要在启动前确认（见“待确认事项”）。

### 3.2 目录结构

```
.
├─ backend/
│  ├─ pom.xml
│  └─ src/
│     ├─ main/java/com/example/pickdispatch/
│     │  ├─ PickDispatchApplication.java
│     │  ├─ common/           # 统一响应、异常、枚举
│     │  ├─ config/           # CORS、Jackson、配置属性
│     │  ├─ warehouse/        # 仓库主数据
│     │  ├─ goods/            # 货物主数据
│     │  ├─ inventory/        # 库存、快照
│     │  ├─ task/             # 拣货任务、下发服务、结果回传
│     │  ├─ replenishment/    # 补货单、扫描下发
│     │  ├─ notification/     # 通知接口与日志桩
│     │  └─ dashboard/        # 统计接口
│     ├─ main/resources/
│     │  ├─ application.yml
│     │  └─ db/               # 初始化 SQL（可选）
│     └─ test/java/...        # 服务与接口测试
├─ frontend/
│  ├─ package.json
│  ├─ vite.config.js
│  ├─ index.html
│  └─ src/
│     ├─ main.js
│     ├─ App.vue
│     ├─ router/index.js
│     ├─ api/
│     ├─ layouts/
│     ├─ views/
│     └─ components/
├─ data/picks.csv             # 保留的原始数据（仅作背景参考）
├─ PLAN.md                    # 本计划
├─ README.md                  # 实施完成后重写
└─ AI_LOG.md                  # 实施完成后重写
```

### 3.3 配置

```yaml
server:
  port: 8080
spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/pick_dispatch?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:}
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
dispatch:
  max-snapshot-age-minutes: 10
```

- 开发环境用 `ddl-auto=update` 自动建表；同时提供 `db/init.sql`（数据库、用户、可选初始化）方便复现。
- 生产环境应改为 Flyway/Liquibase，本期不引入。

---

## 4. 数据与演示种子

### 4.1 主数据

- 仓库：直接采用 `data/picks.csv` 中出现的 4 个仓库（西南枢纽转运仓、华东一号分拨仓、华北综合物流园、华南保税中心仓）。
- 货物：采用数据中的 10 个三级品类作为 MVP 的货物粒度（如粮油副食、宠物主粮……）。
- 说明：真实系统应按 SKU 建模；当前数据只有品类，MVP 用品类作为货物维度，后续可平滑扩展 `goods` 表。

### 4.2 演示库存

- 初始化 4 × 10 = 40 条库存记录，数量为**演示数据**，用于展示“足够/不足/补货后下发”三种场景。
- 演示数据会在 README 和代码注释中明确标为演示用途，不作为任何业务结论的依据。
- 原始 `picks.csv` 仅作为背景参考保留，不直接导入业务表。

### 4.3 初始化方式

- 方案 A（推荐）：`DemoDataInitializer` 在 `app.demo.enabled=true` 时写入主数据和演示库存；重复启动不重复插入。
- 方案 B：`db/init.sql` 手工执行。
- 测试环境使用 H2 + 测试专用数据，不依赖本机 MySQL。

---

## 5. 测试与验收

### 5.1 后端测试用例

1. 库存足够且快照新鲜 → 任务 `DISPATCHED`，库存扣减，产生通知事件。
2. 库存不足 → 任务 `PENDING`，库存不变。
3. 快照过期 → 任务 `PENDING`（原因 `SNAPSHOT_STALE`），即使数量足够。
4. 补货后扫描：多个待下发任务按创建时间先进先出，库存够几个就下发几个。
5. 补货数量只够部分任务 → 剩余任务保持 `PENDING`。
6. 补货幂等：相同 `requestNo` 不重复加库存（若实现）。
7. 结果回传：`DISPATCHED` → `FULFILLED` / `STOCKOUT`；非法状态拒绝。
8. 并发创建任务：库存只够一个时，不出现超卖（可选集成测试）。

### 5.2 验收清单

- [ ] `mvn test` 全部通过。
- [ ] 后端 `http://localhost:8080` 启动成功。
- [ ] 前端 `npm run dev` 启动，页面可访问。
- [ ] 创建任务：库存足够时立即下发；不足时进入待下发。
- [ ] 补货后，匹配的待下发任务自动下发，页面能看到结果。
- [ ] 通知接口被调用（日志/`notification_log` 可见）。
- [ ] 任务状态与库存数量在并发/重复操作下保持一致。
- [ ] README 包含启动步骤、数据库初始化、API 说明、演示流程。
- [ ] AI_LOG 记录本轮 AI 交互与纠错。

---

## 6. 实施步骤与里程碑

| 阶段 | 内容 | 预计耗时 |
|---|---|---:|
| P0 清理与骨架 | 删除旧分析代码；创建 backend/frontend 骨架；数据库初始化脚本 | 15 min |
| P1 后端核心 | 实体、Repository、任务下发服务、补货扫描服务、通知接口 | 45 min |
| P2 后端接口与测试 | Controller、统一响应、异常处理、H2 测试 | 25 min |
| P3 前端 | Vue 骨架、五个页面、API 对接、状态展示 | 40 min |
| P4 联调与种子数据 | 初始化演示数据、启动 MySQL、端到端走通三种场景 | 20 min |
| P5 文档与收尾 | README、AI_LOG、构建验证、提交 | 15 min |

**优先级**：P0 → P1 → P2 是核心闭环；P3 前端可在后端稳定后进行；若时间紧张，先交付可运行的后端 + 最小任务页面，再补仪表盘。

---

## 7. 删除与保留清单

### 删除（旧分析项目代码）

- `scripts/analyze.mjs`
- `scripts/gate.mjs`
- `results/`（脚本生成的分析结果）
- 根目录 `package.json`（旧分析项目的 npm 配置）
- 旧 `README.md`、`AI_LOG.md` 内容（实施完成后按新项目重写）

### 保留

- `data/picks.csv`：原始数据，作为仓库背景资料保留；不直接作为业务表数据。
- `.git/`：仓库历史。
- `.gitignore`：更新为新项目（增加 `backend/target/`、`frontend/node_modules/`、`frontend/dist/` 等）。

---

## 8. 风险与待确认事项

### 8.1 待你确认

1. **MySQL 连接信息**：本机 3306 已有 MySQL 8.0.33 在运行。请提供数据库用户名/密码，或授权我创建 `pick_dispatch` 库和专用账号；否则只能写代码、无法本机联调。
2. **货物粒度**：MVP 用“品类”作为货物（数据只有品类），还是需要我先建一套 SKU 主数据？推荐前者，后续可扩展。
3. **快照新鲜度校验**：是否默认开启 10 分钟阈值？推荐开启，可在配置中关闭；如果你希望严格按“数量够就下发”，我设为关闭。
4. **结果回传**：是否需要本期实现“已拣出/缺货”回传？推荐实现，便于展示完整闭环。
5. **旧分析内容**：确认删除 `scripts/`、`results/` 和旧 README/AI_LOG；`data/picks.csv` 保留。
6. **登录/权限**：本期不做，是否可以？

### 8.2 风险

- **MySQL 密码未知**：阻塞本机启动与联调；代码可以先写，但验收需要数据库。
- **Java 版本错位**：PATH 的 `java` 是 1.8，Maven 使用 JDK 17；启动脚本需显式设置 `JAVA_HOME`。
- **通知桩**：本期只落日志/表，不接真实渠道；需求已允许“先写接口不实现”。
- **并发与超卖**：通过悲观锁 + 唯一约束控制；需要测试覆盖。
- **演示数据**：库存数量为演示值，不能用于业务结论；README 必须标明。
- **前端依赖安装**：需要网络访问 npm registry；若受限，改用最小依赖或本机缓存。

---

## 9. 交付物

- 可运行的 Spring Boot 后端（含 MySQL schema、演示数据、测试）
- 可运行的 Vue 3 前端（任务、待下发、补货、库存、仪表盘）
- `README.md`：架构、启动步骤、API、演示流程、设计取舍
- `AI_LOG.md`：本轮 AI 交互、错误与修正
- `PLAN.md`：本计划
- 保留的 `data/picks.csv`（背景参考）

---

## 10. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-10-17 | 初版计划：按“快照判断 → 下发/待下发 → 补货扫描 → 通知桩”设计 |
| 2026-10-17 | 实施完成：Spring Boot 后端 + Vue 前端 + MySQL 联调；9 个后端测试通过；分析脚本与 README/AI_LOG 补齐 |

---

## 11. 第一轮实施结果（2026-10-17）

已按本计划完成核心交付，实际落地情况：

- 后端：Java 17 + Spring Boot 3.2.5 + Spring Data JPA + MySQL 8；8 张表；任务下发、补货 FIFO 扫描、通知桩、结果回传、仪表盘接口。
- 前端：Vue 3 + Vite + Element Plus；仪表盘、任务管理、待下发队列、补货/同步、库存五个页面。
- 数据库：本机 MySQL 8.0.33，库名 `pick_dispatch`，默认 `root/root`；`database/init.sql` 可复现建库。
- 测试：第一轮 9 个集成测试全部通过（H2），覆盖立即下发、库存不足、快照过期、补货 FIFO、部分补货、过期后补货下发、任务幂等、补货幂等、结果回传；第二轮扩展后总计 15 个。
- 联调：真实 MySQL 接口冒烟通过，通知日志落库 5 条。
- 分析：`analysis/analyze.mjs` 可复现 README 中的全部数字。
- 与计划的差异：
  - 快照新鲜度校验默认开启，阈值 10 分钟，可通过 `dispatch.max-snapshot-age-minutes` 关闭；
  - 结果回传在计划中是可选项，已实现；
  - 演示数据直接由 `DemoDataService` 写入，不需要手工执行 SQL；
  - 未引入 Flyway、登录权限、通知重试/消息队列，留作生产化下一步。
  - 第二轮扩展见第 12 节；最终测试 15 个、页面 7 个。

---

## 12. 第二轮扩展：用户下单 + 仓库 PDA + WebSocket

已实施：

- **订单域**：新增 `customer_order` 表与订单状态机 `PENDING → DISPATCHED → PICKING → PICKED / STOCKOUT`；下单与拣货任务同事务创建，任务通过 `order_id` / `order_no` 关联订单。
- **仓库选择**：下单可指定仓库；不指定时优先“库存足够且快照新鲜”，其次“库存最多”，再退到第一个仓。
- **PDA 认领**：`POST /api/pick-tasks/{id}/claim`，任务行悲观锁防并发认领；认领后订单变为 `PICKING`。
- **实时推送**：原生 WebSocket `/ws/picking`，事务提交后广播 `TASK_DISPATCHED`、`TASK_CLAIMED`、`TASK_COMPLETED`，消息带 `eventId` 去重。
- **前端**：同一 Vue 进程中新增 `/shop` 用户下单页和 `/pda` 仓库 PDA 页；`/shop`、`/pda` 使用独立门户布局，调度台保留侧边栏。
- **验证**：15 个后端测试全部通过（任务链路 9 + 订单链路 6）；前端构建通过；浏览器完成“下单 → 实时通知 → 认领 → 拣出完成”全流程。
- **未做**：支付/收货地址/取消订单、账号权限、WebSocket 多实例广播、通知重试；留作生产化下一步。

---

## 13. 服务器演示部署（2026-10-17）

- 公网入口：`http://39.106.14.83/front/`，服务器只开放 80。
- 宿主机 nginx 路径转发：
  - 前端 `/front/` → `127.0.0.1:7998/`
  - 后端 API `/backend/api/` → `127.0.0.1:7999/backend/api/`
  - WebSocket `/backend/ws/` → `127.0.0.1:7999/backend/ws/`（Upgrade）
- 后端 Spring Boot context-path：`/backend`；前端 API base `/backend/api`，WebSocket `/backend/ws/picking`。
- 容器：前端 7998:80、后端 7999:7999，均只绑定 `127.0.0.1`；统一接入 `pick-dispatch-net`。
- 前端 Vite base 为 `/front/`，Vue Router、静态资源和页面路径都带 `/front` 前缀。
- 侧边栏拆为三个角色入口：仪表盘（运营）、用户下单（用户）、PDA 拣货（仓库）；下方保留运营管理页。
- 新增 `deploy/nginx-demo.conf`，重写 `docs/docker-deploy.md`。
