import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * B2·认知引擎 — 端点面补测（矩阵域02 的「本批未排入」33 条，2026-11-13 第二轮）。
 *
 * **本轮实测得到的四条判据纪律**（写进断言，防止后人误判）：
 *
 * 1. **501 不是缺陷**：`/api/v1/cognitive/world-model/{relations,rules,traces}` 三面
 *    实测 501 `ECOS-COG-503` + `data.stub=true` + `reason/owner/segment/missing`。
 *    这是详细设计-05 **F05-16 显式 stub 化**的裁定结果，`missing` 字段还写明了
 *    「ecos_wm_causal_link 只连 goal↔goal，非契约关系集；V194 裁本域外因果口径以
 *    ontology REST 只读为准」。故本文件断言「501 必须带完整 stub 元数据」——
 *    而不是断言「必须 200」。
 * 2. **404 与 405 语义不同**：`/cognitive/executions`、`/outcomes/{id}/review-tasks`、
 *    `/replay` 实测 **405**（路径存在、方法不对），说明端点已注册但动词不同；
 *    `/cognitive/assets`、`/plans`、`/demo/wave3`、`/world-model` 实测 **404 ECOS-SYS-404**
 *    （路由未注册）。两者必须分开记，不能都写「不可用」。
 * 3. **400 必须点名缺什么**：`operation-eval` 未带参数时返回 `data.missing="mind"`——
 *    这是合格形态。本文件断言 400 必带 `missing` 或 `message` 说明。
 * 4. **越权/无效场景必须 404 且有 guard 标记**：`NOT_EXIST_999` 四端点实测
 *    404 `msg="scenario not found"` + `data.guard`，不得裸 500、不得泄露内部信息。
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

test.describe('V-COG-010 / 011 / 012 认知健康三面', () => {
  test('V-COG-010 引擎健康：200 + status=UP + components 含 engine/db', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/engine/cognitive/health');
    expect(status, 'cognitive health 应 200').toBe(200);
    const d = json!.data;
    expect(String(d.status).toUpperCase(), 'status 必须为 UP').toBe('UP');
    expect(d.components, '必须报 components').toBeTruthy();
    expect(Object.keys(d.components), 'components 至少含 engine 与 db')
      .toEqual(expect.arrayContaining(['engine', 'db']));
  });

  test('V-COG-011 / 012 状态与内部健康前缀实测形态（404 登记，非静默通过）', async ({ request }) => {
    // 矩阵原写「GET /api/v1/engine/cognitive」与「GET /api/v1/cognitive」均可达；
    // 实测两者都是 404 ECOS-SYS-404（路由未注册）。钉住现状，实现后本腿会红。
    for (const p of ['/api/v1/engine/cognitive', '/api/v1/cognitive']) {
      const { status, json } = await get(request, p);
      expect(status, `${p} 当前实测 404（路由未注册）；若已实现请改判 V-COG-011/012`)
        .toBe(404);
      expect(json?.errorCode, `${p} 应为网关 ECOS-SYS-404`).toBe('ECOS-SYS-404');
    }
  });
});

