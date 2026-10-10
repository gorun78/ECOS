import { expect, test } from '@playwright/test';
import { ApiResponse, call, loginAsAdmin } from '../helpers/ecos';

/**
 * PRD-09 确定性预测闭环 —— 15 端点 live REST 契约规格（`--project=api`，只需 gateway :8080）。
 *
 * 【交付口径（与 workbench-e2e.spec.ts 同纪律，交付非验收）】
 * 本文件是**契约源码交付**，非"已验收通过"凭证。断言只落到
 * `ApiResponse<T>` 信封 + `body.code` + 关键路由字段（六要素 header / denied / routingTo / preview 指标键），
 * 具体断言一律**绝不臆造数字**（生产侧金额/回测数值由 cognitive-engine 计算后经 data-engine
 * 写回 `ecos_dw`，workspace 是"只编排不生产"在线薄层如实按 GA-59-056 承载）。
 *
 * 【承载层事实（忠实源，不臆造）】
 * - `FcCaliberController`：#1/#2/#6 读侧委托 ontology、#3/#4/#5 写侧 denied hint
 *   （R-60② workspace 不建第二套口径源）。
 * - `FcForecastRunController`：#8~#16，`FcRunState` in-memory（ConcurrentHashMap），
 *   **无持久化**（V215~V218 已落脚本未实跑库，重启后状态清零 => 六演练不能跨 run 走"金标准对账"实证，
 *   只能验" viven 契约 ready"，见 R-63-2 诚实登记）。
 * - `FcBacktestAndPeriodController`：`POST /backtests/preview` 是引擎侧 `FcBacktestCalc`
 *   dry-run 委托，指标 5 项结构（mape / intervalCoverage / reviewRequired / reviewRole / sampleFlag）。
 * - `FcActionExtController`：#17 FC-04 五必填缺一 400 不静默。
 *
 * 【依赖】gateway :8080 起 + `cognitive:true` profile（workspace-service 打包在 gateway fat-JAR 里）。
 * 凭据仅从 `ecos-tests/.env` 环境变量读取（禁写入用例，见 AGENTS.md `ecos-tests` 条）。
 *
 * 【断言焦点】
 *   A. 六端点三滤波器 / 路由存在性 + `body.code===0` + `data` 非空 map。
 *   B. 400/409 拒改路径 HTTP 4xx（`BusinessException` → `@ResponseStatus(BAD_REQUEST)` 但 code 显式 400/409 承载
 *      "语义拒绝"）—— 断言 HTTP 为 400 且 `body.code` 携带 400/409，二者一致（禁 200+code:503 伪装）。
 *   C. 六要素 header 完整性：`data.six` 6 键（forecastRunId / caliberId / caliberVersion /
 *      asOfTime / snapshotId / formulaVersion）一次不落。
 *   D. Idempotency：同 runKey 再发 → `reused===true` + `reusedFromRunId===firstRunId`。
 *   E. 409 C210 终态不可动：`retry` on SUCCEEDED run → HTTP 400 + body.code 409（拒绝语义双重一致）。
 */

const CALIBERS = '/api/v1/workspace/calibers';
const RUNS = '/api/v1/workspace/forecast-runs';
const BACKTESTS = '/api/v1/workspace/backtests';
const PERIODS = '/api/v1/workspace/periods';

let token = '';
test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

// ─── A. Caliber roster（R-60②：仅 #7 实装，#1/#2 委托 ontology，#3/#4/#5/#6 write-denied hint）───

test('forecast-loop C-1: listCaliber / getCaliber —— 读侧委托 hint（delegated → ontology）', async ({ request }) => {
  const l = await call(request, 'GET', CALIBERS, { token });
  expect(l.status, `listCalibers HTTP=${l.status}`).toBe(200);
  expect(l.json?.code).toBe(0);
  expect(l.json?.data?.op).toBe('listCalibers');
  expect(String(l.json?.data?.routingTo ?? '')).toContain('/api/v1/ontology/calibers');
  expect(String(l.json?.data?.layer ?? '')).toMatch(/workspace-scene|R-60/);

  const g = await call(request, 'GET', `${CALIBERS}/cal-e2e-1`, { token });
  expect(g.status).toBe(200);
  expect(g.json?.code).toBe(0);
  expect(g.json?.data?.op).toBe('getCaliber');
  expect(String(g.json?.data?.routingTo ?? '')).toContain('/api/v1/ontology/calibers');
});

