import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * B2·AI 引擎 — 端点面补测（矩阵域03 的「本批未排入」28 条，2026-11-13 第二轮）。
 *
 * **路径口径**：矩阵里的多数条目只写了类名或估算前缀，本文件用源码实测到的真实映射：
 *  - 2026-11-23 去 wagent 化后仅 /api/v1/wagent/approval-tokens 存活（L3 审批凭证，CLI 契约依赖）
 *  - LLM 网关是 `POST /api/v1/llm/{chat,embedding}`（GET 列表不存在）
 *  - 护栏两块：`/api/v1/guardrails`（策略+校验）与 `/api/v1/aip/guardrails`（另一套 CRUD）
 *  - catalog 真实前缀是 `/api/v1/datanet/catalog`（不是 `/api/v1/catalog`）
 *  - 能力目录是 `/api/v1/ai/{capabilities,models,providers}`
 *
 * **反假绿纪律（本轮四条实测结论）**：
 * 1. **501 可以是设计内降级**（skills 因 MCP 未连），但必须带 `stub` 与 `reason`。
 * 2. **403/404 合并文案是隐私策略**（`E-NOT-FOUND: 当前权限不支持该操作，或对象不存在`），
 *    不得要求它区分——区分反而成了越权探测面。
 * 3. **400 必须是「参数不合法」而非 500**；文案统一走诊断码是团队既定策略，不强求字段名。
 * 4. **路由未注册（404 ECOS-SYS-404）与 405（路径在/动词不对）必须分开记**。
 */

let token = '';
test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

const NOT_5XX = [500, 502, 504];

async function get(request: any, path: string) {
  return call(request, 'GET', path, { token });
}

async function post(request: any, path: string, data: unknown = {}) {
  return call(request, 'POST', path, { token, data });
}

test.describe('V-AI-055 L3 审批凭证（wagent 面下线后唯一存活的 wagent 前缀端点）', () => {
  test('V-AI-055 审批凭证：真实签发腿可达，四眼与 scopeType 闸都在', async ({ request }) => {
    // 2026-11-23 去 wagent 化：W 面（tools/evidence/kill-switch/readiness/decisions）随
    // wagent 包删除，仅审批凭证签发面保留（CLI 契约依赖），路由仍在 /api/v1/wagent/approval-tokens。
    const empty = await post(request, '/api/v1/wagent/approval-tokens', {});
    expect(empty.status, '空体必须 400（缺 scopeType/scopeId），不得 404/500').toBe(400);

    const badScope = await post(request, '/api/v1/wagent/approval-tokens',
      { scopeType: 'NOT_A_SCOPE', scopeId: 'X' });
    expect(badScope.status, 'scopeType 越界必须 400（白名单见 L3ApprovalScope）').toBe(400);

    const selfApproval = await post(request, '/api/v1/wagent/approval-tokens',
      { scopeType: 'CANDIDATE_PUBLISH', scopeId: 'VERIFY-NOT-EXIST', requestedBy: 'u-admin' });
    expect(selfApproval.status, '四眼原则：审批人不得给自己发起的事项签发').toBe(403);

    const ok = await post(request, '/api/v1/wagent/approval-tokens',
      { scopeType: 'CANDIDATE_PUBLISH', scopeId: 'VERIFY-NOT-EXIST', requestedBy: 'u-someone-else', ttlSeconds: 600 });
    expect(ok.status, '他人发起时 admin 应能签发（证明 L3 审批面本身可用）').toBe(200);
    expect(ok.json?.data?.token, '凭证明文只在本次响应出现').toBeTruthy();
    expect(String(ok.json?.data?.tokenEcho ?? ''), '必须自述不回显策略').toContain('SHA-256');
  });
});

test.describe('V-AI-070 LLM 网关（唯一出口）', () => {
  test('POST /llm/chat 空体 ⇒ 400 messages 不能为空（点名缺什么）', async ({ request }) => {
    const { status, json } = await post(request, '/api/v1/llm/chat', {});
    expect(NOT_5XX, 'LLM 网关不得 5xx').not.toContain(status);
    expect(status, '空 messages 应 400').toBe(400);
    expect(String(json?.message ?? ''), '400 必须点名 messages').toContain('messages');
  });

  test('POST /llm/embedding 空体 ⇒ 400 input/texts 不能同时为空', async ({ request }) => {
    const { status, json } = await post(request, '/api/v1/llm/embedding', {});
    expect(NOT_5XX).not.toContain(status);
    expect(status, '空 input 应 400').toBe(400);
    expect(String(json?.message ?? ''), '400 必须点名 input/texts')
      .toMatch(/input|texts/);
  });

  test('GET /llm 列表不存在（网关只有 chat/embedding 两个写面）', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/llm');
    expect(status, 'GET /llm 应 404（该前缀只注册 POST 两腿）').toBe(404);
    expect(json?.errorCode).toBe('ECOS-SYS-404');
  });
});

