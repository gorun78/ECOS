import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * B7·控制面板 — API 级（对应矩阵域08）。
 *
 * 反假绿纪律：
 *  - 任务中心统计必须与列表**交叉对账**（total 不能凭空出现）
 *  - 任务状态机：不存在的任务必须 404；非法跃迁必须被拒（不得静默成功）
 *  - 字典表/列两模式都必须真实可用（空数组是真实态，404 不是）
 */

let token = '';

test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

async function ok(request: any, path: string, label: string): Promise<any> {
  const { status, json } = await call(request, 'GET', path, { token });
  expect(status, `${label} HTTP 应 200，实得 ${status}`).toBe(200);
  expect(json?.code, `${label} code 应为 0（${json?.message ?? ''}）`).toBe(0);
  return json?.data;
}

test.describe('任务中心（V-CTRL-TASK）', () => {
  test('V-CTRL-TASK-001 任务列表真实分页', async ({ request }) => {
    const d = await ok(request, '/api/v1/task/list', '任务列表');
    expect(Array.isArray(d.items), '任务列表应含 items').toBe(true);
    expect(typeof d.total === 'number' || d.items.length > 0, '应能看出总量').toBe(true);
    if (d.items.length) {
      const t = d.items[0];
      expect(t.taskId, '任务行应有 taskId').toBeTruthy();
      expect(t.taskName ?? t.name, '任务行应有名称').toBeTruthy();
      // 列表 VO 不含状态字段（状态由 /{id}/status 提供）；此处只校验身份三要素
      expect(t.taskType ?? t.type ?? t.category, '任务行应有类型/分类').toBeTruthy();
    }
  });

  test('V-CTRL-TASK-002 统计与列表交叉对账（防编造计数）', async ({ request }) => {
    const stats = await ok(request, '/api/v1/task/stats', '任务统计');
    const list = await ok(request, '/api/v1/task/list', '任务列表');
    expect(typeof stats.total, '统计应有 total').toBe('number');
    ['running', 'pending', 'succeeded', 'failed', 'cancelled'].forEach((k) => {
      if (stats[k] !== undefined) {
        expect(typeof stats[k], `统计 ${k} 应为数字`).toBe('number');
        expect(stats[k], `统计 ${k} 不应为负`).toBeGreaterThanOrEqual(0);
      }
    });
    const sum = ['running', 'pending', 'succeeded', 'failed', 'cancelled']
      .map((k) => Number(stats[k] ?? 0))
      .reduce((a, b) => a + b, 0);
    if (stats.total > 0 && sum > 0) {
      expect(sum, `各状态之和(${sum}) 不应超过 total(${stats.total})`).toBeLessThanOrEqual(stats.total);
    }
    // 列表条数与 total 应对得上（列表未分页截断时严格相等）
    const listTotal = Number(list.total ?? list.items.length);
    expect(listTotal, `列表 total(${listTotal}) 应与统计 total(${stats.total}) 一致`).toBe(stats.total);
  });

  test('V-CTRL-TASK-003 任务类型清单非空（按分类分组）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/task/types', { token });
    expect(status, '任务类型应可达').toBe(200);
    expect(json?.code).toBe(0);
    const d = json?.data as any;
    // 真实形态：{categories:[{key,label,types:[...]}]}
    const cats = d?.categories ?? (Array.isArray(d) ? d : null);
    expect(Array.isArray(cats), `任务类型应按分类返回，实得 keys=${Object.keys(d ?? {}).join(',')}`).toBe(true);
    expect(cats.length, '任务分类应有条目').toBeGreaterThan(0);
    cats.forEach((c: any) => {
      expect(c.key ?? c.name, '分类应有 key').toBeTruthy();
      expect(Array.isArray(c.types), '分类应含 types 数组').toBe(true);
      expect(c.types.length, `分类 ${c.key} 应有具体类型`).toBeGreaterThan(0);
    });
  });

  test('V-CTRL-TASK-004 不存在任务必须 404（反向探针）', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/task/__NOT_EXIST__', { token });
    expect([400, 404], `不存在任务应 400/404，实得 ${status}`).toContain(status);
  });

  test('V-CTRL-TASK-005 不存在任务的状态查询必须 404', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/task/__NOT_EXIST__/status', { token });
    expect([400, 404], `不存在任务状态应 400/404，实得 ${status}`).toContain(status);
  });

  test('V-CTRL-TASK-006 不存在任务的取消必须被拒（不得假成功）', async ({ request }) => {
    const { status, json } = await call(request, 'POST', '/api/v1/task/__NOT_EXIST__/cancel', { token, data: {} });
    const rejected = status >= 400 || json?.code !== 0;
    expect(rejected, `取消不存在任务必须被拒，实得 HTTP=${status} code=${json?.code}`).toBe(true);
  });

  test('V-CTRL-TASK-007 不存在任务的归档必须被拒', async ({ request }) => {
    const { status, json } = await call(request, 'POST', '/api/v1/task/__NOT_EXIST__/archive', { token, data: {} });
    const rejected = status >= 400 || json?.code !== 0;
    expect(rejected, `归档不存在任务必须被拒，实得 HTTP=${status} code=${json?.code}`).toBe(true);
  });

  test('V-CTRL-TASK-008 空提交必须被拒（不得创建空任务）', async ({ request }) => {
    const { status, json } = await call(request, 'POST', '/api/v1/task/submit', { token, data: {} });
    const rejected = status >= 400 || json?.code !== 0;
    expect(rejected, `空提交必须被拒，实得 HTTP=${status} code=${json?.code}`).toBe(true);
  });
});