test('forecast-loop C-2: createCaliber / submitCaliber / approveCaliber / supersedeCaliber —— 写侧 denied（R-60②）', async ({ request }) => {
  for (const [op, path, method] of [
    ['createCaliber', `${CALIBERS}`, 'POST'],
    ['submitCaliber', `${CALIBERS}/cal-e2e-1/submit`, 'POST'],
    ['approveCaliber', `${CALIBERS}/cal-e2e-1/approve`, 'POST'],
    ['supersedeCaliber', `${CALIBERS}/cal-e2e-1/supersede`, 'POST'],
  ] as const) {
    const r = await call(request, method, path, { token, data: op === 'supersedeCaliber' ? { change_reason: 'e2e' } : {} });
    expect(r.status, `${op} HTTP=${r.status} 应 200（denied 走 ApiResponse.success 提示，非 reject）`).toBe(200);
    expect(r.json?.code, `${op} code=${r.json?.code}`).toBe(0);
    expect(r.json?.data?.op).toBe(op);
    expect(r.json?.data?.denied, `${op} 应 denied=true`).toBe(true);
    expect(String(r.json?.data?.routingTo ?? '')).toContain('/api/v1/ontology/calibers');
  }
});

// CaliberDsl.validate 是 live 实装（唯一非 hint 的 15 端点中 #7）。
test('forecast-loop C-3: validateCaliber —— live 4 关静态校验（V1 语法 / V2 引用 / V3 量纲 / V4 无环）', async ({ request }) => {
  // Case 1: formula 空/缺失 → ok=false（不 400，此端点契约：返回结构化 body）
  const empty = await call(request, 'POST', `${CALIBERS}/validate`, { token, data: { formula: '' } });
  expect(empty.status).toBe(200);
  expect(empty.json?.code).toBe(0);
  expect(empty.json?.data?.ok).toBe(false);
  expect(Array.isArray(empty.json?.data?.violations)).toBe(true);
  expect((empty.json?.data?.violations ?? []).length).toBeGreaterThan(0);

  // Case 2: 一个合法公式与已知符号集 → ok=true（V1/V2 双方通过；V3/V4 无元数据空跳）
  const ok = await call(request, 'POST', `${CALIBERS}/validate`, {
    token,
    data: { formula: 'a + b', knownSymbols: ['a', 'b'] },
  });
  expect(ok.status).toBe(200);
  expect(ok.json?.code).toBe(0);
  expect(ok.json?.data?.ok, `Case2 violations=${JSON.stringify(ok.json?.data?.violations)}`).toBe(true);
  expect((ok.json?.data?.violations ?? []).length).toBe(0);

  // Case 3: 未声明符号 → ok=false + 引用闭合 V2 fail
  const v2 = await call(request, 'POST', `${CALIBERS}/validate`, {
    token,
    data: { formula: 'a + ghost_sym', knownSymbols: ['a'] },
  });
  expect(v2.status).toBe(200);
  expect(v2.json?.code).toBe(0);
  expect(v2.json?.data?.ok).toBe(false);
  expect((v2.json?.data?.violations ?? []).length).toBeGreaterThan(0);
});

// ─── B. Forecast run 主链（#8~#16）：B1 建 → B2 六要素齐全 → B3 idempotency → B4 结果下钻 / evidence → B5 409 拒改 ───

interface RunView {
  runId: string;
  status: string;
  reused: boolean;
  reusedFromRunId: string | null;
  scopeHash: string;
  overridesHash: string;
  runKey: string;
  six: Record<string, string>;
  cellCount: number;
}

const SIX_KEYS = ['forecastRunId', 'caliberId', 'caliberVersion', 'asOfTime', 'snapshotId', 'formulaVersion'] as const;

function assertSixEnvelope(data: any, label: string) {
  expect(data, `${label}: data 缺失`).toBeTruthy();
  for (const k of SIX_KEYS) {
    expect(data.six?.[k], `${label}: six.${k} 缺失 or 空串`).toBeTruthy();
  }
}

let firstRunId = '';

