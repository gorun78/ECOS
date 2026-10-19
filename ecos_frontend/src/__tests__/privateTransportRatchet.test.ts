/**
 * privateTransportRatchet.test.ts — C2′ 扩面判据「私设传输棘轮门」(#143/B5-11a)
 *
 * 判据（C2 原判据的扩面，订立依据见差距分析台账 §13.2.4 C2′ 注）：
 *   生产文件（排除 `__tests__` / `*.test.*` / `*.spec.*` / `test/` / `*.d.ts`
 *   与单源正主 `services/httpClient.ts`）内不得出现任何**私设传输**——
 *   a) 裸 `fetch(`（不含 `await` 前缀的 `return fetch(` 同样计入）；
 *   b) `new WebSocket(`；
 *   c) `XMLHttpRequest` / `axios`（2026-10-19 实测全仓 0 命中，判据留位防复发）。
 *   传输层唯一有权封装的是 `services/httpClient.ts`（Bearer 拼接、解包、traceId
 *   取号、401 会话副作用、网络/权限广播均在其内单源）。
 *   **匿名腿不算豁免**（#143/B5-11d）：本门收的是「传输封装散落」，不是「所有请求都必须带身份」。
 *   域文件为维持匿名语义而留裸 `fetch` 会在判据上出现洞，httpClient 遂提供具名匿名通道
 *   `rawAnonUrlFetch` —— 显式点名调用使「这次不带身份」成为全仓可 grep 的决定，
 *   对「带身份 / 匿名」两种腿判据只有一个：裸传输不得出现在 httpClient 之外。
 *
 * 与 C2 原判据的差别：C2 只查「私设 `await fetch(` ∩ 手写 `data ??` 解包」，
 * 已于 #143/B5-10c-1/2/3 三子批 0/0 收口；C2′ 把面扩到**任何**私设传输，
 * 因此覆盖面 = 裸 fetch（含非 await 形态）+ 私设 WebSocket。
 *
 * 棘轮口径 = **文件级基线 + 每文件命中行上限**（两者都只减不增）：
 *   新增文件即红；基线内文件命中行增加亦红（防「文件不变、私设通道膨胀」）。
 *   基线内项若在本次扫描中 0 命中同样判红（陈旧豁免项 = 未来回归的藏身处），
 *   收口一个文件必须同批把该行从 `BASELINE` 剪掉。
 *   注：命中数按**行**计（一行两处 fetch 记 1），与 `api-base-single-source`
 *   的「文件级白名单」相比更严，故两者不可互相替代。
 *
 * SSE/流式四腿（`pages/aiworkbench/api.ts`、`pages/knowledge/services/govern.ts`、
 * `services/agentLoopStream.ts`、`services/wagentRunStream.ts`）在此计入基线——
 * 它们的帧序已由 #143/B5-2~B5-5b 收口至单源，但传输腿仍是裸 fetch，
 * 归本门渐进收敛（不因「另有 SSE 门」而豁免，否则判据出现洞）。
 *
 * 页面域三腿（#143/B5-11g）的合流口径：三处原本写 `fetch(url, { headers: authHeaders() })`，
 * 而 `authHeaders()` 含 `Content-Type: application/json`、`rawAuthedUrlFetch` 的单源底头
 * **只有 Authorization**（该通道刻意不设 Content-Type，交由调用方按腿决定）。
 * 直接替换会让这批 GET 丢掉 `Content-Type`——`Guardrails/helpers#apiCall` 另有 POST/PUT 腿，
 * 丢掉即有 415 风险。故本批三腿统一传 `{ headers: { 'Content-Type': 'application/json' } }`，
 * 头集合逐键不变，只把传输封装收进单源。
 *
 * 基线数字为 2026-10-19 #143/B5-11c（alertsApi+AlertPanel → worldModelGraphApi+dataAssetsApi
 * → kbFetch+agentMeshApi 三批收口后）实跑值，非估算。首批订立时实测 26 文件 / 45 处。
 * #143/B5-11d 剪除 `services/knowledgeQueryApi.ts`(5) + `pages/knowledge/services/extract.ts`(3)
 * ⇒ 21/33 → 19 文件 / 25 命中行。
 * #143/B5-11e 剪除 `services/caseLibraryApi.ts`(4) + `services/copilotApi.ts`(2)
 * + `services/datanetMetadataApi.ts`(2) 三条**匿名腿**（走新增具名通道 `rawAnonUrlFetch`，
 * 不因「本来就没身份」而豁免）⇒ 19/25 → **16 文件 / 17 命中行**（同批实跑校验，非推算）。
 * #143/B5-11f 剪除 `services/systemDictApi.ts`(1) + `services/ontology/ontologyExportApi.ts`(1)
 * 两条带 Bearer 腿（合流 `rawAuthedUrlFetch`）⇒ 16/17 → **14 文件 / 15 命中行**
 * （同批实跑校验：改前先跑一次本门读出实测值 14/15，再剪基线，非推算）。
 * #143/B5-11g 剪除 `pages/EngineMonitor.tsx`(1) + `pages/Guardrails/helpers.ts`(1)
 * + `pages/MonitoringCenter.tsx`(1) 三条页面域 helper 腿（合流 `rawAuthedUrlFetch`
 * 并显式补回 `Content-Type`，见下「页面域三腿」注）⇒ 14/15 → **11 文件 / 12 命中行**
 * （同批实跑校验：改前先跑一次本门读出实测值 11/12，再剪基线，非推算）。
 * #143/B5-11h 剪除 `pages/knowledge/tabs/SyncTab.tsx`(1) +
 * `pages/data-workbench/tabs/connections/InlineSqlConsole.tsx`(1) +
 * `pages/aiworkbench/AgentStudioView.tsx`(1) 三条腿（合流 `rawAuthedUrlFetch`，
 * 头集合逐键不变：SyncTab 原本只发 Authorization ⇒ 不补 `Content-Type`，
 * 另两条原本经 `authHeaders()`/显式字面量发 `Content-Type` ⇒ 逐腿补回）
 * + `services/engineProbeApi.ts`(2) ⇒ 11/12 → **7 文件 / 7 命中行**
 * （同批实跑校验：改前先跑一次本门读出实测值 7/7，再剪基线，非推算）。
 *
 * `services/engineProbeApi.ts` 的处置 = **下线，不是收口**（判据口径要写清，
 * 否则后来者会以为那两条腿被合流了）：全仓取证（ripgrep，排除 node_modules/target）
 * 显示除本基线自身外零消费者，文件头声称的「自 EngineMonitor / MonitoringCenter /
 * DataAssetsDashboard 迁入」从未接线，且两个导出与页面现行 helper 已经漂移——
 * `monitoringCenterFetch` 返回 `T | null`，而 MonitoringCenter 用的是
 * `{kind:'ok'|'denied'|'error'|'offline'}` 四态对象，形态不兼容 ⇒ 既不能当作
 * 单源复用，也不该继续占 2 枚豁免位（基线预算用来保护死代码是最坏用法）。
 * 该「迁入未接线 + 形态漂移」缺陷已登记台账，属 #143/B5-11h。
 */
