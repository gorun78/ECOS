#!/usr/bin/env node
/**
 * scripts/check-i18n-parity.mjs — F9 i18n 双语平行度检查（CI 可选）。
 *
 * 读 `locales/knowledge/zh-CN.json` 与 `en.json`，对二者求差集（zh 有而 en 无 + en 有而 zh 无）。
 * 排除 `_depr.*` 前缀的元 key（F9 废弃标记，不参与业务消费，i18next 不读取）。
 * 差集非空 → exit 1，输出缺失 key 明细；差集为空 → exit 0。
 *
 * 本批次（PMO-D Batch 3）不强制启用 CI，仅建脚本 + 本地跑一次确认通过。
 *
 * 用法：node scripts/check-i18n-parity.mjs
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const localeDir = path.resolve(__dirname, '../src/locales/knowledge');
const zhPath = path.join(localeDir, 'zh-CN.json');
const enPath = path.join(localeDir, 'en.json');

/** F9 `_depr.{key}: true` 元 key 前缀 — 不参与平行度判定 */
const DEPR_PREFIX = '_depr.';

/** 解析 JSON 对象（顶层 key → value 的映射） */
function loadJson(file) {
  const raw = readFileSync(file, 'utf8');
  return JSON.parse(raw);
}

const zh = loadJson(zhPath);
const en = loadJson(enPath);

/** 过滤 `_depr.*` 元 key 后的有效 key 集合 */
function activeKeys(obj) {
  return Object.keys(obj).filter((k) => !k.startsWith(DEPR_PREFIX));
}

const zhActive = new Set(activeKeys(zh));
const enActive = new Set(activeKeys(en));

/** zh 有而 en 无 */
const zhOnly = [...zhActive].filter((k) => !enActive.has(k));
/** en 有而 zh 无 */
const enOnly = [...enActive].filter((k) => !zhActive.has(k));

const failures = [];
if (zhOnly.length > 0) {
  failures.push(`zh-CN 有而 en 无（${zhOnly.length} 个）：`);
  zhOnly.forEach((k) => failures.push(`  zh-only: ${k}`));
}
if (enOnly.length > 0) {
  failures.push(`en 有而 zh-CN 无（${enOnly.length} 个）：`);
  enOnly.forEach((k) => failures.push(`  en-only: ${k}`));
}

if (failures.length > 0) {
  console.error('[check-i18n-parity] FAIL — 双语平行度差集非空：');
  failures.forEach((line) => console.error(line));
  console.error(`[check-i18n-parity] zh 有效 key=${zhActive.size}  en 有效 key=${enActive.size}`);
  console.error('[check-i18n-parity] （_depr.* 元 key 已排除）');
  process.exit(1);
}

console.log(`[check-i18n-parity] OK — zh/en 有效 key 平行度一致（各 ${zhActive.size} 个，_depr.* 元 key 已排除）`);
process.exit(0);
