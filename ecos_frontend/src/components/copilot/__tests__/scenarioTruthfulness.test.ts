import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

/**
 * Copilot 场景真实性回归哨兵（2026-11-14 验证批，V-COP-021~027）。
 *
 * 背景：原 12 条场景里 8 条（pipeline/ontology/health/lineage + 4 条 ws_*）是
 * `await sleep()` + 成功 toast 的纯演示流 —— 0 次后端调用，「本体建对象」连内存都没变
 * （App.tsx 未向抽屉传 setObjectTypes，落到默认空实现）。经用户裁决整体删除。
 *
 * 本门禁钉住三条不变量，防伪场景回流：
 *   ① 目录里只剩 4 条真实安全场景，且每条都有对应的真实后端通道；
 *   ② 自动化 hook 内不得出现 `await sleep(`（逐步推进必须来自真实调用耗时）；
 *   ③ 抽屉不得再把空实现的 setXxx 透传给 hook（那正是假成功的来源）。
 *
 * 注意：本测试只做静态文本断言，不做运行时可达性证明（那属活体 E2E 面）。
 */

const ROOT = resolve(__dirname, '../../..');
const read = (rel: string) => readFileSync(resolve(ROOT, rel), 'utf8');

/** 去掉行注释与块注释，避免注释里的示例文本造成误判。 */
function stripComments(src: string): string {
  return src
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

const REAL_SCENARIOS = ['sec_gdpr', 'sec_row_col', 'sec_finance', 'sec_audit'];
const FAKE_SCENARIOS = [
  'pipeline', 'ontology', 'health', 'lineage',
  'ws_generate_dashboard', 'ws_auto_bind', 'ws_inject_copilot', 'ws_transform_theme',
];

describe('copilot › scenario truthfulness (2026-11-14 伪场景清退回归哨兵)', () => {
  const catalogSrc = stripComments(read('components/copilot/AgentScenarioData.tsx'));
  const hookSrc = stripComments(read('components/copilot/useAipAutomation.ts'));
  const drawerSrc = stripComments(read('components/copilot/AIPCopilotDrawer.tsx'));

  it('① 目录只剩 4 条真实安全场景，8 条伪场景不得回流', () => {
    for (const s of REAL_SCENARIOS) {
      expect(catalogSrc, `真实场景 ${s} 必须在目录里`).toContain(`'${s}'`);
    }
    for (const s of FAKE_SCENARIOS) {
      // 允许出现在注释解释里（已 strip），但不得作为场景字面量出现
      expect(catalogSrc, `伪场景 ${s} 不得回到目录（它是纯 sleep 演示流）`)
        .not.toContain(`'${s}'`);
    }
  });

  it('② 自动化 hook 内不得出现 await sleep（逐步推进须来自真实调用耗时）', () => {
    const sleepCalls = hookSrc.match(/await\s+sleep\s*\(/g) ?? [];
    expect(sleepCalls.length, `hook 内出现 ${sleepCalls.length} 处 await sleep —— 会造成「toast 说完成、实际没做事」`)
      .toBe(0);
    expect(hookSrc, 'hook 不得再定义 sleep 助手').not.toMatch(/const\s+sleep\s*=/);
  });

  it('③ hook 必须真实调用后端（ABAC 评估 + 审计日志两条通道）', () => {
    expect(hookSrc, 'must call real ABAC evaluate').toContain('evaluateAbacPolicy');
    expect(hookSrc, 'must call real audit-logs').toContain('fetchSecurityAuditLogs');
  });

  it('④ 抽屉不得把空实现的 setXxx 透传给 hook（假成功的来源）', () => {
    // 旧实现形如 setObjectTypes: (v: any) => { (setObjectTypes as any)(v); }
    expect(drawerSrc, '不得再透传 setObjectTypes/setSecurityOrgs 等空实现 state setter')
      .not.toMatch(/set(ObjectTypes|LinkTypes|Datasets|SecurityOrgs|SecurityProjects|SecurityMarkings|SecurityPurposes|SecurityRowColPolicies|SecurityAuditLogs)\s*:/);
    expect(drawerSrc, '不得出现 (() => {}) as any 占位 setter').not.toContain('(() => {}) as any');
  });

  it('⑤ 反向约束：detectScenarioType 未命中安全关键字时必须返回 null（回落真实 chat）', () => {
    expect(drawerSrc, 'detectScenarioType 必须可返回 null').toMatch(/\):\s*AgentScenarioType\s*\|\s*null/);
    expect(drawerSrc, '未命中须 return null，不得回落到某个默认伪场景').toMatch(/return\s+null;/);
  });
});
