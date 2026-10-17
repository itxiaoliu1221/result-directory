# Docker 部署指南（测试环境）

本文档描述如何用 Docker Compose 在一台远端 Linux 服务器上启动 MySQL、Spring Boot 后端和 Vue 前端，并在首次启动时自动写入一套虚构的测试数据。

> 测试数据全部为“测试仓库 / 测试商品 / 测试订单”，不含任何真实仓库、订单或库存信息。
> `database/init-test.sql` 会先删除再重建业务表，**只能用于测试环境**。

## 1. 部署结构

```text
浏览器 / PDA
     │  http://<服务器IP>:8080
     ▼
┌─────────────────────────────┐
│ frontend (nginx:80)         │
│  /        → Vue 静态资源     │
│  /api/*   → backend:8080    │
│  /ws/*    → backend:8080    │
└─────────────┬───────────────┘
              │ Docker 内部网络
              ▼
┌─────────────────────────────┐
│ backend (Spring Boot:8080)  │
└─────────────┬───────────────┘
              ▼
┌─────────────────────────────┐
│ mysql (MySQL 8.0:3306)      │
│ 数据卷：mysql-data           │
│ 首次启动执行 init-test.sql   │
└─────────────────────────────┘
```

端口规划：

| 服务 | 容器端口 | 默认宿主端口 | 是否对外 | 说明 |
|---|---:|---:|---|---|
| frontend | 80 | `WEB_PORT=8080` | 是 | 前端页面入口，同时代理 API 和 WebSocket |
| backend | 8080 | `API_PORT=8081` | 可选 | 便于直接用 curl 调接口；不需要可删除 compose 中的端口映射 |
| mysql | 3306 | 不映射 | 否 | 仅容器网络内访问 |

## 2. 仓库中的部署文件

| 路径 | 作用 |
|---|---|
| `docker-compose.yml` | MySQL、后端、前端三个服务的编排 |
| `backend/Dockerfile` | 多阶段构建 Spring Boot 可执行 jar，运行时 Java 17 |
| `backend/.dockerignore` | 排除 `target/`、`.idea/` |
| `frontend/Dockerfile` | Node 20 构建 Vue，Nginx 托管 `dist/` |
| `frontend/nginx.conf` | SPA 路由回退、`/api` 反向代理、`/ws` WebSocket 代理 |
| `frontend/.dockerignore` | 排除本地 `node_modules/`、`dist/` |
| `database/init-test.sql` | 测试库表结构 + 虚构测试数据 |
| `.env.example` | 端口和 MySQL 账号模板 |

## 3. 前置条件

- Linux 服务器，建议 2 核 4 GB 以上，磁盘 10 GB 以上。
- Docker Engine 24+，Docker Compose v2（`docker compose version` 可用）。
- 服务器可访问 Docker Hub、Maven Central 和 npm registry；内网环境请先配置镜像源。
- 防火墙放行 `WEB_PORT`（默认 8080）；如需要直接调 API，再放行 `API_PORT`（默认 8081）。
- 不要把 3306 暴露到公网。

## 4. 快速部署

### 4.1 获取代码

```bash
git clone <仓库地址> pick-dispatch
cd pick-dispatch
```

### 4.2 配置环境变量

```bash
cp .env.example .env
vi .env
```

至少修改 MySQL 密码：

```dotenv
WEB_PORT=8080
API_PORT=8081

MYSQL_ROOT_PASSWORD=<强密码>
MYSQL_USER=pick
MYSQL_PASSWORD=<强密码>
```

`.env` 已被 `.gitignore` 忽略，不会提交到仓库。

### 4.3 构建并启动

```bash
docker compose up -d --build
```

首次构建需要从网络下载 Maven、npm 依赖，通常 3–10 分钟，取决于服务器网络。查看进度：

```bash
docker compose ps
docker compose logs -f mysql
docker compose logs -f backend
```

当 `docker compose ps` 中 `mysql` 和 `backend` 显示 `healthy`、`frontend` 显示 `running` 后即可访问。

### 4.4 访问入口

| 页面 | 地址 |
|---|---|
| 调度台（仪表盘） | `http://<服务器IP>:8080/` |
| 用户下单 | `http://<服务器IP>:8080/shop` |
| 仓库 PDA | `http://<服务器IP>:8080/pda` |
| 健康检查 | `http://<服务器IP>:8081/api/meta/warehouses` |

如果不想暴露后端端口，删除 `docker-compose.yml` 中 `backend.ports` 的两行映射即可，前端仍可通过 Nginx 代理访问 API。

## 5. 测试数据说明

`database/init-test.sql` 在 MySQL 数据卷首次初始化时自动执行，内容如下：

| 表 | 数量 | 说明 |
|---|---:|---|
| `warehouse` | 2 | 测试仓库A、测试仓库B |
| `goods` | 8 | 测试商品01–08，分类为测试分类A–D |
| `inventory` | 15 | 仓库A 8 条，仓库B 7 条；含库存不足、零库存场景 |
| `inventory_snapshot` | 15 | 与库存对应的初始快照 |
| `pick_task` | 7 | 已下发 2、待下发 3、已拣出 1、缺货 1 |
| `customer_order` | 7 | 待分配 3、已下发 1、拣货中 1、已完成 1、缺货 1 |
| `replenishment` | 1 | 1 张补货单，含 2 条明细 |
| `notification_log` | 3 | 对应已下发任务的通知日志 |

三个待下发任务分别覆盖：