test('forecast-loop F-1: createForecastRun 六要素入参完整 + 首跑 reused=false', async ({ request }) => {
  const r = await call(request, 'POST', RUNS, {
    token,
    data: {
      caliberId: 'cal-e2e-rev-a',
      caliberVersion: 'v1',
      formulaVersion: 'f1',
      asOfTime: '2026-10-05T12:00:00Z',
      snapshotId: 'snap-e2e-01',
      scope: { projectId: 'P1', period: '2026-09', stage: 'SD', departmentId: 'D1' },
      overrides: {},
      force: false,
    },
  });
  expect(r.status, `create HTTP=${r.status}`).toBe(200);
  expect(r.json?.code).toBe(0);
  const d = r.json?.data as RunView;
  expect(d.runId).toBeTruthy();
  expect(d.status).toBe('SUCCEEDED'); // 语义层：coordinator 状态直接落 SUCCEEDED（生产链路另由 cognitive + data 回写）
  expect(d.reused).toBe(false);
  assertSixEnvelope(d, 'F-1');
  expect(d.cellCount).toBe(0); // 生产侧 cellRefs 未回填（in-memory 层空数组）
  firstRunId = d.runId;
});

test('forecast-loop F-2: 缺六要素 → 400 且 code 显式 400（禁 200 吞 503 伪装）', async ({ request }) => {
  const missing = await call(request, 'POST', RUNS, {
    token,
    data: {
      // 故意漏 caliberVersion
      caliberId: 'cal-e2e-rev-a',
      formulaVersion: 'f1',
      asOfTime: '2026-10-05T12:00:00Z',
      snapshotId: 'snap-e2e-02',
      scope: { projectId: 'P1' },
      overrides: {},
      force: false,
    },
  });
  expect(missing.status, `missing-six HTTP=${missing.status} 应 400`).toBe(400);
  expect(missing.json?.code, `missing-six code=${missing.json?.code} 应 400`).toBe(400);
  expect(String(missing.json?.message ?? '')).toMatch(/六要素|caliberVersion|caliberId|asOfTime|snapshotId|formulaVersion/);

  // 空 scope → 另一条 400 分支
  const noScope = await call(request, 'POST', RUNS, {
    token,
    data: {
      caliberId: 'c', caliberVersion: 'v1', formulaVersion: 'f1',
      asOfTime: 't', snapshotId: 's', scope: {}, overrides: {}, force: false,
    },
  });
  expect(noScope.status).toBe(400);
  expect(noScope.json?.code).toBe(400);
});

test('forecast-loop F-3: getForecastRun 六要素摘要 + idempotr 命中 → reused=true', async ({ request }) => {
  const g = await call(request, 'GET', `${RUNS}/${firstRunId}`, { token });
  expect(g.status).toBe(200);
  expect(g.json?.code).toBe(0);
  assertSixEnvelope(g.json?.data, 'F-3.get');
  expect(g.json?.data?.status).toBe('SUCCEEDED');

  // 同 runKey 再发 → 幂等命中 SUCCEEDED → reused=true + reusedFromRunId 指向首 run
  const dup = await call(request, 'POST', RUNS, {
    token,
    data: {
      caliberId: 'cal-e2e-rev-a',
      caliberVersion: 'v1',
      formulaVersion: 'f1',
      asOfTime: '2026-10-05T12:00:00Z',
      snapshotId: 'snap-e2e-01',
      scope: { projectId: 'P1', period: '2026-09', stage: 'SD', departmentId: 'D1' },
      overrides: {},
      force: false,
    },
  });
  expect(dup.status).toBe(200);
  expect(dup.json?.code).toBe(0);
  const d = dup.json?.data as RunView;
  expect(d.reused, '同 runKey 再发应命中 idempotent 复用').toBe(true);
  expect(d.reusedFromRunId, 'reusedFromRunId 应指向首 run').toBe(firstRunId);
});