// @vitest-environment node
import { describe, it, expect, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));

/** 单源正主：唯一有权做传输封装。 */
const CANONICAL = 'services/httpClient.ts';

/**
 * 基线（存量登记，#143/B5-11h 收口 SyncTab + InlineSqlConsole + AgentStudioView
 * 三腿并下线零消费者的 engineProbeApi 后实测 7 文件 / 7 命中行；只减不增）。
 * 值 = 该文件允许的最大命中行数，剪项/降数必须与该文件的真实收口同批。
 */
const BASELINE: Readonly<Record<string, number>> = Object.freeze({
  'pages/aiworkbench/api.ts': 1,
  'pages/data-workbench/api/unstructured.ts': 1,
  'pages/knowledge/services/govern.ts': 1,
  'services/agentLoopStream.ts': 1,
  'services/capabilityClient.ts': 1,
  'services/scenarioActionClient.ts': 1,
  'services/wagentRunStream.ts': 1,
});

/** 私设传输判据：裸 fetch / 私设 WebSocket / XHR / axios。 */
const PRIVATE_TRANSPORT_RE = /(?:[^A-Za-z0-9_.$-]fetch\s*\(|new\s+WebSocket\s*\(|XMLHttpRequest|\baxios\b)/;

function walk(dir: string, out: string[]): void {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (entry === 'node_modules' || entry.startsWith('.')) continue;
    const st = statSync(full);
    if (st.isDirectory()) walk(full, out);
    else if ((entry.endsWith('.ts') || entry.endsWith('.tsx')) && !entry.endsWith('.d.ts')) out.push(full);
  }
}

function isTestFile(rel: string): boolean {
  return (
    rel.includes('__tests__') ||
    rel.endsWith('.test.ts') || rel.endsWith('.test.tsx') ||
    rel.endsWith('.spec.ts') || rel.endsWith('.spec.tsx') ||
    rel.startsWith('test/')
  );
}

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

/** 一文件内私设传输命中行（注释行不计，与 api-base 门同口径）。 */
function hitsOf(code: string): number[] {
  return code
    .split('\n')
    .map((line, i) => ({ line, no: i + 1 }))
    .filter(({ line }) => !isComment(line) && PRIVATE_TRANSPORT_RE.test(line))
    .map(({ no }) => no);
}

function scan(srcRoot: string): Map<string, number[]> {
  const files: string[] = [];
  walk(srcRoot, files);
  const hits = new Map<string, number[]>();
  for (const f of files) {
    const rel = relative(srcRoot, f).split(sep).join('/');
    if (rel === CANONICAL || isTestFile(rel)) continue;
    const found = hitsOf(readFileSync(f, 'utf8'));
    if (found.length > 0) hits.set(rel, found);
  }
  return hits;
}

describe('privateTransportRatchet — C2′ 私设传输棘轮门 (#143/B5-11a)', () => {
  it('noPrivateTransportOutsideHttpClient: 基线外新增即红，基线内命中行增加亦红', () => {
    const hits = scan(SRC_ROOT);
    const failures: string[] = [];

    for (const [rel, lines] of hits) {
      const allowed = BASELINE[rel];
      if (allowed === undefined) {
        failures.push(`[基线外新文件] ${rel}:${lines[0]}（共 ${lines.length} 行 ${lines.join(',')}）— 应收口到 services/httpClient.ts 单源`);
      } else if (lines.length > allowed) {
        failures.push(`[基线内增行] ${rel} 命中 ${lines.length} 行 > 基线 ${allowed}（${lines.join(',')}）— 只减不增`);
      }
    }

    for (const rel of Object.keys(BASELINE)) {
      if (!hits.has(rel)) failures.push(`[陈旧豁免] ${rel} 已 0 命中，必须同批从 BASELINE 剪除（留着 = 未来回归的豁免位）`);
    }

    expect(
      failures,
      failures.length ? `私设传输棘轮变红（合计 ${hits.size} 文件 / ${[...hits.values()].reduce((n, h) => n + h.length, 0)} 命中行）：\n${failures.join('\n')}` : undefined,
    ).toEqual([]);
  }, 30_000);

  it('判据能变红：DEFECT 源命中、CLEAN 源不命中（防恒 0 假绿）', () => {
    const DEFECT = [
      "const r = await fetch('/api/v1/x');",
      'return fetch(url, { ...options, headers });',
      'this.sock = new WebSocket(`${WS_URL}/ws`);',
      "const xhr = new XMLHttpRequest();",
    ].join('\n');
    const CLEAN = [
      "import { API_BASE, apiFetchEnvelope } from './httpClient';",
      'const ALERTS = `${API_BASE}/alerts`;',
      'await apiFetchEnvelope(`${ALERTS}/rules`);',
      '// 历史注释：原裸 fetch(\'/api/v1/x\') 已合流单源',
      'const fetchCount = metrics.fetchRate;',
    ].join('\n');

    expect(hitsOf(DEFECT)).toEqual([1, 2, 3, 4]);
    expect(hitsOf(CLEAN)).toEqual([]);
  }, 30_000);

  it('基线自身可校验：条目数与合计命中上限与文档口径一致', () => {
    const entries = Object.entries(BASELINE);
    expect(entries.length).toBe(7);
    expect(entries.reduce((n, [, c]) => n + c, 0)).toBe(7);
    // 键必须是 POSIX 相对路径且非基线外文件形态
    for (const [rel] of entries) {
      expect(rel.startsWith('./')).toBe(false);
      expect(rel.includes('\\')).toBe(false);
      expect(isTestFile(rel)).toBe(false);
      expect(rel).not.toBe(CANONICAL);
    }
  }, 30_000);
});
