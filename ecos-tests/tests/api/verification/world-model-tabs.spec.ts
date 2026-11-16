import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * 世界模型 7 Tab 后端承流回归（D104/D105/D106 的长效防线）。
 *
 * 为什么单独建一条 spec：
 *  - D104（场景 CRUD 内存假成功）、D105（goal-tracking 经网关恒 404）、D106（Pareto 四端点零实现）
 *    三个缺陷的共同特征是「前端 Tab 看起来正常、后端其实没数据」。单测层面用源码门禁能挡住复发，
 *    但只有 HTTP 活体腿能证明「经网关这条路真能取到库里的东西」。
 *  - 本 spec 只打网关 :8080 的正典路径（与前端一致），不做直连服务断言 —— 缺陷恰恰出在承流层，
 *    直连绕过承流等于自欺。
 *
 * 反假绿纪律：
 *  - 场景列表必须来自 DB：断言「不含 default-scenario 假数据」+ 「至少一行有非空 name」；
 *  - Pareto 的 result 回取必须与 optimize 的返回**逐点一致**（快照语义），不是「两边都 200」；
 *  - 前沿必须互不支配且无重复点（去重是 D106 二次修复的内容，用返回体自检）；
 *  - 参数越界必须 400、未知 problemId 必须 404 —— 这两条是「不静默夹上限」的反向探针。
 */

let token = '';

test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

test.describe('世界模型 7 Tab 承流（D104 / D105 / D106）', () => {
  test('场景列表来自数据库且不含默认场景假数据（D104）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/ecos/world-model-graph/scenarios', { token });
    expect(status, 'scenarios HTTP 应 200').toBe(200);
    expect(json?.code, `code 应为 0（${json?.message ?? ''}）`).toBe(0);

    const rows = (json?.data?.data ?? []) as Array<{ id: string; name: string; status: string }>;
    expect(typeof json?.data?.total, 'total 应为数字').toBe('number');
    expect(json?.data?.total, 'total 应与列表长度一致').toBe(rows.length);

    for (const r of rows) {
      expect(r.id, '不得再出现 default-scenario 假数据（D104 原形态）').not.toBe('default-scenario');
      expect(r.name, '库中场景必须有名称').toBeTruthy();
    }
  });

  test('目标追踪经网关可达并返回真实容器（D105）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/dq/goal-tracking', { token });
    expect(status, 'goal-tracking HTTP 应 200（修复前恒 404）').toBe(200);
    expect(json?.code, `code 应为 0（${json?.message ?? ''}）`).toBe(0);
    expect(Array.isArray(json?.data?.tracking), 'data.tracking 应为数组').toBe(true);
    expect(typeof json?.data?.total, 'total 应为数字').toBe('number');
    expect(json?.data?.total, 'total 应与 tracking 长度一致').toBe((json?.data?.tracking ?? []).length);
  });

  test('嵌套 DQ 面板端点均可达（D105/D107）', async ({ request }) => {
    const paths = ['/api/dq/dashboard', '/api/dq/issues', '/api/dq/rules', '/api/dq/executions'];
    for (const p of paths) {
      const { status, json } = await call(request, 'GET', p, { token });
      expect(status, `${p} HTTP 应 200（修复前为 500/503）`).toBe(200);
      expect(json?.code, `${p} code 应为 0（${json?.message ?? ''}）`).toBe(0);
    }
  });

  test('Pareto 优化→历史→回取闭环，且前沿互不支配无重复（D106）', async ({ request }) => {
    const body = { numObjectives: 2, numVariables: 3, populationSize: 40, generations: 30 };
    const opt = await call(request, 'POST', '/api/v1/pareto/optimize', { token, data: body });
    expect(opt.status, 'optimize HTTP 应 200').toBe(200);
    expect(opt.json?.code, `optimize code 应为 0（${opt.json?.message ?? ''}）`).toBe(0);

    const problemId = opt.json?.data?.problemId as string;
    const sols = (opt.json?.data?.solutions ?? []) as Array<{
      variables: Record<string, number>;
      objectives: Record<string, number>;
    }>;
    expect(problemId, 'problemId 必须返回').toBeTruthy();
    expect(opt.json?.data?.frontSize, 'frontSize 应与解数一致').toBe(sols.length);
    expect(sols.length, '前沿不应为空').toBeGreaterThan(0);
    expect(typeof opt.json?.data?.elapsed_ms, 'elapsed_ms 应为数字').toBe('number');

    // 前沿性质 1：无重复变量向量（D106 二次修复点）
    const keys = sols.map((s) => JSON.stringify(s.variables));
    expect(new Set(keys).size, '前沿不得含重复解（前端会把同一点画两次）').toBe(sols.length);

    // 前沿性质 2：互不支配（最小化语义）
    const dominates = (a: number[], b: number[]): boolean => {
      let strict = false;
      for (let i = 0; i < a.length; i++) {
        if (a[i] > b[i]) return false;
        if (a[i] < b[i]) strict = true;
      }
      return strict;
    };
    const objVecs = sols.map((s) => Object.keys(s.objectives).sort().map((k) => s.objectives[k]));
    for (let i = 0; i < objVecs.length; i++) {
      for (let j = 0; j < objVecs.length; j++) {
        if (i !== j) {
          expect(dominates(objVecs[i], objVecs[j]), `前沿内 ${i} 不得支配 ${j}`).toBe(false);
        }
      }
    }

    // 历史列表应含刚提交的 problemId
    const list = await call(request, 'GET', '/api/v1/pareto/problems', { token });
    expect(list.status, 'problems HTTP 应 200').toBe(200);
    const hist = (list.json?.data?.data ?? []) as Array<{ problemId: string; frontSize: number }>;
    expect(hist.some((h) => h.problemId === problemId), '历史列表应含刚提交的 problemId').toBe(true);

    // 回取必须与提交那次逐点一致（快照语义，不重算）
    const back = await call(request, 'GET', `/api/v1/pareto/result/${problemId}`, { token });
    expect(back.status, 'result HTTP 应 200').toBe(200);
    expect(back.json?.data?.frontSize, '回取 frontSize 应与提交一致').toBe(sols.length);
    expect(
      JSON.stringify(back.json?.data?.solutions),
      '回取解集必须与提交那次逐字节一致（快照回取，不是重算）'
    ).toBe(JSON.stringify(sols));
  });

  test('Pareto 参数越界 400 / 未知 id 404（反向探针）', async ({ request }) => {
    const badBodies = [
      { numObjectives: 1, numVariables: 3, populationSize: 40, generations: 30 },
      { numObjectives: 2, numVariables: 3, populationSize: 999999, generations: 30 },
      { numObjectives: 2, numVariables: 3, populationSize: 40, generations: 9999 },
    ];
    for (const b of badBodies) {
      const { status } = await call(request, 'POST', '/api/v1/pareto/optimize', { token, data: b });
      expect(status, `越界参数必须 400（不得静默夹上限）：${JSON.stringify(b)}`).toBe(400);
    }

    const missing = await call(request, 'GET', '/api/v1/pareto/result/does-not-exist', { token });
    expect(missing.status, '未知 problemId 应 404').toBe(404);
  });
});
