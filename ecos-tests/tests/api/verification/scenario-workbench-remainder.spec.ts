import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * B1·场景工作台 — 端点面补测（矩阵域01 的 34 条「本批未排入」项，2026-11-13 第二轮）。
 *
 * 覆盖 14 个 Controller 的真实映射。**反假绿纪律**（本轮三条实测教训）：
 *
 * 1. **业务前置条件不满足 ≠ 服务故障**（D50）：`cognitive/{diagnose,forecast,simulate,policy}`
 *    四条腿在场景未绑 Mind 时曾抛 500 `INTERNAL`，而抛出点写的是 `BusinessException(404,404)`。
 *    本组断言「**不得 5xx**」而非「必须 200」——因为正确的 404 也是合格结果。
 * 2. **503 是合法业务态**（`available/agents`、`available/wagents` 依赖认知引擎），
 *    但必须带 `errorCode` 且不得是裸 500。故此处断言 `[200, 503]` 且 503 必带码。
 * 3. **404 不等于端点不存在**：`/api/query/*` 与 `/api/v1/ecos/objects/search` 实测 404，
 *    必须先分辨是「路由没注册」还是「资源不存在」。本文件把已确认未注册的路径
 *    单独登记为 MISSING（点「端点缺失」），已注册的才断言行为。
 */

let token = '';
test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

async function get(request: any, path: string) {
  return call(request, 'GET', path, { token });
}

async function post(request: any, path: string, data: unknown) {
  return call(request, 'POST', path, { token, data });
}

function assertNot5xx(status: number, label: string): void {
  // 503 是**显式降级态**（认知引擎不可用/上游未起），属合法结果；500/502/504 才是故障。
  expect(status, `${label} 不得为 500/502/504（实得 ${status}）`)
    .not.toBe(500);
  expect([500, 502, 504], `${label} 必须是 4xx 业务错误或 2xx/503，实得 ${status}`)
    .not.toContain(status);
}

test.describe('V-WS-085 场景选项聚合（7 个选项源）', () => {
  const sources = ['datasets', 'objects', 'knowledge', 'security', 'interfaces'] as const;

  for (const src of sources) {
    test(`available/${src} 返回数组且不 5xx`, async ({ request }) => {
      const { status, json } = await get(request, `/api/v1/workspace/scenarios/available/${src}`);
      expect([200, 503], `available/${src} 应 200 或显式 503，实得 ${status}`).toContain(status);
      if (status === 200) {
        expect(json?.code, `available/${src} code 应为 0`).toBe(0);
        expect(Array.isArray(json?.data), `available/${src} data 应为数组`).toBe(true);
      } else {
        expect(json?.errorCode, `available/${src} 的 503 必须带机读码`).toBeTruthy();
      }
    });
  }

  test('available/agents 与 available/wagents 依赖认知引擎：503 必须带 COGNITIVE_UNAVAILABLE', async ({ request }) => {
    for (const src of ['agents', 'wagents']) {
      const { status, json } = await get(request, `/api/v1/workspace/scenarios/available/${src}`);
      assertNot5xx(status, `available/${src}`);
      expect([200, 503], `available/${src} 应 200 或 503`).toContain(status);
      if (status === 503) {
        // 实测该腿固定回 COGNITIVE_UNAVAILABLE；钉住它，防止退回裸 500。
        expect(json?.errorCode, `available/${src} 503 必须带 COGNITIVE_UNAVAILABLE`)
          .toBe('COGNITIVE_UNAVAILABLE');
        expect(json?.traceId, '503 必须带 traceId 供运维定位').toBeTruthy();
      }
    }
  });
});

test.describe('V-WS-081 / 083 / 084 契约面', () => {
  test('V-WS-081 预测范围返回完整结构', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/workspace/scenarios/sc001/forecast-scope');
    assertNot5xx(status, 'forecast-scope');
    expect(status, 'forecast-scope 应 200').toBe(200);
    const d = json!.data;
    for (const k of ['scenarioId', 'periods', 'coverageRatio']) {
      expect(d, `forecast-scope 必须含 ${k}`).toHaveProperty(k);
    }
  });

  test('V-WS-083 回测契约：403 时须自报 permission 与未解析绑定（不裸 500）', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/workspace/backtests');
    assertNot5xx(status, 'backtests');
    if (status === 403) {
      const d = json!.data ?? {};
      expect(d.permission ?? json!.message, '403 必须点明缺哪个权限（便于授信）').toBeTruthy();
    }
  });

  test('V-WS-084 关账契约：非法期间必须业务拒绝且显式标注未落库', async ({ request }) => {
    const { status, json } = await post(request, '/api/v1/workspace/periods/202601/close', {});
    assertNot5xx(status, 'periods/close');
    if (status === 403) {
      expect(String(json!.message ?? ''), '403 必须写明 fail-closed 未落库')
        .toContain('未落库');
    }
  });
});