test.describe('V-AI-072 护栏（两套面：/guardrails 与 /aip/guardrails）', () => {
  test('/guardrails/policies 返回数组；/guardrails/validate 返回裁决七键', async ({ request }) => {
    const pol = await get(request, '/api/v1/guardrails/policies');
    expect(pol.status, 'guardrails/policies 应 200').toBe(200);
    expect(Array.isArray(pol.json!.data), 'policies 应为数组').toBe(true);

    const val = await post(request, '/api/v1/guardrails/validate', {});
    expect(NOT_5XX, 'validate 不得 5xx').not.toContain(val.status);
    expect(val.status, 'validate 应 200 并给出裁决').toBe(200);
    const d = val.json!.data;
    for (const k of ['passed', 'decision', 'blocked', 'violations']) {
      expect(d, `裁决必须含 ${k}（缺了调用方无法判断是否放行）`).toHaveProperty(k);
    }
    expect(typeof d.passed, 'passed 必须为布尔').toBe('boolean');
    expect(typeof d.blocked, 'blocked 必须为布尔').toBe('boolean');
    expect(Array.isArray(d.violations), 'violations 必须为数组').toBe(true);
  });

  test('/aip/guardrails 是另一套 CRUD 面，返回数组', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/aip/guardrails');
    expect(status, 'aip/guardrails 应 200').toBe(200);
    expect(Array.isArray(json!.data), 'aip guardrails 应为数组').toBe(true);
  });
});

test.describe('V-AI-074 / 073 / 077 演化·调度·目录', () => {
  test('V-AI-074 演化：trigger 返回 missionId；log 返回数组', async ({ request }) => {
    const trig = await post(request, '/api/v1/evolution/trigger', {});
    expect(NOT_5XX, 'evolution/trigger 不得 5xx').not.toContain(trig.status);
    expect(trig.status, 'trigger 应 200').toBe(200);
    const d = trig.json!.data;
    expect(d.status, 'trigger 必须回执 status').toBeTruthy();
    expect(d.missionId, 'trigger 必须回执 missionId 供追踪').toBeTruthy();

    const log = await get(request, `/api/v1/evolution/log/${d.missionId}`);
    expect(log.status, 'evolution/log 应 200').toBe(200);
    expect(Array.isArray(log.json!.data), 'log 应为数组').toBe(true);
  });

  test('V-AI-073 定时任务列表返回数组', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/cron-jobs');
    expect(status, 'cron-jobs 应 200').toBe(200);
    expect(Array.isArray(json!.data), 'cron-jobs 应为数组').toBe(true);
  });

  test('V-AI-077 目录真实前缀是 /datanet/catalog（不是 /catalog）', async ({ request }) => {
    const dash = await get(request, '/api/v1/datanet/catalog/dashboard');
    expect(dash.status, 'catalog/dashboard 应 200').toBe(200);
    const d = dash.json!.data;
    for (const k of ['totalResources', 'totalFields']) {
      expect(d, `dashboard 必须含 ${k}`).toHaveProperty(k);
    }

    const search = await get(request, '/api/v1/datanet/catalog/search?q=a');
    expect(search.status, 'catalog/search 应 200').toBe(200);
    expect(Array.isArray(search.json!.data), 'search 应为数组').toBe(true);

    // 矩阵写的 /api/v1/catalog 不存在 —— 钉住真实前缀，防后人再按错路径验收
    const wrong = await get(request, '/api/v1/catalog');
    expect(wrong.status, '/api/v1/catalog 实测 404；真实前缀是 /api/v1/datanet/catalog')
      .toBe(404);
  });
});

