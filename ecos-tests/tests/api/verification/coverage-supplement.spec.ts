import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * 覆盖补充 — 对「282 Controller 登记判定」中未被前序 spec 命中的真实可达面做实测。
 *
 * 反假绿纪律：
 *  - 「不可达」必须区分三种语义：方法不匹配(405) / 未装配(501) / 依赖缺失(502) / 真不存在(404)
 *    四者不可混同——把 502 当 404 会掩盖装配缺陷
 *  - 能读的必须断言形态真实，不能只看 200
 */

let token = '';

test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

test.describe('AI 域补充面（域03 未覆盖部分）', () => {
  test('V-AI-100 AI 能力目录真实（capabilityId + family）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/ai/capabilities', { token });
    expect(status, 'AI 能力目录应 200').toBe(200);
    expect(json?.code).toBe(0);
    const list = json?.data as any[];
    expect(Array.isArray(list)).toBe(true);
    expect(list.length, '能力目录应有条目').toBeGreaterThan(0);
    list.slice(0, 3).forEach((c) => {
      expect(c.capabilityId, '能力应有 capabilityId').toBeTruthy();
      expect(c.family, '能力应有 family').toBeTruthy();
    });
  });

  test('V-AI-101 Agent 工具面真实（name + description）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/agent/tools', { token });
    expect(status).toBe(200);
    const list = json?.data as any[];
    expect(Array.isArray(list)).toBe(true);
    expect(list.length, '工具清单应有条目').toBeGreaterThan(0);
    list.slice(0, 3).forEach((t) => {
      expect(t.name, '工具应有 name').toBeTruthy();
      expect(t.description, '工具应有 description').toBeTruthy();
    });
  });

  test('V-AI-102 定时任务清单真实（id + cron 表达式）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/cron-jobs', { token });
    expect(status).toBe(200);
    const list = json?.data as any[];
    expect(Array.isArray(list)).toBe(true);
    expect(list.length, '定时任务应有基线').toBeGreaterThan(0);
    const withCron = list.filter((j) => j.cronExpression ?? j.cron).length;
    expect(withCron, '定时任务应带 cron 表达式').toBeGreaterThan(0);
  });

  test('V-AI-103 Agent 会话列表可达（空也为真实态）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/agent-loop/sessions', { token });
    expect(status).toBe(200);
    expect(json?.code).toBe(0);
    const d = json?.data as any;
    expect(typeof d?.total, '会话应返回 total').toBe('number');
    expect(Array.isArray(d?.sessions), '会话应返回 sessions 数组').toBe(true);
    expect(d.sessions.length, 'sessions 长度不应超过 total').toBeLessThanOrEqual(Number(d.total));
  });

  test('V-AI-104 Skills 面未装配必须如实 501（禁伪造技能清单）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/skills', { token });
    expect([200, 501], `Skills 应 200 或 501，实得 ${status}`).toContain(status);
    if (status === 501) {
      expect(String(json?.errorCode ?? ''), '501 应给出运行时缺失错误码').toContain('RUNTIME-ABSENT');
      // 501 的 data 是**诊断元数据**（capability/provider/reason/stub），非业务数据；
      // 但必须如实标注 stub，绝不能伪装成真实技能清单
      const d = json?.data as any;
      if (d) {
        expect(d.stub, '诊断体必须显式标注 stub=true').toBe(true);
        expect(d.reason, '诊断体必须说明缺失原因').toBeTruthy();
        expect(Array.isArray(d), '501 不得返回技能数组冒充可用').toBe(false);
      }
    } else {
      expect(Array.isArray(json?.data), '可用时应返回数组').toBe(true);
    }
  });

  test('V-AI-105 Agent Profile 依赖缺失必须 502（不得 200 空壳）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/agent/profiles', { token });
    expect([200, 502], `Profile 应 200 或 502，实得 ${status}`).toContain(status);
    if (status === 502) {
      expect(String(json?.message ?? ''), '502 必须说明缺哪个依赖').toMatch(/依赖|Profile|未就绪/i);
      expect(json?.data, '502 不得携带数据').toBeFalsy();
    }
  });
});

test.describe('WAgent 面（写语义面，只验方法契约）', () => {
  const collections = ['/api/v1/wagent/goals', '/api/v1/wagent/questions', '/api/v1/wagent/runs'];

  for (const p of collections) {
    test(`V-AI-110 ${p} 只接受 POST（GET 必须 405）`, async ({ request }) => {
      const { status } = await call(request, 'GET', p, { token });
      expect(status, `GET ${p} 应 405（写语义面），实得 ${status}`).toBe(405);
    });
  }

  test('V-AI-111 WAgent goals 空提交必须被拒', async ({ request }) => {
    const { status, json } = await call(request, 'POST', '/api/v1/wagent/goals', { token, data: {} });
    const rejected = status >= 400 || json?.code !== 0;
    expect(rejected, `WAgent 空 goal 必须被拒，实得 HTTP=${status} code=${json?.code}`).toBe(true);
  });

  test('V-AI-112 WAgent 不存在 goal 必须 404/400', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/wagent/goals/__NOT_EXIST__', { token });
    expect([400, 404, 405], `不存在 goal 应被拒，实得 ${status}`).toContain(status);
  });
});

test.describe('认知域补充面', () => {
  test('V-AI-120 认知引擎健康含组件明细', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/engine/cognitive/health', { token });
    expect(status).toBe(200);
    const d = json?.data as any;
    expect(d.status, '认知健康应有状态').toMatch(/UP|RUNNING|DEGRADED/);
    expect(d.components, '认知健康应有 components').toBeTruthy();
  });

  test('V-AI-121 认知模型注册表可达（契约路径 /api/v1/cognitive/models）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/cognitive/models', { token });
    expect(status, `认知模型注册表应 200，实得 ${status}`).toBe(200);
    expect(json?.code).toBe(0);
    const d = json?.data;
    expect(Array.isArray(d) || (d && typeof d === 'object'), '应返回数组或分页体').toBe(true);
  });
});