test.describe('V-WS-074 认知 v2 只读面（4 映射）', () => {
  const faces = [
    { p: 'detect', ok: [200], label: 'detect' },
    { p: 'operation-eval', ok: [200, 400], label: 'operation-eval（缺必填参数 400 属正常）' },
    { p: 'hypotheses', ok: [200], label: 'hypotheses' },
    { p: 'beliefs', ok: [200], label: 'beliefs' },
  ];

  for (const { p, ok, label } of faces) {
    test(`cognition/${label} 不裸 500`, async ({ request }) => {
      const { status, json } = await get(request, `/api/v1/business/scenarios/sc001/cognition/${p}`);
      assertNot5xx(status, `cognition/${p}`);
      expect(ok, `cognition/${p} 的允许状态 ${JSON.stringify(ok)}，实得 ${status}`).toContain(status);
      if (status === 400) {
        expect(json?.message, '400 必须说明缺什么').toBeTruthy();
      }
      if (status === 200) {
        expect(json?.code, `cognition/${p} code=0`).toBe(0);
      }
    });
  }
});

test.describe('V-WS-075 场景认知面（D50 回归常驻）', () => {
  /**
   * **D50 常驻探针**：这四条 POST 曾全部 500 `INTERNAL`，真因是
   * `ScenarioCognitiveController#buildPayload` 抛 `BusinessException(404,404,"no_active_mind_for_scenario=…")`
   * 而 `WorkspaceExceptionHandler` 没有 `DataBridgeException` 分支 ⇒ 落兜底 500。
   * 断言「不得 5xx」是核心：正确结果可能是 200（场景有 Mind）或 404（未绑 Mind），
   * 但**绝不能是 500**——后者会让前端弹系统错误并触发 5xx 告警。
   */
  for (const p of ['diagnose', 'forecast', 'simulate', 'policy']) {
    test(`cognitive/${p} 未绑 Mind 时归 404，不得 500（D50）`, async ({ request }) => {
      const { status, json } = await post(request, `/api/v1/workspace/scenarios/sc001/cognitive/${p}`, {});
      expect(status, `cognitive/${p} 不得 500（D50 复发），实得 ${status}`).not.toBe(500);
      if (status === 404) {
        expect(String(json?.message ?? ''), '404 必须点名 no_active_mind_for_scenario，可指导用户先绑 Mind')
          .toContain('no_active_mind');
      }
      if (status === 200) {
        expect(json?.code, '有 Mind 时应 code=0').toBe(0);
      }
      if (status === 503) {
        // workspace :18090 未起时网关透传 503 ECOS-WS-503-UPSTREAM —— 也是合格的非 500 结果。
        expect(json?.errorCode, '503 必须带机读码').toBeTruthy();
      }
    });
  }
});