test.describe('数据字典（V-CTRL-DICT）', () => {
  test('V-CTRL-DICT-001 字典类型列表真实', async ({ request }) => {
    const list = await ok(request, '/api/v1/system/dict', '字典类型');
    expect(Array.isArray(list)).toBe(true);
    expect(list.length, '字典类型应有基线').toBeGreaterThan(0);
  });

  test('V-CTRL-DICT-002 字典表模式可达（空表也是真实态，不得 404）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/dict/tables', { token });
    expect(status, `/dict/tables 应 200，实得 ${status}`).toBe(200);
    expect(json?.code).toBe(0);
    expect(Array.isArray(json?.data), '字典表应为数组').toBe(true);
  });

  test('V-CTRL-DICT-003 字典类型维度自查（每行有 dictType + 非空 items）', async ({ request }) => {
    const list = await ok(request, '/api/v1/system/dict', '字典类型');
    list.slice(0, 3).forEach((row: any) => {
      // 真实形态：{dictType:"agent_type", itemCount:4, items:[{id,dictType,...}]}
      const key = row.dictType ?? row.dictCode ?? row.code ?? row.typeCode;
      expect(String(key ?? '').trim().length, `字典行应有类型键：${JSON.stringify(row).slice(0, 100)}`).toBeGreaterThan(0);
      if (row.items !== undefined) {
        expect(Array.isArray(row.items), `字典 ${key} 的 items 应为数组`).toBe(true);
        expect(row.items.length, `字典 ${key} 应含具体条目`).toBeGreaterThan(0);
        if (typeof row.itemCount === 'number') {
          expect(row.itemCount, `字典 ${key} 的 itemCount 应与 items 长度一致`).toBe(row.items.length);
        }
      }
    });
  });
});

test.describe('监控中心（V-CTRL-MON）', () => {
  test('V-CTRL-MON-001 监控三面齐备（指标/健康/告警）', async ({ request }) => {
    const metrics = await ok(request, '/api/monitor', '监控指标');
    expect(metrics.system, '应有 system 段').toBeTruthy();
    const health = await ok(request, '/api/monitor/health', '监控健康');
    expect(health.status, '健康应有 status').toBeTruthy();
    const alerts = await ok(request, '/api/monitor/alerts', '监控告警');
    expect(alerts, '告警面应返回对象').toBeTruthy();
  });

  test('V-CTRL-MON-002 监控数值不得为编造常量（多字段为正数）', async ({ request }) => {
    const d = await ok(request, '/api/monitor', '监控指标');
    const s = d.system;
    const numeric = [s.heap_used_mb, s.heap_max_mb, s.cpu_cores].filter((v) => typeof v === 'number');
    expect(numeric.length, '至少三个系统指标应为数字').toBeGreaterThanOrEqual(3);
    const maxV = Number(s.heap_max_mb ?? 0);
    const usedV = Number(s.heap_used_mb ?? 0);
    expect(maxV, '堆上限应为正数').toBeGreaterThan(0);
    expect(usedV, '堆占用不应超过上限').toBeLessThanOrEqual(maxV);
  });
});