test('forecast-loop F-4: results / evidence / audit-pack 三端点 GR (= live 契约存在 + 六要素随行)', async ({ request }) => {
  const r = await call(request, 'GET', `${RUNS}/${firstRunId}/results`, { token });
  expect(r.status).toBe(200);
  expect(r.json?.code).toBe(0);
  expect(r.json?.data?.runId).toBe(firstRunId);
  expect(r.json?.data?.routingNote).toBeTruthy();
  expect(Array.isArray(r.json?.data?.cellRefs)).toBe(true);

  const e = await call(request, 'GET', `${RUNS}/${firstRunId}/evidence?detailId=d-1`, { token });
  expect(e.status).toBe(200);
  expect(e.json?.code).toBe(0);
  expect(e.json?.data?.detailId).toBe('d-1');
  expect(e.json?.data?.sourceTypeRouter).toMatch(/ACTUAL|PLAN|PROFILE_IMPUTED|COMPUTED/);

  const a = await call(request, 'GET', `${RUNS}/${firstRunId}/audit-pack`, { token });
  expect(a.status).toBe(200);
  expect(a.json?.code).toBe(0);
  expect(a.json?.data?.auditChannel).toMatch(/kafka|ecos\.audit/);
});

test('forecast-loop F-5: retry on terminal SUCCEEDED → 409 语义拒改（C210 应用层红线）', async ({ request }) => {
  const r = await call(request, 'POST', `${RUNS}/${firstRunId}/retry`, { token });
  expect(r.status, `retry terminal HTTP=${r.status} 应 400（BusinessException 固定 400）`).toBe(400);
  expect(r.json?.code, `retry terminal code=${r.json?.code} 应 409（业务语义 409）`).toBe(409);
  expect(String(r.json?.message ?? '')).toMatch(/C210|terminal|immutab/i);
});

let scenarioRunId = '';

test('forecast-loop F-6: scenario-copy 派生新 run + compare 两态都存在', async ({ request }) => {
  const s = await call(request, 'POST', `${RUNS}/${firstRunId}/scenario-copy`, {
    token,
    data: { region: 'cn-east', tier: 'premium' },
  });
  expect(s.status).toBe(200);
  expect(s.json?.code).toBe(0);
  const d = s.json?.data as RunView & { baselineRunId?: string };
  expect(d.runId).toBeTruthy();
  expect(d.runId).not.toBe(firstRunId);
  expect(d.baselineRunId, 'scenario-copy 应指回基线 run').toBe(firstRunId);
  scenarioRunId = d.runId;

  const c = await call(request, 'GET', `${RUNS}/compare?baselineRunId=${firstRunId}&scenarioRunId=${scenarioRunId}`, { token });
  expect(c.status).toBe(200);
  expect(c.json?.code).toBe(0);
  expect(c.json?.data?.baseline?.forecastRunId).toBe(firstRunId);
  expect(c.json?.data?.scenario?.forecastRunId).toBe(scenarioRunId);
});

test('forecast-loop F-7: createFcAction 五必填缺一 → 400 且 code=400（C206 护栏）', async ({ request }) => {
  // 缺 kpiText
  const bad = await call(request, 'POST', `${RUNS}/${firstRunId}/actions`, {
    token,
    data: {
      actionDesc: 'e2e action',
      ownerId: 'u1',
      dueDate: '2026-10-20',
      expectedImpact: '100',
      // kpiText: 缺
    },
  });
  expect(bad.status).toBe(400);
  expect(bad.json?.code).toBe(400);
  expect(String(bad.json?.message ?? '')).toMatch(/kpi_text/);

  // 全齐 → 200 envelope（写侧 delegated hint，非本层落库）
  const good = await call(request, 'POST', `${RUNS}/${firstRunId}/actions`, {
    token,
    data: {
      actionDesc: 'e2e action', ownerId: 'u1', dueDate: '2026-10-20',
      expectedImpact: '100', kpiText: 'KPI-x',
    },
  });
  expect(good.status).toBe(200);
  expect(good.json?.code).toBe(0);
  expect(good.json?.data?.runId).toBe(firstRunId);
  expect(good.json?.data?.status).toBe('DRAFT');
});

// ─── C. Backtest preview 五指标引擎侧 dry-run 委托（离线可测，数字由 FcBacktestCalc 承）───

function sampleRow(f: number, a: number, p10: number, p90: number) {
  return { forecast: String(f), actual: String(a), p10: String(p10), p90: String(p90) };
}

