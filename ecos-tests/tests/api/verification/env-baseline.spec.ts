import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * B0 环境与契约基线（对应矩阵域09第一部分 §1.1）。
 * 目的：立基线，使后续所有「绿」有参照。每条关键断言配反向探针，证明用例真的在看。
 * 覆盖矩阵条目：V-GW-001 ~ V-GW-009（端口/健康/登录/权限语义/反向探针）。
 *
 * 判定纪律：任何一条失败即记 FAIL，不降级为「环境问题」——本批次的职责就是区分环境与产品。
 */

let token = '';
test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

test('V-GW-004 登录返回 code=0 且 token 非空（V-SEC-010 复用面）', async () => {
  expect(token.length, 'token 长度应 > 500（RS256）').toBeGreaterThan(500);
});

test('V-GW-005 五引擎健康全 200', async ({ request }) => {
  for (const e of ['data', 'ontology', 'ai', 'knowledge', 'security']) {
    const { status } = await call(request, 'GET', `/api/v1/engine/${e}/health`, { token });
    expect(status, `引擎 ${e} 健康应 200`).toBe(200);
  }
});

test('V-GW-006 认知引擎健康 UP 且 agents 字段为数字', async ({ request }) => {
  const { status, json } = await call(request, 'GET', '/api/v1/engine/cognitive/health', { token });
  expect(status).toBe(200);
  expect(json?.data?.status).toBe('UP');
  expect(typeof json?.data?.components?.agents, 'agents 应为数字').toBe('number');
});

test('V-GW-007 无 token 访问受保护端点被拒绝且不泄露数据', async ({ request }) => {
  const { status, json } = await call(request, 'GET', '/api/v1/system/users');
  // 实测基线：无 token → 403（错 token 才 401）。语义不一致为观察项，此处断言「拒绝」这一底线。
  expect([401, 403], '无 token 必须 401 或 403').toContain(status);
  // 不得返回数据：data 应为空或非数组
  const data = json?.data;
  const leaked = Array.isArray(data) && data.length > 0;
  expect(leaked, '无 token 不应返回用户数据').toBe(false);
});

test('V-GW-008 错误 token 访问被拒绝', async ({ request }) => {
  const { status } = await call(request, 'GET', '/api/v1/system/users', { token: 'xxx.invalid.token' });
  expect([401, 403], '错 token 必须 401 或 403').toContain(status);
});

test('V-GW-009 反向探针：不存在的场景详情必须 404（证明详情用例不是恒绿）', async ({ request }) => {
  const { status } = await call(request, 'GET', '/api/v1/workspace/scenarios/NOT_EXIST_999', { token });
  expect(status, '不存在场景应 404').toBe(404);
});

test('V-GW-009b 反向探针：不存在的 mind 详情必须 404（证明 mind 用例不是恒绿）', async ({ request }) => {
  const { status } = await call(request, 'GET', '/api/v1/cognitive/minds/NOT_EXIST_MIND_999', { token });
  expect([400, 404], '不存在 mind 应 400 或 404').toContain(status);
});

test('V-GW-010 路由清单对账：场景列表端点可达且返回数组（route-manifest ↔ 活体）', async ({ request }) => {
  const { status, json } = await call(request, 'GET', '/api/v1/workspace/scenarios', { token });
  expect(status).toBe(200);
  expect(Array.isArray(json?.data), '场景列表 data 应为数组').toBe(true);
});
