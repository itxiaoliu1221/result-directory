# Docker 部署指南（80 端口 + /front 与 /backend 路径）

本文档描述如何在服务器 `http://39.106.14.83/` 上部署本项目。服务器**只对外开放 80 端口**，由宿主机 nginx 按路径转发到 Docker 容器；前端容器端口 7998、后端容器端口 7999 只监听 `127.0.0.1`，不直接对外暴露。

- 前端页面统一走 `/front/`
- 后端 API 和 WebSocket 统一走 `/backend/`

测试数据全部为虚构的“测试仓库 / 测试商品 / 测试订单”，不含真实业务数据。`database/init-test.sql` 只用于测试环境。

## 1. 部署结构

```text
浏览器 / PDA
     │
     │  http://39.106.14.83/  (仅 80)
     ▼
┌──────────────────────────────────────────────┐
│ 宿主机 nginx                                  │
│  /front/       → 127.0.0.1:7998/             │
│  /backend/api/ → 127.0.0.1:7999/backend/api/ │
│  /backend/ws/  → 127.0.0.1:7999/backend/ws/  │
└───────────────┬──────────────────────────────┘
                │ Docker NAT 网络 pick-dispatch-net
                ▼
┌───────────────────────┐      ┌───────────────────────┐
│ frontend (nginx:80)   │      │ backend (Spring:7999) │
│ 对外映射 127.0.0.1:7998│      │ 对外映射 127.0.0.1:7999│
└───────────────────────┘      └───────────┬───────────┘
                                           ▼
                                ┌───────────────────────┐
                                │ mysql (3306，仅内网)   │
                                └───────────────────────┘
```

路径约定：

| 对外路径 | 转发到 | 用途 |
|---|---|---|
| `/front/` | `127.0.0.1:7998/` | 前端 SPA（静态资源、`/shop`、`/pda`） |
| `/backend/api/...` | `127.0.0.1:7999/backend/api/...` | REST API |
| `/backend/ws/picking` | `127.0.0.1:7999/backend/ws/picking` | WebSocket 实时通知 |

访问地址：

- 调度台：`http://39.106.14.83/front/`
- 用户下单：`http://39.106.14.83/front/shop`
- 仓库 PDA：`http://39.106.14.83/front/pda`
- API 健康检查：`http://39.106.14.83/backend/api/meta/warehouses`
- WebSocket：`ws://39.106.14.83/backend/ws/picking`

## 2. 前置条件

- Linux 服务器，Docker Engine 24+、Docker Compose v2。
- 宿主机 nginx 已安装并监听 80。
- 防火墙/安全组只放行 80；**不要**放行 7998、7999、3306。
- 服务器能访问 Docker Hub、Maven Central、npm registry；内网环境先配置镜像源。
- 如果 80 端口已被其他站点占用，把下面的 `server` 块合并到现有 nginx 配置中即可；`/front/` 和 `/backend/` 前缀用于避免与其他容器路由冲突。

## 3. 仓库中的部署文件

| 路径 | 作用 |
|---|---|
| `docker-compose.yml` | MySQL、后端、前端三个服务，统一接入 `pick-dispatch-net` |
| `backend/Dockerfile` | 多阶段构建 Spring Boot，运行端口 7999，context-path `/backend` |
| `backend/.dockerignore` | 排除 `target/`、`.idea/` |
| `frontend/Dockerfile` | Node 20 构建 Vue，Nginx 托管静态文件 |
| `frontend/nginx.conf` | 前端容器内的 SPA 路由回退 |
| `deploy/nginx-demo.conf` | 宿主机 nginx 的完整 server 块（`/front/` + `/backend/`） |
| `database/init-test.sql` | 测试库表结构 + 虚构测试数据 |
| `.env.example` | 端口和 MySQL 账号模板 |

前端构建时使用 `VITE_APP_BASE=/front/`（Dockerfile 默认值），静态资源 URL 都带 `/front/` 前缀；宿主机 nginx 在转发到前端容器时会去掉该前缀。后端通过 `server.servlet.context-path=/backend` 提供 `/backend/api` 和 `/backend/ws`。

## 4. 启动容器

```bash
cd /path/to/result-directory
cp .env.example .env
vi .env          # 修改 MySQL 密码；端口默认 7998/7999
docker compose up -d --build
```

检查状态：

```bash
docker compose ps
docker compose logs -f backend
docker compose logs -f frontend
```

容器端口只绑定回环地址：

- `127.0.0.1:7998 -> frontend:80`
- `127.0.0.1:7999 -> backend:7999`
- MySQL 不映射宿主机端口

验证后端（在服务器上）：

```bash
curl -fsS http://127.0.0.1:7999/backend/api/meta/warehouses
curl -I http://127.0.0.1:7998/
```

## 5. 配置宿主机 nginx

把 `deploy/nginx-demo.conf` 中的 `server` 块加入 nginx 的 `http {}` 配置，或：

```bash
sudo cp deploy/nginx-demo.conf /etc/nginx/conf.d/pick-dispatch.conf
sudo nginx -t
sudo systemctl reload nginx
```

核心配置如下（完整版见 `deploy/nginx-demo.conf`）：

