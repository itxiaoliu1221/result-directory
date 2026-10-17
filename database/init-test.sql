-- ============================================================================
-- 测试环境初始化脚本
--
-- 用途：为 Docker 测试部署提供完整的表结构和演示数据。
-- 数据全部为虚构的“测试仓库 / 测试商品 / 测试订单”，不包含任何真实业务数据。
--
-- 警告：
--   1. 本脚本会先 DROP 再重建业务表，只能用于测试环境，禁止在生产库执行。
--   2. MySQL 官方镜像仅在数据卷为空时自动执行本脚本；已有数据卷不会重复执行。
--
-- 表结构与后端 Hibernate 实体保持一致（由本机 MySQL 8.0.33 实际生成的 DDL 导出）。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS pick_dispatch
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE pick_dispatch;

SET NAMES utf8mb4;
SET time_zone = '+08:00';

DROP TABLE IF EXISTS notification_log;
DROP TABLE IF EXISTS replenishment_item;
DROP TABLE IF EXISTS replenishment;
DROP TABLE IF EXISTS customer_order;
DROP TABLE IF EXISTS pick_task;
DROP TABLE IF EXISTS inventory_snapshot;
DROP TABLE IF EXISTS inventory;
DROP TABLE IF EXISTS goods;
DROP TABLE IF EXISTS warehouse;

-- ---------------------------------------------------------------------------
-- 表结构
-- ---------------------------------------------------------------------------