- `PENDING / INSUFFICIENT_STOCK`：仓库B 商品02 需要 10，库存只有 6；
- `PENDING / SNAPSHOT_STALE`：仓库B 商品03 快照为 45 分钟前；
- `PENDING / NO_INVENTORY`：仓库B 商品08 没有库存记录。

预期仪表盘统计：

```json
{
  "totalTasks": 7,
  "pending": 3,
  "dispatched": 2,
  "fulfilled": 1,
  "stockout": 1,
  "inventoryRows": 15,
  "staleInventory": 1
}
```

### 注意：快照会随时间变旧

库存的 `synced_at` 是初始化脚本执行时刻，后端默认把超过 **10 分钟**的快照视为过期。因此容器启动十分钟后再新建任务，库存充足的仓库A 也会进入待下发。测试时可以先刷新快照：

```bash
# 刷新仓库A的库存时间戳，保留仓库B商品03的过期快照用于演示
docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot pick_dispatch \
   -e "UPDATE inventory SET synced_at = NOW(6), version = version + 1 WHERE warehouse_id = 1;"'
```

也可以在前端“补货/同步”页面提交一笔补货，系统会同时刷新库存并重新扫描待下发任务。

## 6. 数据初始化与重置

### 6.1 为什么重启后没有重复插入数据

MySQL 官方镜像只在数据目录为空时执行 `/docker-entrypoint-initdb.d/` 下的脚本。只要 `mysql-data` 数据卷还在，`init-test.sql` 就不会再次执行。

### 6.2 完全重置测试环境

```bash
docker compose down -v
docker compose up -d --build
```

`-v` 会删除 `mysql-data` 数据卷，下次启动重新执行初始化脚本。**该操作会清空数据库，请确认不要用于有真实数据的实例。**

### 6.3 对已有数据卷重新执行初始化脚本

```bash
docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot pick_dispatch' < database/init-test.sql
```

脚本会先 DROP 再重建业务表，同样会清空现有数据。

## 7. 常用运维命令

```bash
# 查看状态
docker compose ps

# 查看日志
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f mysql

# 重启单个服务
docker compose restart backend

# 停止（保留数据）
docker compose stop

# 停止并删除容器网络（保留数据卷）
docker compose down

# 升级：拉取代码后重新构建镜像
git pull
docker compose up -d --build
```

### 备份与恢复

```bash
# 备份
docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --databases pick_dispatch' \
  > backup-$(date +%F).sql

# 恢复
cat backup-2026-10-17.sql | docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot'
```

## 8. 部署后验证

```bash
# 仓库主数据
curl http://127.0.0.1:8081/api/meta/warehouses

# 仪表盘统计
curl http://127.0.0.1:8081/api/dashboard/summary

# 任务列表
curl "http://127.0.0.1:8081/api/pick-tasks?size=10"

# 订单列表
curl http://127.0.0.1:8081/api/orders
```

页面验证：

1. 打开 `/`，仪表盘应显示 7 个任务、3 个待下发、15 条库存；
2. 打开 `/shop`，用测试商品01 下单 2 件，应直接下发并进入“已下发”；
3. 打开 `/pda`，应看到已下发任务，可认领、拣出或标记缺货；
4. 打开 `/pending`，应看到库存不足、快照过期、无库存三个待下发任务；
5. 打开 `/inventory`，仓库B 商品03 应标记为“快照过期”。

## 9. 常见问题

| 现象 | 排查与处理 |
|---|---|
| 页面能打开，接口 502 | 后端未就绪或启动失败。`docker compose logs backend` 查看是否连不上 MySQL；等待 healthcheck 通过 |
| 数据库里没有测试数据 | `mysql-data` 卷不是首次创建。执行 `docker compose down -v` 后重新启动 |
| 启动报 `Port is already allocated` | 修改 `.env` 中的 `WEB_PORT` / `API_PORT`，重新 `docker compose up -d` |
| MySQL 容器健康检查一直失败 | 检查 `.env` 密码是否含特殊字符、数据卷权限；`docker compose logs mysql` |
| 后端连接 MySQL 超时 | 确认使用 `mysql` 作为主机名（容器网络），不要写 `localhost` |
| WebSocket 建连失败 | 若在 Nginx 前还有一层反向代理，需要同样开启 `Upgrade` / `Connection` 头；仓库内 Nginx 配置已处理 |
| 中文乱码 | 初始化脚本和 MySQL 已配置 `utf8mb4`；自建库时确认数据库、表和连接都使用 utf8mb4 |
| 时间差 8 小时 | compose 已设置 `TZ=Asia/Shanghai` 和 MySQL `--default-time-zone=+08:00`；自建环境需保持一致 |
| 镜像/依赖下载慢 | 为 Docker、Maven、npm 配置可用镜像源，或在有网络的机器上构建后推送私有镜像仓库 |

## 10. 安全建议

1. 修改 `.env` 中的默认密码，不要使用示例值。
2. 不要将 MySQL 3306 端口暴露到公网。
3. 后端 `API_PORT` 仅在需要直连调试时开放；生产环境建议只暴露 Nginx。
4. 生产环境在 Nginx 前配置 HTTPS，并限制 WebSocket 允许的 Origin（当前代码为演示方便允许所有来源）。
5. `database/init-test.sql` 是测试脚本，包含 DROP TABLE；生产环境应改用独立的迁移脚本（如 Flyway/Liquibase）并关闭 `app.demo.enabled`。
6. `.env`、数据库备份文件不要提交到 Git。
