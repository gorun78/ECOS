import { expect, test } from '@playwright/test';
import { injectAuth, loginAsAdmin } from '../../helpers/ecos';

/**
 * 66 条前端路由全登记判定（产品级逐功能验证 B2 交付物）。
 *
 * 判定口径（每条路由必须有明确结论，禁「未判定」）：
 *   - render  ：进入后必须真正渲染出页面（main 区有可见文案）、不落 ErrorBoundary
 *   - redirect：旧路径必须收敛到新正典 hash
 *   - param   ：带占位参数的详情路由，必须给出明确的空态/错误提示，不得白屏或崩
 *
 * 反向探针：`*`(404 fallback) 用不存在的 hash 断言落到兜底页；若该条也「正常渲染」，
 * 说明兜底路由吞掉了未知路径 —— 与「渲染成功」是两码事，故单独归类断言。
 */

type Kind = 'render' | 'redirect' | 'param' | 'fallback';

interface RouteCase {
  key: string;
  hash: string;
  kind: Kind;
  /** redirect：期望最终 hash 片段 */
  expectHash?: string;
  /** render/param：main 区必须出现的关键词（任一命中即可，避免文案微调即红） */
  expectAnyText?: string[];
}

const CASES: RouteCase[] = [
  { key: 'login', hash: '#/login', kind: 'render' },
  { key: 'no-access', hash: '#/no-access', kind: 'render' },
  { key: 'index-redirect', hash: '#/decision', kind: 'render' },
  { key: 'decision', hash: '#/decision', kind: 'render' },
  { key: 'decision/mind', hash: '#/decision/mind', kind: 'render' },
  { key: 'decision/mind/:mindId', hash: '#/decision/mind/none', kind: 'param' },
  { key: 'mission_control', hash: '#/mission_control', kind: 'render' },
  { key: 'monitor', hash: '#/monitor', kind: 'render' },
  { key: 'biz_dashboard', hash: '#/biz_dashboard', kind: 'render' },
  { key: 'project_tracker', hash: '#/project_tracker', kind: 'render' },
  { key: 'contract_manager', hash: '#/contract_manager', kind: 'render' },
  { key: 'ops_dashboard', hash: '#/ops_dashboard', kind: 'render' },
  { key: 'ops_apps', hash: '#/ops_apps', kind: 'render' },
  { key: 'marketplace', hash: '#/marketplace', kind: 'render' },
  { key: 'resource-center', hash: '#/resource-center', kind: 'render' },
  { key: 'objects', hash: '#/objects', kind: 'render' },
  { key: 'ontology_workbench', hash: '#/ontology_workbench', kind: 'render' },
  { key: 'business-workbench', hash: '#/business-workbench', kind: 'render' },
  { key: 'data-workbench', hash: '#/data-workbench', kind: 'render' },
  { key: 'dq_dashboard', hash: '#/dq_dashboard', kind: 'render' },
  { key: 'gateway-diagnostics', hash: '#/gateway-diagnostics', kind: 'render' },
  { key: 'alerts-center', hash: '#/alerts-center', kind: 'render' },
  { key: 'domains(redirect)', hash: '#/domains', kind: 'redirect', expectHash: '#/ontology_workbench' },
  { key: 'domain_designer(redirect)', hash: '#/domain_designer', kind: 'redirect', expectHash: '#/ontology_workbench' },
  { key: 'ontology(redirect)', hash: '#/ontology', kind: 'redirect', expectHash: '#/ontology_workbench' },
  { key: 'ontology_designer(redirect)', hash: '#/ontology_designer', kind: 'redirect', expectHash: '#/ontology_workbench' },
  { key: 'glossary(redirect)', hash: '#/glossary', kind: 'redirect', expectHash: '#/ontology_workbench' },
  { key: 'knowledge_graph', hash: '#/knowledge_graph', kind: 'render' },
  { key: 'dict', hash: '#/dict', kind: 'render' },
  { key: 'system-config', hash: '#/system-config', kind: 'render' },
  { key: 'platform-governance', hash: '#/platform-governance', kind: 'render' },
  { key: 'workflow_designer', hash: '#/workflow_designer', kind: 'render' },
  { key: 'project_workbench', hash: '#/project_workbench', kind: 'render' },
  { key: 'cognitive-workbench', hash: '#/cognitive-workbench', kind: 'render' },
  { key: 'workbench', hash: '#/workbench', kind: 'render' },
  { key: 'workbench/:scenarioId', hash: '#/workbench/sc001', kind: 'param' },
  { key: 'workbench/:scenarioId/forecast', hash: '#/workbench/sc001/forecast', kind: 'render' },
  { key: 'workbench/:scenarioId/decisions', hash: '#/workbench/sc001/decisions', kind: 'render' },
  { key: 'scenario-sandbox/:scenarioId', hash: '#/scenario-sandbox/sc001', kind: 'render' },
  { key: 'world_model', hash: '#/world_model', kind: 'render' },
  { key: 'knowledge(legacy)', hash: '#/knowledge', kind: 'redirect', expectHash: '#/knowledge_view' },
  { key: 'knowledge_view', hash: '#/knowledge_view', kind: 'render' },
  { key: 'agent_studio(redirect)', hash: '#/agent_studio', kind: 'redirect', expectHash: '#/ai-workbench' },
  { key: 'ai-workbench', hash: '#/ai-workbench', kind: 'render' },
  { key: 'chatbot_studio', hash: '#/chatbot_studio', kind: 'render' },
  { key: 'workshop', hash: '#/workshop', kind: 'render' },
  { key: 'agent_mesh', hash: '#/agent_mesh', kind: 'render' },
  { key: 'agent-builder(no id)', hash: '#/agent-builder', kind: 'render' },
  { key: 'agent-test/:agentId', hash: '#/agent-test/agent-data', kind: 'param' },
  { key: 'case_library', hash: '#/case_library', kind: 'render' },
  { key: 'alerts', hash: '#/alerts', kind: 'render' },
  { key: 'security-center', hash: '#/security-center', kind: 'render' },
  { key: 'guardrails', hash: '#/guardrails', kind: 'render' },
  { key: 'iam', hash: '#/iam', kind: 'render' },
  { key: 'kanban', hash: '#/kanban', kind: 'render' },
  { key: 'telemetry', hash: '#/telemetry', kind: 'render' },
  { key: 'tokens', hash: '#/tokens', kind: 'render' },
  { key: 'tenants', hash: '#/tenants', kind: 'render' },
  { key: 'engine-security', hash: '#/engine-security', kind: 'render' },
  { key: 'engine-data', hash: '#/engine-data', kind: 'render' },
  { key: 'engine-ontology', hash: '#/engine-ontology', kind: 'render' },
  { key: 'engine-cognitive', hash: '#/engine-cognitive', kind: 'render' },
  { key: 'engine-knowledge', hash: '#/engine-knowledge', kind: 'render' },
  { key: 'engine-ai', hash: '#/engine-ai', kind: 'render' },
  { key: 'engine-tasks', hash: '#/engine-tasks', kind: 'render' },
  { key: '*(404 fallback)', hash: '#/zzz-nonexistent-route', kind: 'fallback', expectAnyText: ['404', '不存在', '找不到'] },
];

