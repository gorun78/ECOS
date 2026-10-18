// @vitest-environment node
/**
 * 场景工作台双结构收敛守卫（设计方案 §3.8 R1~R4 / §3.9，node 环境禁 jsdom）。
 *
 * <p>冻结 2026-10-11 收敛结果，防止回潮：</p>
 * ① #/project_workbench（ScenarioManagementView）只允许 fusion/decision/git 三 Tab，
 *    不得重新 import CognitiveRightPanel / CognitionPanel / MetricsTab；
 * ② CognitiveRightPanel（认知四卡 E1~E4）全应用唯一挂载点 =
 *    L2「高级/调试」子面 AdvancedDebugPanel（迁一删一，E2E COG-IA 单实例断言的前提）；
 * ③ 假真理清除：MetricsTab.tsx 已删除，data.ts 不再导出 threatRadarData/efficiencyData
 *    静态演示图表数据，ScenarioManagementView 不得再出现硬编码 KPI 伪值；
 * ④ L2 场景详情必须挂载 AdvancedDebugPanel（运行面下沉落点）。
 */
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { join, sep } from 'node:path';
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });

const SRC = join(process.cwd(), 'src');

function srcFiles(dir: string, out: string[] = []): string[] {
  for (const e of readdirSync(dir)) {
    const full = join(dir, e);
    if (statSync(full).isDirectory()) {
      if (e === '__tests__') continue;
      srcFiles(full, out);
    } else if (/\.(tsx|ts)$/.test(e)) {
      out.push(full);
    }
  }
  return out;
}

const SMV = join(SRC, 'pages', 'ScenarioManagementView.tsx');
const ADV = join(SRC, 'pages', 'workbench', '[scenarioId]', 'advanced', 'AdvancedDebugPanel.tsx');
const L2 = join(SRC, 'pages', 'workbench', '[scenarioId]', 'index.tsx');
const DATA = join(SRC, 'pages', 'project-workbench', 'data.ts');
const METRICS_TAB = join(SRC, 'pages', 'project-workbench', 'tabs', 'MetricsTab.tsx');

/** 去注释后的源码（行注释/块注释头豁免，与既有单源守卫同口径） */
function codeOf(file: string): string {
  return readFileSync(file, 'utf8')
    .split('\n')
    .filter((l) => {
      const s = l.trim();
      return !(s.startsWith('//') || s.startsWith('*') || s.startsWith('/*'));
    })
    .join('\n');
}

describe('§3.8 R1~R4 双结构收敛守卫', () => {
  it('高级视图只保留 fusion/decision/git 三 Tab，且不再 import 认知/指标面', () => {
    const code = codeOf(SMV);
    const tabs = /const TABS = \[([^\]]*)\]/.exec(code);
    expect(tabs, 'ScenarioManagementView 必须保留显式 TABS 常量').not.toBeNull();
    const keys = tabs![1].split(',').map((s) => s.trim().replace(/["']/g, '')).filter(Boolean);
    expect(keys.sort()).toEqual(['decision', 'fusion', 'git']);
    for (const forbidden of ['CognitiveRightPanel', 'CognitionPanel', 'MetricsTab']) {
      expect(new RegExp(`import\\s+\\S*${forbidden}`).test(code), `高级视图不得重新 import ${forbidden}`).toBe(false);
    }
  });

  it('CognitiveRightPanel 全应用唯一生产挂载点 = AdvancedDebugPanel（迁一删一）', () => {
    const self = join(SRC, 'pages', 'scenario', 'cognition', 'CognitiveRightPanel.tsx');
    // 只计「值导入」（组件挂载）；`import type { … } from './CognitiveRightPanel'`
    // 是子 Tab 复用 MindLoadState 类型，不构成第二渲染实例。
    const VALUE_IMPORT = /import\s+(?!type\s)[A-Za-z_$][\s\S]{0,80}?from\s+['"][^'"]*CognitiveRightPanel['"]/;
    const importers = srcFiles(join(SRC, 'pages'))
      .filter((f) => f !== self)
      .filter((f) => VALUE_IMPORT.test(codeOf(f)))
      .map((f) => f.split(sep).join('/'));
    expect(importers.length, `CognitiveRightPanel import 必须唯一，实得:\n${importers.join('\n')}`).toBe(1);
    expect(importers[0].endsWith('AdvancedDebugPanel.tsx'), '唯一挂载点必须是 L2 高级/调试子面').toBe(true);
  });

  it('AdvancedDebugPanel 存在且被 L2 场景详情挂载', () => {
    expect(existsSync(ADV), 'L2 高级/调试子面文件必须存在（§3.8 R2 运行面落点）').toBe(true);
    expect(/from '\.\/advanced\/AdvancedDebugPanel'/.test(codeOf(L2))).toBe(true);
    expect(/<AdvancedDebugPanel\s/.test(codeOf(L2))).toBe(true);
  });

  it('MetricsTab 假演示面已物理删除且 data.ts 不再导出演示图表数据', () => {
    expect(existsSync(METRICS_TAB), 'MetricsTab.tsx（静态演示雷达/效率图）必须已删除').toBe(false);
    const code = codeOf(DATA);
    expect(/threatRadarData/.test(code)).toBe(false);
    expect(/efficiencyData/.test(code)).toBe(false);
  });

  it('高级视图禁止回归硬编码 KPI 伪值', () => {
    // 用去注释口径：文档注释允许引用被删除的伪值作历史记录，实质代码不允许
    const code = codeOf(SMV);
    for (const fake of ['¥2.85M', '(-93.7%)', 'kpi.block', 'kpi.settle', 'kpi.budget']) {
      expect(code.includes(fake), `无假真理 P0：ScenarioManagementView 不应再出现 ${fake}`).toBe(false);
    }
  });
});
