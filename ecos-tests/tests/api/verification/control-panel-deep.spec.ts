import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * B5/B6·控制面板 — 端点面补测（矩阵域08 的 V-CTRL-*）。
 *
 * 域08 按「侧边栏控制面板 5 项」组织，绝大多数是 Tab 渲染类（需浏览器点击），
 * 本 spec 只判**能从后端面证实的部分**：五组后端读面可达、引擎六类健康一致、
 * 权限门控（无 token 全拒）、审计查询需认证。UI 交互项在分册「未覆盖清单」里登记。
 *
 * 反假绿纪律：
 *  - 引擎健康必须逐个 6 引擎都查，不许抽查一个就当「六类都行」
 *  - 无 token 必须逐个面都拒，不许只测一个
 */

let token = '';
test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

test.describe('一、控制面板五组后端面', () => {
  const faces = [
    { id: 'V-CTRL-SEC-003', path: '/api/v1/security/policies', name: '安全中心·策略' },
    { id: 'V-CTRL-SEC-004', path: '/api/v1/security/audit/logs', name: '安全中心·审计' },
    { id: 'V-CTRL-TASK-001', path: '/api/v1/task/list', name: '任务中心·列表' },
    { id: 'V-CTRL-MON-001', path: '/api/v1/ecos/monitoring/dashboard', name: '监控中心·指标' },
    { id: 'V-CTRL-DICT-001', path: '/api/v1/system/dict', name: '数据字典·字典项' },
    { id: 'V-CTRL-DICT-002', path: '/api/v1/dict/tables', name: '数据字典·表模式' },
    { id: 'V-CTRL-GW-002', path: '/api/v1/monitor/alerts', name: '告警中心·列表' },
  ];

  for (const { id, path, name } of faces) {
    test(`${id} ${name} 后端面可达且 code=0`, async ({ request }) => {
      const { status, json } = await call(request, 'GET', path, { token });
      expect(status, `${name} 应 200，实得 ${status}`).toBe(200);
      expect(json?.code, `${name} code 应为 0`).toBe(0);
      // eslint-disable-next-line no-console
      console.log(`[${id}] ${path} → 200`);
    });
  }
});

test.describe('二、引擎健康六类一致（V-CTRL-MON-004 / V-CTRL-ENG-001~006）', () => {
  const engines = ['security', 'data', 'ontology', 'knowledge', 'cognitive', 'ai'];

  for (const e of engines) {
    test(`V-CTRL-ENG ${e} 引擎健康可达且带 status 字段`, async ({ request }) => {
      const { status, json } = await call(request, 'GET', `/api/v1/engine/${e}/health`, { token });
      expect(status, `${e} 健康应 200，实得 ${status}`).toBe(200);
      expect(json?.data?.status ?? json?.data?.state, `${e} 健康响应应带 status 字段`).toBeTruthy();
      // eslint-disable-next-line no-console
      console.log(`[V-CTRL-ENG] ${e} → ${status} status=${json?.data?.status ?? json?.data?.state}`);
    });
  }
});

test.describe('三、权限门控（V-CTRL-S-001~004）', () => {
  const guarded = [
    { path: '/api/v1/security/policies', name: '安全策略' },
    { path: '/api/v1/task/list', name: '任务列表' },
    { path: '/api/v1/system/dict', name: '字典' },
    { path: '/api/v1/monitor/alerts', name: '告警' },
    { path: '/api/v1/ecos/monitoring/dashboard', name: '监控聚合' },
  ];

  test('V-CTRL-S-001 无 token 访问控制面板五面全部被拒', async ({ request }) => {
    for (const { path, name } of guarded) {
      const res = await request.fetch(path, { headers: { Accept: 'application/json' } });
      expect([401, 403], `${name} 无 token 应 401/403，实得 ${res.status()}`).toContain(res.status());
    }
  });

  test('V-CTRL-S-004 审计查询需认证（无 token 拒绝）', async ({ request }) => {
    const res = await request.fetch('/api/v1/security/audit/logs', { headers: { Accept: 'application/json' } });
    expect([401, 403], `审计无 token 应 401/403，实得 ${res.status()}`).toContain(res.status());
  });

  test('V-CTRL-S-003 字典写越权：无 token 写必须拒绝', async ({ request }) => {
    const res = await request.fetch('/api/v1/system/dict', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      data: { dictType: 'VERIFY', dictLabel: 'x', dictValue: 'x' },
    });
    expect([401, 403, 400], `无 token 写字典应拒绝，实得 ${res.status()}`).toContain(res.status());
  });
});