test.describe('V-COG-021 / 022 / 024 认知 E2/E3 + 越权', () => {
  test('V-COG-021 operation-eval 缺参必 400 并点名缺什么（实测 missing=mind）', async ({ request }) => {
    const { status, json } = await get(request,
      '/api/v1/business/scenarios/sc001/cognition/operation-eval');
    expect(status, '缺必填参数应 400').toBe(400);
    const missing = json?.data?.missing ?? json?.message;
    expect(missing, '400 必须点明缺哪个参数（实测 data.missing="mind"）').toBeTruthy();
    expect(String(missing).toLowerCase()).toContain('mind');
  });

  test('V-COG-022 hypotheses 返回数组（状态词表须∈HypothesisStatus）', async ({ request }) => {
    const { status, json } = await get(request,
      '/api/v1/business/scenarios/sc001/cognition/hypotheses');
    expect(status, 'hypotheses 应 200').toBe(200);
    const rows = json!.data as any[];
    expect(Array.isArray(rows), 'hypotheses 应为数组').toBe(true);
    // 有行时必须校验状态词表（P1-6 冲突项：出现 VALIDATED 即为越界）
    const allowed = ['PROPOSED', 'TESTING', 'SUPPORTED', 'REFUTED', 'RETIRED',
      'PENDING', 'CONFIRMED', 'REJECTED'];
    for (const r of rows) {
      const s = String(r.status ?? '').toUpperCase();
      if (s) {
        expect(allowed, `hypothesis 状态 ${s} 不在 HypothesisStatus 词表内（P1-6）`)
          .toContain(s);
      }
    }
  });

  test('V-COG-024 无效场景必须 404 + guard 标记，不裸 500、不泄露', async ({ request }) => {
    for (const p of ['detect', 'hypotheses', 'beliefs']) {
      const { status, json } = await get(request,
        `/api/v1/business/scenarios/NOT_EXIST_999/cognition/${p}`);
      expect(NOT_5XX, `${p} 不得 5xx`).not.toContain(status);
      expect(status, `${p} 无效场景应 404，实得 ${status}`).toBe(404);
      const body = JSON.stringify(json);
      expect(body, `${p} 响应不得泄露表名/堆栈/SQL`)
        .not.toMatch(/SELECT |FROM |at com\.chinacreator|ecos_cognitive\./i);
      // 实测带 data.guard 标记（可区分「场景不存在」与「引擎不可用」）
      expect(json?.data?.guard ?? json?.errorCode,
        `${p} 必须带可辨标记（guard 或 errorCode）`).toBeTruthy();
    }
  });
});

test.describe('V-COG-091 世界模型三面：501 是设计内 stub（F05-16）', () => {
  for (const seg of ['relations', 'rules', 'traces']) {
    test(`world-model/${seg} 返回 501 且带完整 stub 元数据`, async ({ request }) => {
      const { status, json } = await get(request, `/api/v1/cognitive/world-model/${seg}`);
      expect(status, `world-model/${seg} 实测 501（F05-16 显式 stub），若已实现请改判 V-COG-091`)
        .toBe(501);
      expect(json?.errorCode, 'stub 必须带机读码 ECOS-COG-503').toBe('ECOS-COG-503');
      const d = json?.data ?? {};
      expect(d.stub, 'stub 标记必须为 true（与真实故障区分）').toBe(true);
      for (const k of ['reason', 'owner', 'segment', 'path', 'missing']) {
        expect(d, `stub 必须自述 ${k}（否则调用方无法判断该找谁）`).toHaveProperty(k);
      }
      expect(d.segment, 'segment 必须回显被请求的段').toBe(seg);
      expect(String(d.reason), 'reason 必须指向裁定出处').toMatch(/F05-16|stub/);
    });
  }

  test('world-model 独立前缀 `/api/v1/world-model` 路由未注册（404 登记）', async ({ request }) => {
    for (const p of ['/api/v1/world-model', '/api/v1/world-model/tree']) {
      const { status, json } = await get(request, p);
      expect(status, `${p} 当前实测 404；若已实现请改判 V-COG-092`).toBe(404);
      expect(json?.errorCode).toBe('ECOS-SYS-404');
    }
  });
});

test.describe('V-COG-032 / 045 / 036 Mind 与计划', () => {
  test('V-COG-032 提交提案：mind 不存在必须 400（不得 500）', async ({ request }) => {
    const { status, json } = await post(request, '/api/v1/cognitive/minds/x/proposals');
    expect(NOT_5XX, 'mind 缺失不得 5xx').not.toContain(status);
    expect(status, 'mind 不存在应 400').toBe(400);
    expect(String(json?.message ?? ''), '400 必须说明 mind not found')
      .toMatch(/mind/i);
  });

  test('V-COG-045 计划校验：非法计划被拒且报出 issues，不 500', async ({ request }) => {
    const { status, json } = await post(request, '/api/v1/cognitive/plans/validate', { plan: null });
    expect(NOT_5XX, '计划校验不得 5xx').not.toContain(status);
    expect(status, '非法计划应 200（带校验结论）或 400，实得 ' + status).toBeLessThan(500);
    if (status === 200) {
      const d = json!.data;
      expect(typeof d.passed, '校验结论必须含布尔 passed').toBe('boolean');
      expect(typeof d.dagAcyclic, '必须报 DAG 无环判定').toBe('boolean');
      expect(Array.isArray(d.issues), 'issues 必须为数组').toBe(true);
    }
  });

  test('V-COG-042 框架列表返回真实条目（实测 4 个）', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/cognitive/frameworks');
    expect(status, 'frameworks 应 200').toBe(200);
    const rows = json!.data as any[];
    expect(Array.isArray(rows) && rows.length > 0, '框架列表必须有条目').toBeTruthy();
  });
});