```nginx
server {
    listen 80;
    server_name 39.106.14.83;

    location = /front {
        return 301 /front/;
    }

    location /front/ {
        proxy_pass http://127.0.0.1:7998/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /backend/api/ {
        proxy_pass http://127.0.0.1:7999;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /backend/ws/ {
        proxy_pass http://127.0.0.1:7999;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        proxy_buffering off;
    }
}
```

说明：

- `/front/` 的 `proxy_pass` 末尾有 `/`，会去掉 `/front/` 前缀，前端容器按根路径提供静态资源和 SPA 回退。
- `/backend/` 的 `proxy_pass` **没有**末尾 `/`，保留完整路径；Spring Boot 的 context-path 就是 `/backend`。
- WebSocket 走 `/backend/ws/`，需要 `Upgrade` / `Connection` 头和较长的读超时。

### 如果 nginx 本身也是容器

把 nginx 容器接入 `pick-dispatch-net`：

```bash
docker network connect pick-dispatch-net <nginx-container>
```

然后把地址改成 Docker 服务名：

```nginx
proxy_pass http://frontend:80/;
proxy_pass http://backend:7999;
```

不需要映射 7998/7999 到宿主机；容器之间通过 `pick-dispatch-net` 直接通信。

## 6. WebSocket 连通性验证

WebSocket 地址：

```text
ws://39.106.14.83/backend/ws/picking
```

浏览器控制台验证：

```js
const ws = new WebSocket('ws://39.106.14.83/backend/ws/picking')
ws.onopen = () => console.log('WS connected')
ws.onmessage = (event) => console.log('WS message', event.data)
```

预期：连接成功，下单/认领/完成时收到 `TASK_DISPATCHED`、`TASK_CLAIMED`、`TASK_COMPLETED`。

也可以用 `wscat`：

```bash
npx wscat -c ws://39.106.14.83/backend/ws/picking
```

如果返回 400/426/502，优先检查：

1. `/backend/ws/` 的 `Upgrade` / `Connection` 头是否存在；
2. `proxy_read_timeout` 是否足够长；
3. 后端容器健康检查是否通过；
4. 宿主机 nginx 是否把 `/backend/ws/` 错误地转到了前端容器；
5. 后端端口是否仍为 7999，context-path 是否为 `/backend`（`docker compose ps`、`docker compose logs backend`）。

## 7. 页面联调流程

1. 打开 `http://39.106.14.83/front/`，看到调度台仪表盘。
2. 打开 `http://39.106.14.83/front/shop`，选择商品/数量下单：
   - 库存足够且快照新鲜 → 订单“已分配”，PDA 收到实时通知；
   - 库存不足/快照过期 → 订单“待分配”。
3. 打开 `http://39.106.14.83/front/pda`，填写拣货员名称：
   - 待认领任务点击“认领任务” → 订单变为“拣货中”；
   - 点击“拣出完成”/“缺货” → 订单闭环。
4. 在调度台查看任务、待下发队列、库存和补货。

## 8. 测试数据与重置

`database/init-test.sql` 会在 MySQL 数据卷首次创建时执行，写入：

- 2 个测试仓库、8 个测试商品、15 条库存；
- 若干测试任务/订单。

演示数据均为虚构值，不作为业务结论依据。

需要重置测试数据时（**会删除 MySQL 数据卷**）：

```bash
docker compose down -v
docker compose up -d --build
```

只重启服务、保留数据：

```bash
docker compose restart
```

## 9. 更新代码

```bash
git pull
docker compose up -d --build
```

- 后端代码变化：重新构建 backend。
- 前端代码变化：重新构建 frontend（会重新执行 `npm run build`，使用 `/front/` base）。
- 仅 nginx 路径变化：修改 `deploy/nginx-demo.conf`，`nginx -t && systemctl reload nginx`。

## 10. 常见问题

| 现象 | 排查 |
|---|---|
| `/front/` 白屏、资源 404 | 前端是否用 `VITE_APP_BASE=/front/` 构建；宿主机 nginx 是否去掉 `/front/` 前缀转发到 7998 |
| `/backend/api/...` 502 | 后端容器是否健康；`docker compose ps`；`docker compose logs backend` |
| `/backend/api` 404 | 后端 context-path 是否为 `/backend`；nginx 是否保留了 `/backend` 路径 |
| WebSocket 连接失败/400 | 检查 `/backend/ws/` 的 Upgrade 头、超时时间、转发路径 |
| 页面能打开但请求 404 | 前端构建 base 与 nginx 路径不一致；确认访问的是 `/front/` 而不是 `/` |
| 7998/7999 被外部访问 | compose 已绑定 `127.0.0.1`；检查是否被其他配置覆盖 |
| MySQL 连接失败 | 检查 `.env` 密码、`docker compose logs mysql`；数据卷初始化只执行一次 |
| 端口被占用 | 修改 `.env` 的 `WEB_PORT`/`API_PORT`，同步修改 nginx `proxy_pass` |

## 11. 安全与边界

- 对外只开放 80；7998/7999 绑定 `127.0.0.1`；MySQL 不映射宿主机。
- 演示项目没有登录/权限体系，拣货员名称由前端输入；生产环境需补认证、审计和 WebSocket 鉴权。
- 通知当前为日志桩；生产环境应接短信/企业微信/消息队列，并加重试与幂等。
- 当前 WebSocket 为单实例内存会话；多实例部署需要 Redis Pub/Sub 或 STOMP broker。
- `database/init-test.sql` 只用于测试环境，不要用于生产数据。