test('forecast-loop BT-1: /backtests/preview 结构（5 指标 + sampleFlag + reviewRole，禁臆造数字断言）', async ({ request }) => {
  const r = await call(request, 'POST', `${BACKTESTS}/preview`, {
    token,
    data: {
      period: '2026-09', granularity: 'month',
      projectId: 'P1', departmentId: 'D1', stage: 'SD',
      mapeThreshold: '15', coverageThreshold: '80', minSample: 5,
      samples: [
        sampleRow(100, 102, 90, 110),
        sampleRow(200, 210, 180, 230),
        sampleRow(300, 290, 280, 320),
        sampleRow(400, 405, 380, 420),
        sampleRow(500, 495, 480, 520),
      ],
    },
  });
  expect(r.status, `preview HTTP=${r.status} 应 200`).toBe(200);
  expect(r.json?.code).toBe(0);
  // 5 指标 + 6 元数据键齐全，不臆造 BigDecimal 数值
  for (const k of ['mae', 'mape', 'biasDirection', 'intervalCoverage', 'dataCoverage',
                    'zeroActualCount', 'sampleCount', 'sampleFlag', 'reviewRequired', 'reviewRole', 'reviewReason']) {
    // reviewReason 允许 null（stringify 后是 undefined），其余非空
    const v = (r.json?.data as any)?.[k];
    if (k !== 'reviewReason' && k !== 'reviewRole') {
      expect(v, `preview 缺 ${k}`).toBeTruthy();
    }
  }
  expect(r.json?.data?.sampleCount).toBe(5);
});

test('forecast-loop BT-2: /backtests/preview 门槛三键任一缺 → 400（R-66① 禁默认）', async ({ request }) => {
  const r = await call(request, 'POST', `${BACKTESTS}/preview`, {
    token,
    // 缺 coverageThreshold
    data: {
      period: '2026-09', mapeThreshold: '15', minSample: 5,
      samples: [sampleRow(1, 1, 1, 1)],
    },
  });
  expect(r.status).toBe(400);
  expect(r.json?.code).toBe(400);
  expect(String(r.json?.message ?? '')).toMatch(/thresholds|coverageThreshold|mapeThreshold|minSample/);

  // empty samples → 400
  const r2 = await call(request, 'POST', `${BACKTESTS}/preview`, {
    token,
    data: { mapeThreshold: '15', coverageThreshold: '80', minSample: 5, samples: [] },
  });
  expect(r2.status).toBe(400);
  expect(r2.json?.code).toBe(400);
});

test('forecast-loop BT-3: /backtests 列表（routing hint 三通道：data-engine read + 五指标徽标）', async ({ request }) => {
  const r = await call(request, 'GET', `${BACKTESTS}?period=2026-09&granularity=month`, { token });
  expect(r.status).toBe(200);
  expect(r.json?.code).toBe(0);
  expect(String(r.json?.data?.routingTo ?? '')).toMatch(/data-engine|datanet/);
  expect(Array.isArray(r.json?.data?.fiveMetrics)).toBe(true);
  expect(r.json?.data?.fiveMetrics).toEqual(expect.arrayContaining(['mae', 'mape', 'intervalCoverage']));
});

test('forecast-loop BT-4: /periods/{yyyMM}/close → 期间关账 routing hint（禁本层回灌，§3.4）', async ({ request }) => {
  const r = await call(request, 'POST', `${PERIODS}/2026-09/close`, { token });
  expect(r.status).toBe(200);
  expect(r.json?.code).toBe(0);
  expect(r.json?.data?.periodStatus).toBe('CLOSED');
  expect(String(r.json?.data?.routingTo ?? '')).toMatch(/data-engine|periods/);
  expect(String(r.json?.data?.rejectReason ?? '')).toMatch(/唯一回灌|data-engine/i);
});

// ─── D. 隐式 6-要素 header 一致性：跨端点校验同一 run 的六要素不漂移（byte-identical 语义在线验证）───

test('forecast-loop X-1: 六要素随行部分不漂移（同 runId 3 端点回给 100% 同一个 six map）', async ({ request }) => {
  const g = await call(request, 'GET', `${RUNS}/${firstRunId}`, { token });
  const e = await call(request, 'GET', `${RUNS}/${firstRunId}/evidence?detailId=d-9`, { token });
  const a = await call(request, 'GET', `${RUNS}/${firstRunId}/audit-pack`, { token });
  const s1 = JSON.stringify(g.json?.data?.six);
  const s2 = JSON.stringify(e.json?.data?.six);
  const s3 = JSON.stringify(a.json?.data?.six);
  expect(s1, 'evidence.six 与 get.six 应 byte-identical').toBe(s2);
  expect(s1, 'audit-pack.six 与 get.six 应 byte-identical').toBe(s3);
});

