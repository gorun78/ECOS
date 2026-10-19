/**
 * syncSqlAgentTransport.singleSource.spec.ts — #143/B5-11h 三条页面腿合流 + engineProbeApi 下线取证
 * （判据依据：`src/__tests__/privateTransportRatchet.test.ts` 的 C2′ 扩面口径、
 * 差距分析台账 §13.2.4 C2′ 注；批次对象：#143/B5-11h）
 *
 * 本批改的三处传输腿（全部合流 `services/httpClient.ts` 的 `rawAuthedUrlFetch`）：
 *   1) `pages/knowledge/tabs/SyncTab.tsx` — 改前
 *      `await (await fetch('/api/integration/metadata', { headers: { Authorization: \`Bearer ${getAuthToken()}\` } })).json()`；
 *   2) `pages/data-workbench/tabs/connections/InlineSqlConsole.tsx` — 改前 POST
 *      `'/api/v1/engine/data/query/execute'`，头写 `{'Content-Type': ..., Authorization: ...}`；
 *   3) `pages/aiworkbench/AgentStudioView.tsx` — 改前 POST
 *      `${API_BASE}/v1/guardrails/policies/preview`，头写 `authHeaders()`。
 *
 * 「头集合逐键不变」按腿分别成立，是本批唯一的硬约束，也是三条腿写法不同的原因：
 *   · `rawAuthedUrlFetch` 的单源底头**只有 Authorization**（该通道刻意不设 Content-Type，
 *     由调用方按腿决定），内核 `rawUrlFetch` 对调用方 `headers` 做**逐键合并**；
 *   · SyncTab 改前只发 `Authorization` ⇒ 本批**不传** `headers`，合并结果仍是只发 Authorization；
 *     若顺手补 `Content-Type` 就是给一条 GET 换头集合 = 语义变更，不属本批授权；
 *   · 另两条改前经 `authHeaders()`/显式字面量发 `Content-Type: application/json` ⇒ 逐腿显式补回，
 *     否则 POST 体在网关侧有 415 风险。
 *
 * 一处已知的**非逐字**差异，如实登记而不是掩盖：SyncTab 改前写 `Bearer ${getAuthToken()}`，
 * 无登录态时会发出字面 `Authorization: Bearer null`；合流后单源在无 token 时**省略该键**。
 * 两者在网关侧都落到 `anyRequest().authenticated()` ⇒ 401，域内应答面同为
 * `catch { setAssets([]) }`（401 的 HTML/空体不是合法 JSON，`res.json()` 抛错走同一条腿），
 * 且该页面本身只在登录后才挂载 ⇒ 判定为等价，不做兼容 `Bearer null` 的额外封装。
 *
 * `services/engineProbeApi.ts` 的处置 = **下线（git rm）**，本批对它没有「合流」这回事：
 * 全仓取证（Grep，排除 node_modules/target/dist）显示除棘轮基线自身与台账文字外零消费者，
 * 文件头声称的「自 EngineMonitor / MonitoringCenter / DataAssetsDashboard 迁入」从未接线，
 * 且两个导出与页面现行 helper 已漂移（`monitoringCenterFetch` 返回 `T | null`，
 * MonitoringCenter 实际用 `{kind:'ok'|'denied'|'error'|'offline'}` 四态）⇒ 既不能复用，
 * 也不该继续占 2 枚豁免位。缺陷登记见台账 #143/B5-11h 段（迁入未接线 + 形态漂移）。
 *
 * 取证边界（写在代码里而不是留给记忆）：三处腿都位于 React 组件体内的事件/加载函数，
 * 未导出 ⇒ 本文件只做**源码形态**断言（与 #143/B5-11g 对两个页面私有 helper、
 * #143/B5-4 对 AlertPanel 的口径一致）。行为面断言需要渲染组件，属另一批工作量；
 * 本批未做 curl/live 复验，不宣布三条腿的真实应答面已验证。
 */