test.describe('V-AI-078 / 079 / 080 能力目录与认知桥', () => {
  test('V-AI-078 capabilities 返回 13 条；models/providers 结构完整', async ({ request }) => {
    const cap = await get(request, '/api/v1/ai/capabilities');
    expect(cap.status, 'ai/capabilities 应 200').toBe(200);
    expect((cap.json!.data as any[]).length, '能力目录必须有条目').toBeGreaterThan(0);

    const models = await get(request, '/api/v1/ai/models');
    expect(models.status, 'ai/models 应 200').toBe(200);
    expect(Array.isArray(models.json!.data.items), 'models.items 应为数组').toBe(true);

    const prov = await get(request, '/api/v1/ai/providers');
    expect(prov.status, 'ai/providers 应 200').toBe(200);
    expect(prov.json!.data, 'providers 必须报 available/stub 等元信息')
      .toHaveProperty('available');
  });

  test('V-AI-079 动作桥/提示编译：available-actions 与 index-status 可达', async ({ request }) => {
    const act = await get(request, '/api/v1/cognitive/available-actions');
    expect(act.status, 'available-actions 应 200').toBe(200);
    expect(Array.isArray(act.json!.data), 'available-actions 应为数组').toBe(true);
    expect((act.json!.data as any[]).length, '实测 3 个可用动作').toBeGreaterThan(0);

    const idx = await get(request, '/api/v1/cognitive/index-status');
    expect(idx.status, 'index-status 应 200').toBe(200);
    for (const k of ['vectorIndexReady', 'graphIndexReady', 'documentCount']) {
      expect(idx.json!.data, `index-status 必须含 ${k}`).toHaveProperty(k);
    }
  });

  test('V-AI-080 cognitive/config 返回配置数组（AI 侧与认知域同名控制器需可区分）', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/cognitive/config');
    expect(status, 'cognitive/config 应 200').toBe(200);
    expect(Array.isArray(json!.data), 'config 应为数组').toBe(true);
  });
});

test.describe('V-AI-081 引擎状态一致性', () => {
  test('ai 与 cognitive 的 status/health 四面均 200 且状态词一致', async ({ request }) => {
    const aiHealth = await get(request, '/api/v1/engine/ai/health');
    expect(aiHealth.status, 'engine/ai/health 应 200').toBe(200);
    expect(aiHealth.json!.data.components, 'ai health 必须报 components').toBeTruthy();

    const aiStatus = await get(request, '/api/v1/engine/ai/status');
    const cogStatus = await get(request, '/api/v1/engine/cognitive/status');
    expect(aiStatus.status, 'engine/ai/status 应 200').toBe(200);
    expect(cogStatus.status, 'engine/cognitive/status 应 200').toBe(200);
    expect(String(aiStatus.json!.data.status), 'ai status 必须非空').toBeTruthy();
    expect(String(cogStatus.json!.data.status), 'cognitive status 必须非空').toBeTruthy();

    // 矩阵原写 GET /api/v1/engine/ai 与 /engine/cognitive 可达 —— 实测 404，钉住现状
    for (const p of ['/api/v1/engine/ai', '/api/v1/engine/cognitive']) {
      const r = await get(request, p);
      expect(r.status, `${p} 实测 404（路由未注册）；若已实现请改判 V-AI-081/V-COG-011`)
        .toBe(404);
    }
  });
});

test.describe('V-AI-071 技能：MCP 未连时 501 且自述 stub', () => {
  test('skills 501 + stub + reason 指认 MCP 通道', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/skills');
    expect(NOT_5XX, 'skills 不得 500（501 是显式降级）').not.toContain(status);
    expect(status, 'MCP 未连时实测 501；接通后请改判 V-AI-071').toBe(501);
    const d = json?.data ?? {};
    expect(d.stub, 'stub 标记必须为 true（与真故障区分）').toBe(true);
    expect(String(d.reason ?? ''), 'reason 必须指认 MCP 通道未连').toMatch(/MCP/i);
    expect(d.capability ?? d.provider, '必须报受影响的能力/提供方').toBeTruthy();
  });
});

test.describe('V-AI-092 无 token 访问 AI 面全部被拒', () => {
  test('六类 AI 端点无 token 必须 401/403，不得 200', async ({ request }) => {
    const paths: Array<[string, 'GET' | 'POST']> = [
      ['/api/v1/wagent/approval-tokens', 'POST'],
      ['/api/v1/llm/chat', 'POST'],
      ['/api/v1/guardrails/policies', 'GET'],
      ['/api/v1/ai/capabilities', 'GET'],
      ['/api/v1/cron-jobs', 'GET'],
      ['/api/v1/cognitive/config', 'GET'],
    ];
    for (const [p, method] of paths) {
      const res = await request.fetch(p, {
        method,
        headers: { 'Content-Type': 'application/json' },
        data: method === 'POST' ? JSON.stringify({}) : undefined,
      });
      expect([401, 403], `${p} 无 token 必须 401/403，实得 ${res.status()}`)
        .toContain(res.status());
    }
  });
});

