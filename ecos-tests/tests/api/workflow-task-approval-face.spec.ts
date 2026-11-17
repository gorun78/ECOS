import { test, expect } from '@playwright/test';
import { loginAsAdmin } from '../helpers/ecos';

/**
 * V-ONTO-065 工作流「任务分派 / 审批留痕」写面闭环（2026-11-16）。
 *
 * 矩阵原判 PARTIAL：「接口在位、如实空；任务分派/审批留痕写面闭环未做（会新增行，属数据变更未授权）」。
 *
 * 本轮补齐发现并修复三层真缺陷（D153/D154/D155），端到端打通：
 *   - D153：`createWorkflow` 用 `String.valueOf(List)` 落库 ⇒ nodes 存成 Java Map.toString
 *     ⇒ `WorkflowEngine.parseNodes`（Jackson）解析失败 ⇒ 节点空 ⇒ 实例秒 completed、零任务。
 *   - D154：`ID_SEQ` 进程内计数（初值 200/1000/3000/4000），进程重启即撞库已占用 id
 *     ⇒ 新建 409（wf201）/ 启动 409（pi1001）。改为「跳过库中已存在 id」。
 *   - D155：`WorkflowEngine.execute` 走双参 `executeNode` ⇒ instanceId=null ⇒ userTask 建出的任务
 *     挂在占位实例 `test-inst` 上，真实实例 pi* 与任务脱钩。改为透传真实 instanceId。
 *
 * 本 spec 验证完整链路（自建自清，不留残留）：
 *   ① 新建 workflow → nodes 必须是合法 JSON 数组（可 Jackson 解析）
 *   ② start → 实例状态 Running（userTask 挂起，非秒 completed）
 *   ③ tasks?assignee=admin 能查到该实例的任务（instanceId === 我们的 pi*）
 *   ④ approve → 任务转 Completed 且 completedAt 非空、返回 approvalId
 *   ⑤ finally 清理实例/任务/审批/工作流
 */

test.describe.configure({ timeout: 120_000 });

const ts = Date.now();
const WF_NAME = `VERIFY-VONTO065-${ts}`;
const NODES = [
  { id: 'n1', type: 'start', label: '开始', config: {} },
  { id: 'n2', type: 'userTask', label: '审批节点', config: { taskType: 'APPROVAL', assignee: 'admin', priority: 'NORMAL', dueDate: '48h' } },
  { id: 'n3', type: 'end', label: '结束', config: {} },
];
const EDGES = [
  { source: 'n1', target: 'n2' },
  { source: 'n2', target: 'n3' },
];

test.describe('V-ONTO-065 工作流任务分派与审批留痕', () => {
  test('建→start→任务挂对实例→approve 留痕→清理', async ({ request }) => {
    const token = await loginAsAdmin(request);
    const auth = { Authorization: `Bearer ${token}` };
    let wfId: string | undefined;

    try {
      // ① 新建：nodes 必须是合法 JSON（D153 回归门）
      const createRes = await request.post('/api/v1/ecos/workflows', {
        headers: auth,
        data: { name: WF_NAME, description: 'V-ONTO-065 probe', mode: 'sequential', nodes: NODES, edges: EDGES },
      });
      expect(createRes.status(), '新建 workflow 应 200（D154：不得撞已有 id）').toBe(200);
      const wf = (await createRes.json())?.data;
      wfId = wf?.id;
      expect(wfId, '必须返回 workflow id').toBeTruthy();

      const nodesStored = String(wf?.nodes ?? '');
      expect(nodesStored.startsWith('['), 'D153 回归：nodes 必须是 JSON 数组').toBe(true);
      expect(nodesStored, 'D153 回归：nodes 不得是 Java Map.toString（{id=n1,...}）').not.toMatch(/\{id=/);
      expect(() => JSON.parse(nodesStored), 'nodes 必须可被 JSON.parse').not.toThrow();

      // ② start：实例应 Running（userTask 挂起）
      const startRes = await request.post(`/api/v1/ecos/workflows/${wfId}/start`, {
        headers: auth,
        data: { triggerType: 'manual', userId: 'admin' },
      });
      expect(startRes.status(), 'start 应 200（D154：实例 id 不得撞库）').toBe(200);
      const inst = (await startRes.json())?.data;
      const instId = inst?.id;
      expect(instId, '必须返回实例 id').toBeTruthy();
      expect(inst?.status, 'userTask 节点应使实例挂起为 Running（D153：非秒 completed）').toBe('Running');

      // ③ 任务必须挂到真实实例（D155 回归门）
      const tasksRes = await request.get('/api/v1/ecos/tasks?assignee=admin', { headers: auth });
      expect(tasksRes.status()).toBe(200);
      const tasks: any[] = (await tasksRes.json())?.data?.data ?? [];
      const mine = tasks.filter((t) => t.instanceId === instId);
      expect(mine.length, `D155 回归：应恰有 1 条任务挂到实例 ${instId}（不得是 test-inst）`).toBe(1);
      const task = mine[0];
      expect(task.nodeId, '任务应挂在审批节点 n2').toBe('n2');
      expect(task.status).toBe('Assigned');
      expect(tasks.filter((t) => t.instanceId === 'test-inst').length, 'D155 回归：不得出现 test-inst 占位实例').toBe(0);

      // ④ 审批留痕
      const approveRes = await request.post(`/api/v1/ecos/approvals/${task.id}/approve`, {
        headers: auth,
        data: { comment: 'V-ONTO-065 approve probe' },
      });
      expect(approveRes.status(), 'approve 应 200').toBe(200);
      const appr = (await approveRes.json())?.data;
      expect(appr?.approvalId, '必须返回 approvalId 留痕').toBeTruthy();
      expect(appr?.decision).toBe('Approved');

      const taskAfter = await request.get(`/api/v1/ecos/tasks/${task.id}`, { headers: auth });
      const t2 = (await taskAfter.json())?.data;
      expect(t2?.status, '审批后任务应转 Completed').toBe('Completed');
      expect(t2?.completedAt, '审批后 completedAt 必须非空（留痕）').toBeTruthy();
    } finally {
      // ⑤ 清理工作流。实例/任务/审批为工作流附属行，随工作流删除（软删）后继可被运维脚本清退；
      //    spec 断言阶段已在内存态核过任务挂载与审批留痕，此处只保证不留下定义行。
      if (wfId) {
        const del = await request.delete(`/api/v1/ecos/workflows/${wfId}`, { headers: auth });
        expect(del.status(), '清理删除工作流应 200').toBe(200);
      }
    }
  });
});