// ─── E. 2026-10-05 验证闭环补测（校订三）：补齐既有 15 端点中未被首版 spec 触达的契约面 ───
// 说明：本段新增 F-8 / C-4 / BT-5 三测，仅落"契约存在 + 路由/语义信封"层面，
// 与首版同一纪律 —— 不臆造生产数字，非 200 一律诚实枚举合法集。

test('forecast-loop F-8: exportForecastRun 走 SEC-01 同管道（本层禁本地拼 CSV，双通道委派）', async ({ request }) => {
  // 合法 runId：200 code=0 + routingNote 明示导出走 security-engine 三通道（RLS/CLS/脱敏）
  const ok = await call(request, 'GET', `${RUNS}/${firstRunId}/export`, { token });
  expect(ok.status, `export HTTP=${ok.status} 应 200`).toBe(200);
  expect(ok.json?.code).toBe(0);
  expect(ok.json?.data?.runId).toBe(firstRunId);
  expect(ok.json?.data?.format, '未传 format 时默认 csv').toBe('csv');
  expect(String(ok.json?.data?.routingNote ?? ''), '导出应走 SEC-01 security-engine 三通道')
    .toMatch(/SEC-01|security-engine|RLS|CLS/i);

  // 显式 format 透传
  const xlsx = await call(request, 'GET', `${RUNS}/${firstRunId}/export?format=xlsx`, { token });
  expect(xlsx.status).toBe(200);
  expect(xlsx.json?.data?.format).toBe('xlsx');

  // 不存在 runId：state.require 抛 IllegalStateException → GlobalExceptionHandler 固定 400（禁 5xx）
  const bogus = await call(request, 'GET', `${RUNS}/nonexistent-run-e2e/export`, { token });
  expect(bogus.status, `export(不存在 run) HTTP=${bogus.status} 应 400 业务拒，非 5xx`).toBe(400);
});

test('forecast-loop C-4: getCaliber 是纯路由委派 hint（id 无关 → 证明 workspace 不建第二套口径源 R-60②）', async ({ request }) => {
  // 同一 getCaliber 对"任意 id"都回同构 ontology 委派 hint（不本地查库、不 404）
  const realId = await call(request, 'GET', `${CALIBERS}/cal-e2e-1`, { token });
  const bogusId = await call(request, 'GET', `${CALIBERS}/cal-does-not-exist-xyz-42`, { token });
  expect(realId.status).toBe(200);
  expect(bogusId.status, `getCaliber(不存在 id) 应仍 200 hint（不 404 探库）=${bogusId.status}`).toBe(200);
  expect(bogusId.json?.code).toBe(0);
  expect(bogusId.json?.data?.op).toBe('getCaliber');
  expect(String(bogusId.json?.data?.routingTo ?? '')).toContain('/api/v1/ontology/calibers');
  // 两个 id 的路由目标完全一致 → 证明本层无真实口径数据面（R-60② 单源 ontology）
  expect(bogusId.json?.data?.routingTo).toBe(realId.json?.data?.routingTo);
});

test('forecast-loop BT-5: /backtests 列表承载 LOW_SAMPLE 契约徽标（n<5 → sampleFlag 声明 + granularity 回显）', async ({ request }) => {
  const r = await call(request, 'GET', `${BACKTESTS}?period=2026-09&granularity=month`, { token });
  expect(r.status).toBe(200);
  expect(r.json?.code).toBe(0);
  // granularity 参数回显（契约可追溯）
  expect(r.json?.data?.granularity).toBe('month');
  expect(r.json?.data?.period).toBe('2026-09');
  // LOW_SAMPLE 徽标契约声明存在（n<5 → sampleFlag=LOW_SAMPLE，源自 cognitive FcBacktestCalc）
  expect(String(r.json?.data?.lowSampleFlag ?? ''))
    .toMatch(/LOW_SAMPLE|n<5|minSample/i);
  // 五指标键齐全（与 BT-1 preview 同口径）
  expect(r.json?.data?.fiveMetrics).toEqual(
    expect.arrayContaining(['mae', 'mape', 'biasDirection', 'intervalCoverage', 'dataCoverage'])
  );
});