CREATE TABLE warehouse (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  name VARCHAR(64) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UK_9wk4ocyt0wv0hpffpr41aoweu (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE goods (
  id BIGINT NOT NULL AUTO_INCREMENT,
  category VARCHAR(64) NOT NULL,
  code VARCHAR(32) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  name VARCHAR(64) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UK_l8eh87voijifwa1sd41xavgn8 (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inventory (
  id BIGINT NOT NULL AUTO_INCREMENT,
  available_qty INT NOT NULL,
  goods_id BIGINT NOT NULL,
  synced_at DATETIME(6) NOT NULL,
  version BIGINT DEFAULT NULL,
  warehouse_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_inventory_warehouse_goods (warehouse_id, goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inventory_snapshot (
  id BIGINT NOT NULL AUTO_INCREMENT,
  created_at DATETIME(6) NOT NULL,
  goods_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  source VARCHAR(32) NOT NULL,
  synced_at DATETIME(6) NOT NULL,
  warehouse_id BIGINT NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pick_task (
  id BIGINT NOT NULL AUTO_INCREMENT,
  completed_at DATETIME(6) DEFAULT NULL,
  created_at DATETIME(6) NOT NULL,
  dispatched_at DATETIME(6) DEFAULT NULL,
  goods_id BIGINT NOT NULL,
  reason VARCHAR(32) DEFAULT NULL,
  request_no VARCHAR(64) DEFAULT NULL,
  required_qty INT NOT NULL,
  result ENUM('FULFILLED','STOCKOUT') DEFAULT NULL,
  snapshot_id BIGINT DEFAULT NULL,
  snapshot_qty INT DEFAULT NULL,
  snapshot_synced_at DATETIME(6) DEFAULT NULL,
  status ENUM('PENDING','DISPATCHED','FULFILLED','STOCKOUT','CANCELLED') NOT NULL,
  task_no VARCHAR(48) NOT NULL,
  warehouse_id BIGINT NOT NULL,
  claimed_at DATETIME(6) DEFAULT NULL,
  claimed_by VARCHAR(64) DEFAULT NULL,
  order_id BIGINT DEFAULT NULL,
  order_no VARCHAR(64) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UK_2ao885g2jxfshw95hnurpagky (task_no),
  UNIQUE KEY UK_rwlm4ckvhmuihcav7cd5kdp0 (request_no),
  KEY idx_pick_task_queue (status, warehouse_id, goods_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE customer_order (
  id BIGINT NOT NULL AUTO_INCREMENT,
  claimed_at DATETIME(6) DEFAULT NULL,
  completed_at DATETIME(6) DEFAULT NULL,
  created_at DATETIME(6) NOT NULL,
  customer_name VARCHAR(64) DEFAULT NULL,
  dispatched_at DATETIME(6) DEFAULT NULL,
  goods_id BIGINT NOT NULL,
  order_no VARCHAR(48) NOT NULL,
  quantity INT NOT NULL,
  status ENUM('PENDING','DISPATCHED','PICKING','PICKED','STOCKOUT','CANCELLED') NOT NULL,
  task_id BIGINT DEFAULT NULL,
  updated_at DATETIME(6) DEFAULT NULL,
  warehouse_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UK_g6sf1b42g952lldbjuvtwxirn (order_no),
  KEY idx_customer_order_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE replenishment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  created_at DATETIME(6) NOT NULL,
  request_no VARCHAR(64) DEFAULT NULL,
  source VARCHAR(32) NOT NULL,
  warehouse_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UK_g9c3di4kp3qy9yag8s0kmgkcc (request_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE replenishment_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  goods_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  replenishment_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  KEY FKiwpggxb7a6t0rxelexcphqmib (replenishment_id),
  CONSTRAINT FKiwpggxb7a6t0rxelexcphqmib
    FOREIGN KEY (replenishment_id) REFERENCES replenishment (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  channel VARCHAR(32) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  error VARCHAR(2000) DEFAULT NULL,
  event_type VARCHAR(32) NOT NULL,
  payload VARCHAR(2000) DEFAULT NULL,
  status VARCHAR(16) NOT NULL,
  task_id BIGINT NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 测试主数据：仓库与商品
-- ---------------------------------------------------------------------------

SET @now6 = NOW(6);

INSERT INTO warehouse (id, code, name, created_at) VALUES
  (1, 'TEST-WH-A', '测试仓库A', @now6 - INTERVAL 7 DAY),
  (2, 'TEST-WH-B', '测试仓库B', @now6 - INTERVAL 7 DAY);

INSERT INTO goods (id, code, name, category, created_at) VALUES
  (1, 'TEST-G01', '测试商品01', '测试分类A', @now6 - INTERVAL 7 DAY),
  (2, 'TEST-G02', '测试商品02', '测试分类A', @now6 - INTERVAL 7 DAY),
  (3, 'TEST-G03', '测试商品03', '测试分类B', @now6 - INTERVAL 7 DAY),
  (4, 'TEST-G04', '测试商品04', '测试分类B', @now6 - INTERVAL 7 DAY),
  (5, 'TEST-G05', '测试商品05', '测试分类C', @now6 - INTERVAL 7 DAY),
  (6, 'TEST-G06', '测试商品06', '测试分类C', @now6 - INTERVAL 7 DAY),
  (7, 'TEST-G07', '测试商品07', '测试分类D', @now6 - INTERVAL 7 DAY),
  (8, 'TEST-G08', '测试商品08', '测试分类D', @now6 - INTERVAL 7 DAY);

-- ---------------------------------------------------------------------------
-- 库存与库存快照
--
-- 测试仓库A：库存充足、快照新鲜，可直接下发。
-- 测试仓库B：
--   商品02 库存不足；
--   商品03 快照为 45 分钟前，用于演示“快照过期”；
--   商品08 没有库存记录，用于演示“无库存”。
-- ---------------------------------------------------------------------------

INSERT INTO inventory (id, warehouse_id, goods_id, available_qty, synced_at, version) VALUES
  (1,  1, 1, 47,  @now6 - INTERVAL 2 MINUTE,  0),
  (2,  1, 2, 8,   @now6 - INTERVAL 3 MINUTE,  0),
  (3,  1, 3, 0,   @now6 - INTERVAL 1 MINUTE,  0),
  (4,  1, 4, 25,  @now6 - INTERVAL 4 MINUTE,  0),
  (5,  1, 5, 3,   @now6 - INTERVAL 2 MINUTE,  0),
  (6,  1, 6, 94,  @now6 - INTERVAL 5 MINUTE,  0),
  (7,  1, 7, 20,  @now6 - INTERVAL 1 MINUTE,  0),
  (8,  1, 8, 12,  @now6 - INTERVAL 3 MINUTE,  0),
  (9,  2, 1, 40,  @now6 - INTERVAL 2 MINUTE,  0),
  (10, 2, 2, 6,   @now6 - INTERVAL 4 MINUTE,  0),
  (11, 2, 3, 25,  @now6 - INTERVAL 45 MINUTE, 0),
  (12, 2, 4, 18,  @now6 - INTERVAL 3 MINUTE,  0),
  (13, 2, 5, 0,   @now6 - INTERVAL 6 MINUTE,  0),
  (14, 2, 6, 60,  @now6 - INTERVAL 2 MINUTE,  0),
  (15, 2, 7, 9,   @now6 - INTERVAL 4 MINUTE,  0);

INSERT INTO inventory_snapshot
  (id, warehouse_id, goods_id, quantity, source, synced_at, created_at) VALUES
  (1,  1, 1, 47,  'INIT', @now6 - INTERVAL 2 MINUTE,  @now6 - INTERVAL 2 MINUTE),
  (2,  1, 2, 8,   'INIT', @now6 - INTERVAL 3 MINUTE,  @now6 - INTERVAL 3 MINUTE),
  (3,  1, 3, 0,   'INIT', @now6 - INTERVAL 1 MINUTE,  @now6 - INTERVAL 1 MINUTE),
  (4,  1, 4, 25,  'INIT', @now6 - INTERVAL 4 MINUTE,  @now6 - INTERVAL 4 MINUTE),
  (5,  1, 5, 3,   'INIT', @now6 - INTERVAL 2 MINUTE,  @now6 - INTERVAL 2 MINUTE),
  (6,  1, 6, 94,  'INIT', @now6 - INTERVAL 5 MINUTE,  @now6 - INTERVAL 5 MINUTE),
  (7,  1, 7, 20,  'INIT', @now6 - INTERVAL 1 MINUTE,  @now6 - INTERVAL 1 MINUTE),
  (8,  1, 8, 12,  'INIT', @now6 - INTERVAL 3 MINUTE,  @now6 - INTERVAL 3 MINUTE),
  (9,  2, 1, 40,  'INIT', @now6 - INTERVAL 2 MINUTE,  @now6 - INTERVAL 2 MINUTE),
  (10, 2, 2, 6,   'INIT', @now6 - INTERVAL 4 MINUTE,  @now6 - INTERVAL 4 MINUTE),
  (11, 2, 3, 25,  'INIT', @now6 - INTERVAL 45 MINUTE, @now6 - INTERVAL 45 MINUTE),
  (12, 2, 4, 18,  'INIT', @now6 - INTERVAL 3 MINUTE,  @now6 - INTERVAL 3 MINUTE),
  (13, 2, 5, 0,   'INIT', @now6 - INTERVAL 6 MINUTE,  @now6 - INTERVAL 6 MINUTE),
  (14, 2, 6, 60,  'INIT', @now6 - INTERVAL 2 MINUTE,  @now6 - INTERVAL 2 MINUTE),
  (15, 2, 7, 9,   'INIT', @now6 - INTERVAL 4 MINUTE,  @now6 - INTERVAL 4 MINUTE);

-- ---------------------------------------------------------------------------
-- 测试订单与拣货任务
--
-- 覆盖状态：
--   DISPATCHED / PENDING(INSUFFICIENT_STOCK) / PENDING(SNAPSHOT_STALE)
--   PENDING(NO_INVENTORY) / FULFILLED / STOCKOUT / PICKING
-- ---------------------------------------------------------------------------

INSERT INTO customer_order
  (id, order_no, customer_name, warehouse_id, goods_id, quantity, status, task_id,
   created_at, updated_at, dispatched_at, claimed_at, completed_at) VALUES
  (1, 'TEST-ORDER-0001', '测试客户甲', 1, 1, 3, 'DISPATCHED', 1,
   @now6 - INTERVAL 10 MINUTE, @now6 - INTERVAL 10 MINUTE, @now6 - INTERVAL 10 MINUTE, NULL, NULL),
  (2, 'TEST-ORDER-0002', '测试客户乙', 2, 2, 10, 'PENDING', 2,
   @now6 - INTERVAL 30 MINUTE, @now6 - INTERVAL 30 MINUTE, NULL, NULL, NULL),
  (3, 'TEST-ORDER-0003', '测试客户丙', 2, 3, 5, 'PENDING', 3,
   @now6 - INTERVAL 20 MINUTE, @now6 - INTERVAL 20 MINUTE, NULL, NULL, NULL),
  (4, 'TEST-ORDER-0004', '测试客户丁', 2, 8, 4, 'PENDING', 4,
   @now6 - INTERVAL 15 MINUTE, @now6 - INTERVAL 15 MINUTE, NULL, NULL, NULL),
  (5, 'TEST-ORDER-0005', '测试客户戊', 1, 4, 5, 'PICKED', 5,
   @now6 - INTERVAL 1 DAY - INTERVAL 5 MINUTE,
   @now6 - INTERVAL 1 DAY,
   @now6 - INTERVAL 1 DAY - INTERVAL 5 MINUTE,
   @now6 - INTERVAL 1 DAY - INTERVAL 4 MINUTE,
   @now6 - INTERVAL 1 DAY),
  (6, 'TEST-ORDER-0006', '测试客户己', 1, 5, 2, 'STOCKOUT', 6,
   @now6 - INTERVAL 1 DAY - INTERVAL 5 MINUTE,
   @now6 - INTERVAL 1 DAY,
   @now6 - INTERVAL 1 DAY - INTERVAL 5 MINUTE,
   @now6 - INTERVAL 1 DAY - INTERVAL 4 MINUTE,
   @now6 - INTERVAL 1 DAY),
  (7, 'TEST-ORDER-0007', '测试客户庚', 1, 6, 6, 'PICKING', 7,
   @now6 - INTERVAL 6 MINUTE, @now6 - INTERVAL 5 MINUTE,
   @now6 - INTERVAL 6 MINUTE, @now6 - INTERVAL 5 MINUTE, NULL);

INSERT INTO pick_task
  (id, task_no, warehouse_id, goods_id, required_qty, status, reason,
   snapshot_id, snapshot_qty, snapshot_synced_at, request_no, order_id, order_no,
   claimed_by, claimed_at, created_at, dispatched_at, completed_at, result) VALUES
  (1, 'TEST-PT-0001', 1, 1, 3, 'DISPATCHED', NULL,
   1, 50, @now6 - INTERVAL 12 MINUTE, 'TEST-REQ-0001', 1, 'TEST-ORDER-0001',
   NULL, NULL, @now6 - INTERVAL 10 MINUTE, @now6 - INTERVAL 10 MINUTE, NULL, NULL),
  (2, 'TEST-PT-0002', 2, 2, 10, 'PENDING', 'INSUFFICIENT_STOCK',
   10, 6, @now6 - INTERVAL 4 MINUTE, 'TEST-REQ-0002', 2, 'TEST-ORDER-0002',
   NULL, NULL, @now6 - INTERVAL 30 MINUTE, NULL, NULL, NULL),
  (3, 'TEST-PT-0003', 2, 3, 5, 'PENDING', 'SNAPSHOT_STALE',
   11, 25, @now6 - INTERVAL 45 MINUTE, 'TEST-REQ-0003', 3, 'TEST-ORDER-0003',
   NULL, NULL, @now6 - INTERVAL 20 MINUTE, NULL, NULL, NULL),
  (4, 'TEST-PT-0004', 2, 8, 4, 'PENDING', 'NO_INVENTORY',
   NULL, NULL, NULL, 'TEST-REQ-0004', 4, 'TEST-ORDER-0004',
   NULL, NULL, @now6 - INTERVAL 15 MINUTE, NULL, NULL, NULL),
  (5, 'TEST-PT-0005', 1, 4, 5, 'FULFILLED', NULL,
   4, 30, @now6 - INTERVAL 1 DAY - INTERVAL 10 MINUTE, 'TEST-REQ-0005', 5, 'TEST-ORDER-0005',
   '测试拣货员01', @now6 - INTERVAL 1 DAY - INTERVAL 4 MINUTE,
   @now6 - INTERVAL 1 DAY - INTERVAL 5 MINUTE,
   @now6 - INTERVAL 1 DAY - INTERVAL 5 MINUTE,
   @now6 - INTERVAL 1 DAY, 'FULFILLED'),
  (6, 'TEST-PT-0006', 1, 5, 2, 'STOCKOUT', NULL,
   5, 5, @now6 - INTERVAL 1 DAY - INTERVAL 10 MINUTE, 'TEST-REQ-0006', 6, 'TEST-ORDER-0006',
   '测试拣货员01', @now6 - INTERVAL 1 DAY - INTERVAL 4 MINUTE,
   @now6 - INTERVAL 1 DAY - INTERVAL 5 MINUTE,
   @now6 - INTERVAL 1 DAY - INTERVAL 5 MINUTE,
   @now6 - INTERVAL 1 DAY, 'STOCKOUT'),
  (7, 'TEST-PT-0007', 1, 6, 6, 'DISPATCHED', NULL,
   6, 100, @now6 - INTERVAL 8 MINUTE, 'TEST-REQ-0007', 7, 'TEST-ORDER-0007',
   '测试拣货员01', @now6 - INTERVAL 5 MINUTE,
   @now6 - INTERVAL 6 MINUTE, @now6 - INTERVAL 6 MINUTE, NULL, NULL);

-- ---------------------------------------------------------------------------
-- 补货记录与通知日志
-- ---------------------------------------------------------------------------

INSERT INTO replenishment (id, request_no, warehouse_id, source, created_at) VALUES
  (1, 'TEST-RP-0001', 1, 'test', @now6 - INTERVAL 3 DAY);

INSERT INTO replenishment_item (id, goods_id, quantity, replenishment_id) VALUES
  (1, 3, 20, 1),
  (2, 6, 50, 1);

INSERT INTO notification_log
  (id, task_id, event_type, channel, payload, status, error, created_at) VALUES
  (1, 1, 'TASK_DISPATCHED', 'LOG',
   'taskNo=TEST-PT-0001, warehouse=测试仓库A, goods=测试商品01, qty=3',
   'SUCCESS', NULL, @now6 - INTERVAL 10 MINUTE),
  (2, 5, 'TASK_DISPATCHED', 'LOG',
   'taskNo=TEST-PT-0005, warehouse=测试仓库A, goods=测试商品04, qty=5',
   'SUCCESS', NULL, @now6 - INTERVAL 1 DAY - INTERVAL 5 MINUTE),
  (3, 7, 'TASK_DISPATCHED', 'LOG',
   'taskNo=TEST-PT-0007, warehouse=测试仓库A, goods=测试商品06, qty=6',
   'SUCCESS', NULL, @now6 - INTERVAL 6 MINUTE);