// @vitest-environment node
import { describe, it, expect, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC_ROOT = fileURLToPath(new URL('../../', import.meta.url));
const read = (relFromSrc: string): string => readFileSync(join(SRC_ROOT, ...relFromSrc.split('/')), 'utf8');

const SYNC_TAB = 'pages/knowledge/tabs/SyncTab.tsx';
const SQL_CONSOLE = 'pages/data-workbench/tabs/connections/InlineSqlConsole.tsx';
const AGENT_STUDIO = 'pages/aiworkbench/AgentStudioView.tsx';
const TARGETS = [SYNC_TAB, SQL_CONSOLE, AGENT_STUDIO];

const RATCHET_GATE = '__tests__/privateTransportRatchet.test.ts';
const APIBASE_GATE = '__tests__/api-base-single-source.test.ts';

/** 与棘轮门同判据（裸 fetch / WebSocket / XHR / axios），注释行不计。 */
const PRIVATE_TRANSPORT_RE = /(?:[^A-Za-z0-9_.$-]fetch\s*\(|new\s+WebSocket\s*\(|XMLHttpRequest|\baxios\b)/;
/** 与 api-base 门同判据。 */
const HARDCODED_RE = /["'`]\/api\b/;

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

/** 非注释行内命中数（按行计，与棘轮门口径一致）。 */
function countInCode(code: string, re: RegExp): number {
  return code.split('\n').filter((l) => !isComment(l) && re.test(l)).length;
}

/** 去掉注释后的源码，用于「出现次数」级别的扫描。 */
function codeOnly(code: string): string {
  return code.split('\n').filter((l) => !isComment(l)).join('\n');
}

function countAll(code: string, re: RegExp): number {
  return (codeOnly(code).match(re) ?? []).length;
}

describe('A 源码形态取证（#143/B5-11h）', () => {
  it('A1 判据自证：DEFECT 源命中、CLEAN 源不命中（防恒 0 假绿）', () => {
    const DEFECT = [
      "const raw = await (await fetch('/api/integration/metadata')).json();",
      "const res = await fetch(url, { headers: { Authorization: `Bearer ${t}` } });",
    ].join('\n');
    const CLEAN = [
      "import { API_BASE, rawAuthedUrlFetch } from '../../../services/httpClient';",
      'const res = await rawAuthedUrlFetch(`${API_BASE}/integration/metadata`);',
      '// 历史注释：原裸 fetch(\'/api/integration/metadata\') 已合流单源',
    ].join('\n');
    expect(countInCode(DEFECT, PRIVATE_TRANSPORT_RE)).toBe(2);
    expect(countInCode(CLEAN, PRIVATE_TRANSPORT_RE)).toBe(0);
  });

  it('A2 三文件零私设传输（棘轮判据）', () => {
    for (const rel of TARGETS) {
      expect(countInCode(read(rel), PRIVATE_TRANSPORT_RE), `${rel} 仍有私设传输腿`).toBe(0);
    }
  });

  it('A2b engineProbeApi 已下线：文件不存在且两份门禁不再提它', () => {
    expect(existsSync(join(SRC_ROOT, 'services', 'engineProbeApi.ts'))).toBe(false);
    for (const gate of [RATCHET_GATE, APIBASE_GATE]) {
      expect(read(gate)).not.toContain("'services/engineProbeApi.ts'");
    }
  });

  it('A3 三文件零手写 Authorization / 零 token 自拼', () => {
    for (const rel of TARGETS) {
      const code = codeOnly(read(rel));
      expect(/Authorization\s*:/.test(code), `${rel} 仍在域内拼 Authorization`).toBe(false);
      expect(/Bearer\s*\$\{/.test(code), `${rel} 仍在域内拼 Bearer`).toBe(false);
    }
  });

  it('A4 每文件恰一条单源腿；Content-Type 按改前头集合逐腿取舍', () => {
    for (const rel of TARGETS) {
      expect(countAll(read(rel), /\brawAuthedUrlFetch\s*\(/g), `${rel} 单源腿数漂移`).toBe(1);
    }
    // SyncTab：改前只发 Authorization ⇒ 不补 Content-Type（补了就是给 GET 换头集合）
    expect(countAll(read(SYNC_TAB), /Content-Type/g)).toBe(0);
    // 另两条改前经 authHeaders()/显式字面量发 Content-Type ⇒ 逐腿补回，避免 POST 415
    expect(countAll(read(SQL_CONSOLE), /'Content-Type':\s*'application\/json'/g)).toBe(1);
    expect(countAll(read(AGENT_STUDIO), /'Content-Type':\s*'application\/json'/g)).toBe(1);
  });

  it('A5 合流后不得再留 token 读取能力（防「留着 import 继续私拼」）', () => {
    for (const rel of TARGETS) {
      const code = codeOnly(read(rel));
      expect(/\bgetAuthToken\b/.test(code), `${rel} 仍引入 getAuthToken`).toBe(false);
      expect(/\bauthHeaders\b/.test(code), `${rel} 仍引入 authHeaders`).toBe(false);
    }
  });

  it('A6 基址单源：零 /api 字面量 + 三枚模板串逐字可追', () => {
    for (const rel of TARGETS) {
      expect(countInCode(read(rel), HARDCODED_RE), `${rel} 仍有 /api 字面量`).toBe(0);
    }
    // 拼出的 URL 与改前字面量一致的前提 = API_BASE 的单源取值，本批不改动它
    expect(read('services/httpClient.ts')).toMatch(/export const API_BASE = ['"]\/api['"]/);
    expect(codeOnly(read(SYNC_TAB))).toContain('`${API_BASE}/integration/metadata`');
    expect(codeOnly(read(SQL_CONSOLE))).toContain('`${API_BASE}/v1/engine/data/query/execute`');
    expect(codeOnly(read(AGENT_STUDIO))).toContain('`${API_BASE}/v1/guardrails/policies/preview`');
  });

  it('A7 两份门禁基线同批剪除本批文件，且棘轮元门禁声明 = 基线自身实数', () => {
    for (const gate of [RATCHET_GATE, APIBASE_GATE]) {
      const src = read(gate);
      expect(src).not.toContain(`'${SYNC_TAB}'`);
      expect(src).not.toContain(`'${SQL_CONSOLE}'`);
      expect(src).not.toContain(`'${AGENT_STUDIO}'`);
    }
    const ratchet = read(RATCHET_GATE);
    const declaredCount = Number(/expect\(entries\.length\)\.toBe\((\d+)\)/.exec(ratchet)?.[1] ?? -1);
    const declaredSum = Number(
      /expect\(entries\.reduce\(\(n, \[, c\]\) => n \+ c, 0\)\)\.toBe\((\d+)\)/.exec(ratchet)?.[1] ?? -1,
    );
    const entries = [...ratchet.matchAll(/^\s*'([^']+)':\s*(\d+),/gm)];
    expect(entries.length).toBe(declaredCount);
    expect(entries.reduce((n, [, , c]) => n + Number(c), 0)).toBe(declaredSum);
    // 收口到此批为止只剩流式/域内错误分类族：本批剪掉的 4 枚不得回到基线
    for (const pruned of [SYNC_TAB, SQL_CONSOLE, AGENT_STUDIO, 'services/engineProbeApi.ts']) {
      expect(ratchet).not.toContain(`'${pruned}'`);
    }
  });
});