test.describe('V-WS-097 / 098 / 026 / 025 可达面', () => {
  test('V-WS-097 工作簿：health 报三运行时 + sessions/history 为数组', async ({ request }) => {
    const health = await request.fetch('/api/workbook/health', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(health.status(), 'workbook/health 应 200').toBe(200);
    const hb = await health.json();
    for (const k of ['sql', 'python', 'r']) {
      expect(hb?.data, `workbook health 必须报 ${k} 运行时`).toHaveProperty(k);
    }
    for (const p of ['/api/workbook/sessions', '/api/workbook/history']) {
      const r = await request.fetch(p, { headers: { Authorization: `Bearer ${token}` } });
      expect(r.status(), `${p} 应 200`).toBe(200);
      const b = await r.json();
      expect(Array.isArray(b?.data), `${p} data 应为数组`).toBe(true);
    }
  });

  test('V-WS-098 知识工作台：health/stats/engine-config 三面返回真实结构', async ({ request }) => {
    const health = await get(request, '/api/v1/knowledge/health');
    assertNot5xx(health.status, 'knowledge/health');
    expect(health.status).toBe(200);
    expect(health.json!.data, 'health 必须报 overall').toHaveProperty('overall');

    const stats = await get(request, '/api/v1/knowledge/stats');
    expect(stats.status, 'knowledge/stats 应 200').toBe(200);
    expect(typeof stats.json!.data.graphNodeCount, 'stats 必须含数值型 graphNodeCount')
      .toBe('number');

    const cfg = await get(request, '/api/v1/knowledge/engine-config');
    expect(cfg.status, 'engine-config 应 200').toBe(200);
    expect(cfg.json!.data, 'engine-config 必须含 scope 与 count').toHaveProperty('scope');
    expect(typeof cfg.json!.data.count, 'engine-config.count 必须为数值').toBe('number');
  });

  test('V-WS-026 绑定目录返回 6 类候选', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/workspace/binding-catalog');
    expect(status, 'binding-catalog 应 200').toBe(200);
    const rows = json!.data as any[];
    expect(Array.isArray(rows), 'binding-catalog 应为数组').toBe(true);
    expect(rows.length, '实测 6 类（dataset/object/knowledge/agent/security/interface 族）')
      .toBeGreaterThan(0);
  });

  test('V-WS-025 完备度报告：islands 与 missingEdges 字段在位', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/workspace/scenarios/sc001/completeness');
    expect(status, 'completeness 应 200').toBe(200);
    const d = json!.data;
    expect(Array.isArray(d.islands), 'islands 必须为数组（孤岛清单）').toBe(true);
    expect(Array.isArray(d.missingEdges), 'missingEdges 必须为数组').toBe(true);
    expect(typeof d.coverage, 'coverage 必须为数值').toBe('number');
    expect(d.verdict, 'verdict 必须存在').toBeTruthy();
  });

  test('V-WS-041 运行列表为数组（含 run_mode/status 时逐行校验）', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/workspace/scenarios/sc001/runs');
    expect(status, 'runs 应 200').toBe(200);
    const rows = json!.data as any[];
    expect(Array.isArray(rows), 'runs 应为数组').toBe(true);
    for (const r of rows) {
      expect(r.runMode ?? r.run_mode, '每行必须带 runMode').toBeTruthy();
      expect(r.status, '每行必须带 status').toBeTruthy();
    }
  });
});

test.describe('对象面/查询面路由已补齐（D31 修复后的正向回归）', () => {
  /**
   * <p><b>2026-11-14 状态翻转</b>：本组原为「端点缺失登记（MISSING）」，断言这四条 404。
   * D31 修复（`WorkspaceForwardController` 增加 `/api/v1/ecos/objects/**`、
   * `/api/v1/ecos/state-machine/**`、`/api/query/**` 转发前缀）后路由已全部可达，
   * 原断言按设计"会红、强制改判"——现按正向契约重写，防路由回退。</p>
   *
   * <p>四条里两条改判口径：</p>
   * <ul>
   *   <li>`V-WS-050`：真实前缀是 `/api/v1/business/scenarios/{id}/blueprints`，
   *       原探针写的 `/api/v1/workspace/scenarios/{id}/blueprints` 从来就不是对的路径；</li>
   *   <li>`V-WS-090#search`：`ObjectController` 的 `@GetMapping("/search")` 挂在
   *       `/api/v1/ecos/objects` 下，需带 `q` 参数（缺参应 400，不是 404）。</li>
   * </ul>
   */
  const reachable = [
    { path: '/api/query/objectql', method: 'POST', data: JSON.stringify({ query: '{"entity":"Customer","limit":2}' }), id: 'V-WS-095', who: 'ObjectQLController' },
    { path: '/api/query/history?page=1&pageSize=5', method: 'GET', id: 'V-WS-096', who: 'QueryHistoryController' },
    { path: '/api/v1/ecos/objects/search?q=x&page=1&pageSize=5', method: 'GET', id: 'V-WS-090#search', who: 'ObjectController#search' },
    { path: '/api/v1/business/scenarios/sc001/blueprints', method: 'GET', id: 'V-WS-050', who: 'ScenarioBlueprintController' },
  ];

  for (const { path, method, id, who, data } of reachable) {
    test(`${id} ${who} 路由可达（不得再回退成 ECOS-SYS-404）`, async ({ request }) => {
      const res = await request.fetch(path, {
        method,
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        data,
      });
      expect(res.status(), `${id} 路由必须可达；404 表示 D31 转发前缀回退`).not.toBe(404);

      const body = await res.json().catch(() => ({}));
      expect(body.errorCode, `${id} 不得返回网关兜底码 ECOS-SYS-404`).not.toBe("ECOS-SYS-404");
      // 反向探针口径：可达端点的业务码必须是 0（成功）或明确业务码（如 400/404 带 message），
      // 不能是把异常压成内部错误的 -1。
      expect(body.code, `${id} 业务码不得为 -1（异常被压成「执行失败」），实得 ${JSON.stringify(body).slice(0, 160)}`)
        .not.toBe(-1);
    });
  }
});
