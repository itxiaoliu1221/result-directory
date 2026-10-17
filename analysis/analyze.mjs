#!/usr/bin/env node
/**
 * Reproduce the numbers quoted in README.md from data/picks.csv.
 * Usage: node analysis/analyze.mjs
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const CSV_PATH = path.join(ROOT, 'data', 'picks.csv')
const OUT_DIR = path.join(ROOT, 'analysis')
const STOCKOUT_MIN = 75
const BLOCK_MIN = 15
const MAX_LAG_MIN = 10
const EXCLUDED_WAREHOUSE = '华东一号分拨仓'

function parseCsv(text) {
  const rows = []
  let row = []
  let field = ''
  let quoted = false
  for (let i = 0; i < text.length; i++) {
    const c = text[i]
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') {
        field += '"'
        i++
      } else if (c === '"') {
        quoted = false
      } else {
        field += c
      }
    } else if (c === '"') {
      quoted = true
    } else if (c === ',') {
      row.push(field)
      field = ''
    } else if (c === '\n' || c === '\r') {
      if (c === '\r' && text[i + 1] === '\n') i++
      row.push(field)
      if (row.some((v) => v !== '')) rows.push(row)
      row = []
      field = ''
    } else {
      field += c
    }
  }
  if (field !== '' || row.length) {
    row.push(field)
    if (row.some((v) => v !== '')) rows.push(row)
  }
  const [header, ...data] = rows
  return data.map((values) => Object.fromEntries(header.map((h, i) => [h, values[i] ?? ''])))
}

const pct = (num, den) => (den ? (100 * num) / den : null)
const round = (x, digits = 2) => (x == null ? null : Number(x.toFixed(digits)))

const rawRows = parseCsv(fs.readFileSync(CSV_PATH, 'utf8'))
const groups = new Map()
for (const row of rawRows) {
  const list = groups.get(row.pick_id) ?? []
  list.push(row)
  groups.set(row.pick_id, list)
}
const duplicateGroups = [...groups.entries()].filter(([, list]) => list.length > 1)
const duplicatesAllIdentical = duplicateGroups.every(([, list]) => {
  const first = JSON.stringify(list[0])
  return list.every((row) => JSON.stringify(row) === first)
})
const rows = [...groups.values()].map((list) => list[0])

for (const row of rows) {
  const pick = Date.parse(row.pick_time)
  const synced = Date.parse(row.stock_synced_at)
  row.lagMin = (pick - synced) / 60000
  row.reported = row.fulfilled === '0' || row.fulfilled === '1'
  row.stockout = row.fulfilled === '0'
}

function stat(list) {
  const reported = list.filter((row) => row.reported)
  const stockouts = reported.filter((row) => row.stockout).length
  return { picks: list.length, reported: reported.length, stockouts, rate: pct(stockouts, reported.length) }
}

const overall = stat(rows)
const lagBuckets = [[0, 10], [10, 15], [15, 30], [30, Infinity]].map(([lo, hi]) => {
  const subset = rows.filter((row) => row.lagMin >= lo && row.lagMin < hi)
  return { label: hi === Infinity ? `${lo}+ min` : `${lo}-${hi} min`, ...stat(subset) }
})

const warehouses = [...new Set(rows.map((row) => row.warehouse))].sort().map((warehouse) => {
  const subset = rows.filter((row) => row.warehouse === warehouse)
  return {
    warehouse,
    total: subset.length,
    fresh: stat(subset.filter((row) => row.lagMin < MAX_LAG_MIN)),
    stale: stat(subset.filter((row) => row.lagMin >= MAX_LAG_MIN))
  }
})

function policy(threshold, excludeWarehouse) {
  const blocked = rows.filter((row) => row.lagMin >= threshold && (!excludeWarehouse || row.warehouse !== EXCLUDED_WAREHOUSE))
  const s = stat(blocked)
  const saved = s.stockouts * STOCKOUT_MIN
  const cost = blocked.length * BLOCK_MIN
  return {
    thresholdMin: threshold,
    blocked: blocked.length,
    caught: s.stockouts,
    precision: pct(s.stockouts, blocked.length),
    savedMin: saved,
    costMin: cost,
    netMin: saved - cost
  }
}

const chosen = policy(MAX_LAG_MIN, true)
const global15 = policy(15, false)
const global10 = policy(10, false)
const sensitivity = [8, 10, 15, 20, 30].map((threshold) => policy(threshold, true))
const excluded = rows.filter((row) => row.warehouse === EXCLUDED_WAREHOUSE && row.lagMin >= MAX_LAG_MIN)
const excludedStat = stat(excluded)
const baselineWaste = overall.stockouts * STOCKOUT_MIN

const zeroStockQty = rows.filter((row) => Number(row.stock_qty) === 0)
const zeroStockQtyStat = stat(zeroStockQty)
const zeroStockQtySuccess = zeroStockQty.filter((row) => row.fulfilled === '1').length

const metrics = {
  dataQuality: {
    rawRows: rawRows.length,
    uniquePickIds: rows.length,
    duplicateRowsRemoved: rawRows.length - rows.length,
    duplicateGroups: duplicateGroups.length,
    duplicatesAllIdentical,
    blanks: overall.picks - overall.reported
  },
  overall,
  lagBuckets,
  warehouses,
  gate: {
    rule: `snapshot_age_min >= ${MAX_LAG_MIN} and warehouse != '${EXCLUDED_WAREHOUSE}'`,
    chosen,
    coverageOfStockouts: pct(chosen.caught, overall.stockouts),
    reductionOfBaselineWaste: pct(chosen.netMin, baselineWaste),
    global15,
    global10,
    sensitivity,
    extraVsGlobal15: chosen.netMin - global15.netMin,
    excludedWarehouse: {
      name: EXCLUDED_WAREHOUSE,
      stalePicks: excluded.length,
      stockouts: excludedStat.stockouts,
      rate: excludedStat.rate,
      netIfBlockedMin: excludedStat.stockouts * STOCKOUT_MIN - excluded.length * BLOCK_MIN
    }
  },
  baseline: {
    stockouts: overall.stockouts,
    wasteMin: baselineWaste
  },
  stockQtyEvidence: {
    zeroStockQtyPicks: zeroStockQty.length,
    zeroStockQtyReported: zeroStockQtyStat.reported,
    zeroStockQtyStockouts: zeroStockQtyStat.stockouts,
    zeroStockQtyRate: zeroStockQtyStat.rate,
    zeroStockQtySuccesses: zeroStockQtySuccess
  }
}

fs.writeFileSync(path.join(OUT_DIR, 'metrics.json'), `${JSON.stringify(metrics, null, 2)}\n`)

const fmt = (x, digits = 1) => (x == null ? '-' : Number(x).toFixed(digits))
const report = `# picks.csv 分析结果

由 \`node analysis/analyze.mjs\` 自动生成。

## 数据质量

- 原始行数：${rawRows.length}
- 去重后任务数：${rows.length}
- 完全重复的 pick_id：${duplicateGroups.length} 组，字段是否完全一致：${duplicatesAllIdentical ? '是' : '否'}
- 未回传：${metrics.dataQuality.blanks}

## 总体

- 缺货：${overall.stockouts} / 有回传 ${overall.reported}，缺货率 ${fmt(overall.rate, 2)}%
- 缺货工时：${baselineWaste} 分钟（${fmt(baselineWaste / 60, 1)} 小时）

## 快照年龄与缺货率

| 快照年龄 | 任务数 | 缺货数 | 缺货率 |
|---|---:|---:|---:|
${lagBuckets.map((b) => `| ${b.label} | ${b.picks} | ${b.stockouts} | ${fmt(b.rate)}% |`).join('\n')}

## 分仓：新鲜 vs 过期（${MAX_LAG_MIN} 分钟）

| 仓库 | 任务数 | 新鲜任务 | 新鲜缺货率 | 过期任务 | 过期缺货率 |
|---|---:|---:|---:|---:|---:|
${warehouses.map((w) => `| ${w.warehouse} | ${w.total} | ${w.fresh.picks} | ${fmt(w.fresh.rate)}% | ${w.stale.picks} | ${fmt(w.stale.rate)}% |`).join('\n')}

## 推荐规则

\`${metrics.gate.rule}\`

- 阻断任务：${chosen.blocked}
- 抓到真实缺货：${chosen.caught}
- 命中率：${fmt(chosen.precision)}%
- 避免工时：${chosen.savedMin} 分钟
- 复核成本：${chosen.costMin} 分钟
- 净收益：${chosen.netMin} 分钟（${fmt(chosen.netMin / 60, 1)} 小时）
- 覆盖已确认缺货：${fmt(metrics.gate.coverageOfStockouts)}%
- 基线缺货工时下降：${fmt(metrics.gate.reductionOfBaselineWaste)}%
- 全局 15 分钟规则净收益：${global15.netMin} 分钟；按仓规则多 ${metrics.gate.extraVsGlobal15} 分钟
- 排除 ${EXCLUDED_WAREHOUSE}：过期 ${excluded.length} 次、缺货 ${excludedStat.stockouts} 次；若阻断净收益 ${metrics.gate.excludedWarehouse.netIfBlockedMin} 分钟

## 阈值敏感性（按仓规则，排除 ${EXCLUDED_WAREHOUSE}）

| 阈值 | 阻断数 | 抓到缺货 | 净收益（分钟） |
|---:|---:|---:|---:|
${sensitivity.map((s) => `| ${s.thresholdMin} 分钟 | ${s.blocked} | ${s.caught} | ${s.netMin} |`).join('\n')}

## 账面库存的证据

- \`stock_qty = 0\`：${zeroStockQty.length} 条任务，其中有回传 ${zeroStockQtyStat.reported} 条，缺货 ${zeroStockQtyStat.stockouts} 条（${fmt(zeroStockQtyStat.rate)}%），成功拣出 ${zeroStockQtySuccess} 条。
- 说明账面库存不能直接当作实物库存，快照新鲜度才是更强的信号。
`

fs.writeFileSync(path.join(OUT_DIR, 'report.md'), report)

console.log(`rows raw=${rawRows.length} unique=${rows.length} duplicates=${duplicateGroups.length}`)
console.log(`stockouts=${overall.stockouts} reported=${overall.reported} rate=${fmt(overall.rate, 2)}% blanks=${metrics.dataQuality.blanks}`)
for (const bucket of lagBuckets) {
  console.log(`${bucket.label}: n=${bucket.picks} stockouts=${bucket.stockouts} rate=${fmt(bucket.rate)}%`)
}
for (const warehouse of warehouses) {
  console.log(`${warehouse.warehouse}: fresh ${warehouse.fresh.picks}/${fmt(warehouse.fresh.rate)}% | stale ${warehouse.stale.picks}/${fmt(warehouse.stale.rate)}%`)
}
console.log(`gate: blocked=${chosen.blocked} caught=${chosen.caught} precision=${fmt(chosen.precision)}% net=${chosen.netMin}min`)
console.log('wrote analysis/metrics.json and analysis/report.md')