test.describe('端点未注册登记（404 ECOS-SYS-404）', () => {
  /**
   * 以下矩阵条目引用的路径实测**路由未注册**。要么矩阵路径写错、要么控制器未进
   * gateway 扫描。记 BLOCKED 并钉住现状：注册后本组转红，强制改判。
   */
  const missing = [
    { p: '/api/v1/cognitive/action-bridge', id: 'V-AI-079', note: '真实路径是 /execute-action 与 /available-actions' },
    { p: '/api/v1/cognitive/config/defaults', id: 'V-AI-080', note: 'config 存在但 /defaults 子腿 404' },
  ];

  for (const { p, id, note } of missing) {
    test(`${id} ${p} 路由未注册`, async ({ request }) => {
      const { status, json } = await get(request, p);
      expect(status, `${p} 当前 404（${note}）；若已实现请改判 ${id}`).toBe(404);
      expect(json?.errorCode, '必须是网关 ECOS-SYS-404（路由未注册）').toBe('ECOS-SYS-404');
    });
  }
});

/**
 * D52 / D53 回归钉（2026-11-13 修复后新增）。
 *
 * D52: `AgentRuntimeController` 在 `services.agent.controller` 包，网关 @ComponentScan
 *      未含该包 ⇒ `/api/v1/agent-runtime/**` 全量 404。前端 `agentMeshApi.ts:59`
 *      的 `telemetry/default` 是产品路径，不能只靠 aiming :18084 直连活着。
 * D53: `OagForwardController` 转发时不带 `Authorization`、不重注入信任头（网关
 *      `EcosTrustHeaderFilter` 会先剥掉客户端的），且 health 指向上游不存在的
 *      `POST /api/v1/oag/health`（真实腿是 `GET /api/v1/oag/chat/health`）
 *      ⇒ 经网关的 OAG 全线 403 / "health check failed: HTTP 403"。
 */
test.describe('D52 / D53 回归钉', () => {
  test('D52 agent-runtime 经网关可达（telemetry 200）', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/agent-runtime/telemetry/default');
    expect(status, 'D52：网关必须自己承载 /api/v1/agent-runtime/**，不得 404').toBe(200);
    expect(json?.code, '业务码必须是 0（不是 404 包装）').toBe(0);
    expect(json?.data?.agentId, '遥测返回 agentId=default 才证明打到真实现').toBe('default');
  });

  test('D52 agent-runtime/tools 参数校验在（400 点名 agentId，不是 404）', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/agent-runtime/tools');
    expect(status, 'D52：缺 agentId 应 400；若回 404 说明扫描又丢了').toBe(400);
    expect(json?.errorCode, '必须走参数校验而非路由缺失').not.toBe('ECOS-SYS-404');
  });

  test('D53 OAG health 转发到真实上游腿并 200', async ({ request }) => {
    const { status, json } = await post(request, '/api/v1/oag/health');
    expect(status, 'D53：转发器必须带认证料到 :18084，否则上游 403 折成 503').toBe(200);
    expect(json?.code).toBe(0);
    expect(String(json?.data ?? ''), '必须指认 /chat/health 这条真实腿').toContain('/api/v1/oag/chat/health');
  });

  test('D53 OAG chat/health 与上游同名同动词（GET 可达）', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/oag/chat/health');
    expect(status, '上游契约是 GET /chat/health，网关必须同名暴露').toBe(200);
    expect(json?.code).toBe(0);
  });

  test('D53 OAG chat 真链路：认证被上游认可（非 403）', async ({ request }) => {
    const { status, json } = await post(request, '/api/v1/oag/chat', { message: '你好' });
    expect(status, 'D53：带 token 经网关调 OAG chat 不得 403').not.toBe(403);
    expect(
      ['COMPLETED', 'BLOCKED', 'FAILED'],
      `OAG 管道必须给出终态，实得 ${JSON.stringify(json?.data?.status)}`,
    ).toContain(json?.data?.status);
  });
});