test.describe('V-COG-074 / 075 / 087 / 071 契约面：不存在资源必须 404 且自述', () => {
  const cases = [
    { p: '/api/v1/cognitive/outcomes/x/backtest', id: 'V-COG-074', must: /outcome/i },
    { p: '/api/v1/cognitive/decision-basis/x', id: 'V-COG-087', must: /decision/i },
    { p: '/api/v1/cognitive/forecast-runs/x/results', id: 'V-COG-071', must: /forecast/i },
    { p: '/api/v1/cognitive/forecast-runs/x/evidence', id: 'V-COG-071', must: /forecast/i },
  ];

  for (const { p, id, must } of cases) {
    test(`${id} ${p} 不存在 id ⇒ 404 且点名资源类型`, async ({ request }) => {
      const { status, json } = await get(request, p);
      expect(NOT_5XX, `${p} 不得 5xx`).not.toContain(status);
      expect(status, `${p} 应 404`).toBe(404);
      expect(String(json?.message ?? ''), `${p} 必须点名哪种资源不存在`).toMatch(must);
    });
  }

  test('V-COG-075 review-tasks 实测 405（路径在、动词不对）', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/cognitive/outcomes/x/review-tasks');
    expect(status, 'review-tasks 当前实测 405；若改为 GET 请更新本腿与 V-COG-075')
      .toBe(405);
    expect(json?.errorCode, '405 必须带机读码').toBe('ECOS-SYS-405');
  });
});

test.describe('V-COG-103 / 104 / 094 目录与演示面：区分 405 与 404', () => {
  test('objects 目录返回真实条目（实测 11 条）', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/cognitive/objects');
    expect(status, 'cognitive/objects 应 200').toBe(200);
    expect((json!.data as any[]).length, '对象目录必须有条目').toBeGreaterThan(0);
  });

  test('executions / replay / assets-proposals 实测 405：端点已注册但动词不同', async ({ request }) => {
    const m405 = [
      { p: '/api/v1/cognitive/executions', method: 'GET' },
      { p: '/api/v1/cognitive/replay', method: 'POST' },
      { p: '/api/v1/cognitive/assets/x/proposals', method: 'GET' },
    ];
    for (const { p, method } of m405) {
      const r = method === 'GET' ? await get(request, p) : await post(request, p);
      expect(r.status, `${p} 当前实测 405（路径存在、方法不符）；若改动词请更新 V-COG-046/054/094`)
        .toBe(405);
      expect(r.json?.errorCode, `${p} 405 必须带 ECOS-SYS-405`).toBe('ECOS-SYS-405');
    }
  });

  test('assets / plans / demo-wave3 路由未注册（404 登记）', async ({ request }) => {
    for (const p of ['/api/v1/cognitive/assets', '/api/v1/cognitive/plans',
      '/api/v1/cognitive/demo/wave3']) {
      const { status, json } = await get(request, p);
      expect(status, `${p} 当前实测 404；若已实现请改判 V-COG-103/104`).toBe(404);
      expect(json?.errorCode).toBe('ECOS-SYS-404');
    }
  });
});

test.describe('V-COG-084 通用预测：缺 metric 必须 400 点名', () => {
  test('forecast 空体 ⇒ 400 + FCST-400 文案含 metric', async ({ request }) => {
    const { status, json } = await post(request, '/api/v1/cognitive/forecast', {});
    expect(NOT_5XX, 'forecast 不得 5xx').not.toContain(status);
    expect(status, '缺 metric 应 400').toBe(400);
    expect(String(json?.message ?? ''), '400 必须点名 metric').toContain('metric');
  });
});
