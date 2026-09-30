import { expect, test } from '@playwright/test';
import { call, loginAsAdmin } from '../helpers/ecos';

/**
 * 迁移自 ecos-tests/t5-pipeline-smoke.mjs（断言 list.200 / list.success / detail.200 / detail.id /
 * detail.nodes>=0 / git.versions.200 / git.versions.arr 逐条对应）。
 * Git 版本端点属卷 02 §「版本×Git 归档」链路：DB 只存在用版本，历史版本走 Git ⇒ 空数组是合法初态。
 */

let token = '';
test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

test.describe.configure({ mode: 'serial' });

let definitionId = '';
let createdHere = false;

test('列出管道定义并选定一个作为详情/Git 版本入参', async ({ request }) => {
  const { status, json } = await call(request, 'GET', '/api/v1/pipeline/definitions', { token });
  expect(status).toBe(200);
  expect(json?.success).toBe(true);
  const items = Array.isArray(json?.data) ? json.data : (json?.data?.items ?? []);
  definitionId = String(items[0]?.id ?? '');

  if (!definitionId) {
    // 空库时按历史冒烟同样自建一条最小定义；只有自建的那条会被删除，不复用他人数据的 id
    const created = await call(request, 'POST', '/api/v1/pipeline/definitions', {
      token,
      data: {
        name: 'e2e-git-versions-probe',
        description: 'Playwright: Git 版本端点探针',
        nodes: [{ id: 'n1', nodeId: 'n1', type: 'SOURCE_JDBC', config: { datasourceId: 'd' }, positionX: 10, positionY: 10 }],
        edges: [],
      },
    });
    definitionId = String(created.json?.data?.id ?? '');
    expect(definitionId, '空库且自建失败').not.toBe('');
    createdHere = true;
  }
});

test('GET /pipeline/definitions/{id} → 200 且 id 一致、nodes 字段存在', async ({ request }) => {
  test.skip(!definitionId, '无可用定义 id');
  const { status, json } = await call(request, 'GET', `/api/v1/pipeline/definitions/${encodeURIComponent(definitionId)}`, { token });
  expect(status).toBe(200);
  expect(String(json?.data?.id ?? '')).toBe(definitionId);
  expect(Array.isArray(json?.data?.nodes), '详情未回显 nodes 数组').toBe(true);
});

test('GET /engine/data/pipeline/git/versions/{id} → 200 且 data 为数组', async ({ request }) => {
  test.skip(!definitionId, '无可用定义 id');
  const { status, json } = await call(request, 'GET', `/api/v1/engine/data/pipeline/git/versions/${encodeURIComponent(definitionId)}`, { token });
  expect(status).toBe(200);
  expect(Array.isArray(json?.data), `data 非数组: ${JSON.stringify(json?.data).slice(0, 120)}`).toBe(true);
});

test.afterAll(async ({ request }) => {
  if (createdHere && definitionId) {
    await call(request, 'DELETE', `/api/v1/pipeline/definitions/${encodeURIComponent(definitionId)}`, { token });
  }
});