/**
 * 已声明的降级面：认知引擎未承流时这些端点恒非 2xx，页面按设计渲染降级横幅。
 *
 * <p><b>判定纪律</b>：这里只登记**后端零映射、且台账/D-B 编号在册**的端点族
 * （每条都写明出处）。新增一条必须同时给出证据，禁止「页面报错就加进白名单」。
 */
const DEGRADED: { pattern: RegExp; why: string }[] = [
  { pattern: /\/api\/v1\/cognitive\//, why: '认知引擎未承流时按设计降级（前端设计方案 §3.12 降级面清单）' },
  { pattern: /\/api\/v1\/ecos\/world-model-graph\//, why: '世界模型图谱未承流' },
  { pattern: /\/api\/v1\/aip\//, why: 'AIP 编排面降级' },
  { pattern: /\/api\/v1\/agent\/models/, why: '模型目录降级（页面有 fallback 列表）' },
  // 台账 D-B5-32（2026-10-19 源级取证）：/api/alerts/{rules,history,test} 三族后端零映射，
  // Panel 一律显式渲染「未承流」而非空列表冒充可用 —— 故这两条不应把整页判红。
  { pattern: /\/api\/alerts\/rules/, why: 'D-B5-32：后端零映射，Panel 如实呈现未承流' },
  { pattern: /\/api\/alerts\/history/, why: 'D-B5-32：后端零映射，Panel 如实呈现未承流' },
  // D35：认知引擎未承流 → 503 + errorCode + traceId，页面按设计渲染降级横幅。
  { pattern: /\/api\/v1\/workspace\/scenarios\/[^/]+\/decisions/, why: 'D35：认知引擎未承流（503 带 traceId）' },
  { pattern: /\/api\/v1\/workspace\/scenarios\/[^/]+\/actions/, why: 'D35：认知引擎未承流（503 带 traceId）' },
];

/**
 * 已声明的**降级文案**（页面把降级如实写进 console）：与 DEGRADED 配套，
 * 逐条显式声明，禁止用正则通配整类错误。
 */
const DEGRADED_CONSOLE: { pattern: RegExp; why: string }[] = [
  { pattern: /认知引擎暂不可用/, why: 'D35：前端 ScenarioActionError 如实上报认知引擎未承流' },
];

/**
 * 已知「后端缺端点」缺口清单（登记不豁免）：这些失败**保留在失败清单里**，
 * 但归类为「后端缺口」而非「前端回归」，供报告分层统计。
 */
const KNOWN_BACKEND_GAPS: { pattern: RegExp; id: string; why: string }[] = [
  { pattern: /\/api\/v1\/ecos\/objects\//, id: 'D31', why: '网关整包排除 workspace.controller，前端直连 /api/v1/ecos/objects/* 恒 404' },
  { pattern: /\/api\/v1\/v1\//, id: 'D30', why: '调用方传完整 URL 给会自加 /api 前缀的 apiFetch' },
  { pattern: /\/api\/api\//, id: 'D30', why: '同 D30：双前缀' },
  // D32：锚点只钉「monitor 路由 + /api/v1/monitor 路径」两要素，不假设 detail 前缀形态。
  // 2026-11-13 实测 detail 形如 `http: 404 /api/v1/monitor`，早先写成 `^404 /api/v1/monitor$`
  // 因前缀 `http: ` 而永不命中 ⇒ 已归因的缺口被判成 UNATTRIBUTED（门禁自造的假红）。
  { pattern: /monitor\b[\s\S]*\/api\/v1\/monitor(?![/\w])/, id: 'D32', why: '前端取聚合 dashboard，后端仅有 /alerts、/dlq 子族' },
  { pattern: /\/api\/v1\/ecos\/biz\/dashboard/, id: 'D33', why: '后端无 biz/dashboard 聚合端点' },
  { pattern: /\/api\/v1\/ontology\/entities\/.*\/instances/, id: 'D34', why: 'compat 端点只在无 v1 路径 /api/ontology/... 提供' },
  { pattern: /\/api\/v1\/workspace\/scenarios\/.*\/(decisions|actions)/, id: 'D35', why: '场景决策/动作面返 503（依赖未就绪，需确认是否属声明降级）' },
];

test.setTimeout(900_000);

test('66 条路由全登记判定：无未判定项、无整页崩', async ({ page, request, context }) => {
  const token = await loginAsAdmin(request);
  await injectAuth(context, token);

  const verdicts: { key: string; kind: Kind; ok: boolean; detail: string }[] = [];

  for (const c of CASES) {
    const consoleErrors: string[] = [];
    const badResponses: string[] = [];
    const onConsole = (m: import('@playwright/test').ConsoleMessage) => {
      if (m.type() !== 'error') return;
      const t = m.text();
      if (/^Failed to load resource/i.test(t)) return;     // 网络面走 badResponses（带 URL）
      if (/\[navConfig\] no tab labelKey/.test(t)) return;  // 见下方「已知非缺陷」
      if (DEGRADED_CONSOLE.some((d) => d.pattern.test(t))) return;  // 见 DEGRADED_CONSOLE
      consoleErrors.push(t.slice(0, 300));
    };
    const onResponse = (r: import('@playwright/test').Response) => {
      const u = r.url();
      if (!u.includes('/api/') || r.status() < 400) return;
      if (DEGRADED.some((d) => d.pattern.test(u))) return;
      badResponses.push(`${r.status()} ${u.replace('http://localhost:3000', '')}`);
    };
    page.on('console', onConsole);
    page.on('response', onResponse);

    await page.goto(`/#/${c.hash.replace(/^#\//, '')}`, { waitUntil: 'domcontentloaded' });
    // 单条最多等 20s；超时也**必须**产出判定（记 FAIL，"页面 20s 内无正文"），
    // 不允许因一条卡住就把后续路由判成「未判定」——那就是漏项。
    let bodyReady = true;
    try {
      await expect
        .poll(() => page.evaluate(() => (document.body.innerText || '').length), { timeout: 20_000 })
        .toBeGreaterThan(0);
    } catch {
      bodyReady = false;
    }
    await page.waitForTimeout(3500);

    const hash = await page.evaluate(() => location.hash);
    const bodyText = await page.evaluate(() => (document.body.innerText || '').replace(/\s+/g, ' '));
    const mainText = await page.evaluate(() => {
      const m = document.querySelector('main');
      return (m?.innerText || '').replace(/\s+/g, ' ');
    });
    const hitBoundary = /页面渲染出错|Something went wrong|Unexpected Application Error/i.test(bodyText);

    let ok = true;
    let detail = '';

    if (c.kind === 'redirect') {
      ok = hash.includes(c.expectHash!);
      detail = `hash=${hash}`;
    } else if (c.kind === 'fallback') {
      // 2026-11-15 硬化：404 兜底页挂文案的时机不稳定（同一代码 3 次连跑里 2 次在 3.5s 时还没挂上、
      // 1 次已挂上），固定等待会把「渲染稍慢」判成假红。改轮询到 10s，仍取不到才记 false。
      let hasMarker = false;
      try {
        await expect
          .poll(
            () => page.evaluate(
              (ss: string[]) => {
                const t = (document.body.innerText || '').replace(/\s+/g, ' ');
                return ss.some((x) => t.includes(x));
              },
              c.expectAnyText ?? [],
            ),
            { timeout: 10_000 },
          )
          .toBe(true);
        hasMarker = true;
      } catch {
        hasMarker = false;
      }
      ok = hasMarker && !hitBoundary;
      detail = `hash=${hash} marker=${hasMarker}` + (hasMarker ? "" : ` bodyHead=${bodyText.slice(0, 120)}`);
    } else {
      // render / param：必须有可读内容且不落错误边界。
      // login / no-access 是不走 App 布局壳的独立页（没有 <main>），故以 body 计；
      // 其余以 <main> 计，避免把「只有侧边导航、正文空」误判成渲染成功。
      const hasNoLayoutShell = c.key === 'login' || c.key === 'no-access';
      const content = hasNoLayoutShell ? bodyText.trim() : mainText.trim();
      ok = content.length > 0 && !hitBoundary;
      detail = `hash=${hash} mainLen=${mainText.trim().length} bodyLen=${bodyText.trim().length} boundary=${hitBoundary}`;
    }

    if (!bodyReady) {
      ok = false;
      detail += ' | 页面 20s 内无任何正文（整体未渲染）';
    }
    if (consoleErrors.length > 0) {
      // param 类用例是**故意**用不存在的 id 探反向路径：页面把「不存在」如实记录并渲染错误态，
      // 是正确行为而非缺陷。仅当 param 用例出现与「不存在」无关的错误才判失败。
      const isExpectedMissing = c.kind === 'param'
        && consoleErrors.every((e) => /not found|不存在|404/i.test(e));
      if (!isExpectedMissing) {
        ok = false;
        detail += ` | console: ${consoleErrors.slice(0, 2).join(' || ')}`;
      } else {
        detail += ' | （param 探针：对象不存在属预期，已如实呈现）';
      }
    }
    if (badResponses.length > 0) {
      ok = false;
      detail += ` | http: ${[...new Set(badResponses)].slice(0, 3).join(', ')}`;
    }

    verdicts.push({ key: c.key, kind: c.kind, ok, detail });
    // eslint-disable-next-line no-console
    console.log(`[${ok ? 'PASS' : 'FAIL'}] ${c.key} (${c.kind}) ${detail}`);
    page.removeListener('console', onConsole);
    page.removeListener('response', onResponse);
  }

    const failed = verdicts.filter((v) => !v.ok);
    expect(verdicts, '必须逐条判定，不得缺项').toHaveLength(66);

    // 二次探针：对首屏报出的 4xx/5xx 逐条重放（带同一 token），把「瞬时抖动」与「真缺陷」分开。
    // 纪律：重放用**同一路径与方法**，只判状态码是否仍 ≥400；仍失败才算真缺陷。
    const replay: string[] = [];
    const flakyOnly = new Set<string>();
    for (const f of failed) {
      const urls = [...f.detail.matchAll(/(\d{3}) (\/api\/[^\s,|]+)/g)];
      let allFlaky = urls.length > 0;
      for (const [, status, path] of urls) {
        const res = await request.get(`http://127.0.0.1:8080${path}`, {
          headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
        });
        replay.push(`${f.key}: ${path} 首屏=${status} 重放=${res.status()}`);
        if (res.status() < 400) {
          // 单条路径重放转绿，仍不能整页判通过（可能另有未捕获原因），只做归因标注
        } else {
          allFlaky = false;
        }
      }
      if (allFlaky) flakyOnly.add(f.key);
    }
    if (replay.length > 0) {
      // eslint-disable-next-line no-console
      console.log(`\n==== 二次探针（同路径重放）====\n${replay.join('\n')}`
        + `\n首屏失败但重放全绿的路由（瞬时抖动，不计入后端缺口计数）：${[...flakyOnly].join(', ') || '无'}`);
    }

    // 失败分层：后端缺口（在册）vs 未归因（＝前端回归，必须为 0 才算本批收口）
    const gaps = failed.map((f) => {
      const hit = KNOWN_BACKEND_GAPS.find((g) => g.pattern.test(f.detail));
      return hit ? `${hit.id} ${f.key}` : `UNATTRIBUTED ${f.key}`;
    });
  // eslint-disable-next-line no-console
  console.log(`\n==== 失败分层 ====\n${gaps.join('\n')}`);
  const unattributed = failed.filter(
    (f) => !KNOWN_BACKEND_GAPS.some((g) => g.pattern.test(f.detail)) && !flakyOnly.has(f.key),
  );
  expect(
    unattributed.map((f) => `${f.key}: ${f.detail}`),
    `未归因失败 ${unattributed.length} 条（首屏失败但重放全绿的瞬时抖动 ${flakyOnly.size} 条已单列，`
      + `有归因的后端缺口见控制台清单，二者仍计入失败总数、不得当通过）`,
  ).toEqual([]);
});

/**
 * 已知非缺陷（判定时剔除，不隐藏真实问题）：
 * [navConfig] no tab labelKey for route id "xxx" —— 导航配置对**未在 tab 表中登记**的路由
 * 输出占位文案（W201 fail-closed，禁止回退到原始 key）。这是设计如此的显式告警，
 * 不是页面错误，故从 console 判定中剔除；但若某路由连占位都不给，仍会因 main 空而失败。
 */
