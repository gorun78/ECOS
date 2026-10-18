// @vitest-environment node
/**
 * 决策面（P09 四联视图）路径/传输单源守卫（详设-09 v1.2 §十一 H-8）。
 *
 * <p>约束 `pages/workbench/[scenarioId]/decisions.tsx` 与 `decisions/*.tsx`：
 * ① 不出现 `"/api` 字面量（outcomes 走 `services/cognitive/outcomesApi`，
 *    actions/decisions 走 `services/scenarioActionClient`，两者均由单源基址常量派生）；
 * ② 不直接 fetch（无第二套传输/auth 拼接）；
 * ③ 取数面必须 import 对应单源 client；
 * ④ 演示徽标判定单源：业务面禁给 `DemoBadge` 传 `force`（防各处三元判断漂移，
 *    翻转只能改 `services/forecastScenarioMode.ts` 一处）。</p>
 */
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });

const ROOT = join(process.cwd(), 'src', 'pages', 'workbench', '[scenarioId]');
const FILE_BY_BASE: Record<string, string> = {
  'decisions.tsx': join(ROOT, 'decisions.tsx'),
  'ActionFormPanel.tsx': join(ROOT, 'decisions', 'ActionFormPanel.tsx'),
  'DecisionBoardPanel.tsx': join(ROOT, 'decisions', 'DecisionBoardPanel.tsx'),
  'OutcomeQuadView.tsx': join(ROOT, 'decisions', 'OutcomeQuadView.tsx'),
};

/** outcomes / actions / decisions 三面完整字面量 —— 组件面只允许经 client 派生 */
const HARDCODE_API = /["'`]\/api\b|\/v1\/cognitive\/outcomes|\/v1\/workspace\/scenarios|\/v1\/workspace\/actions/;
const DIRECT_FETCH = /\bfetch\s*\(/;

/** 注释行豁免：只统计实质代码（与 blueprint/cognitive 守卫同一口径） */
function codeOnly(src: string): string {
  return src
    .split('\n')
    .filter((l) => {
      const t = l.trim();
      return !t.startsWith('//') && !t.startsWith('*') && !t.startsWith('/*');
    })
    .join('\n');
}

describe('决策面（P09 四联视图）— 路径/传输/徽标判定单源', () => {
  it('目录下存在四联视图文件', () => {
    for (const [name, abs] of Object.entries(FILE_BY_BASE)) {
      expect(existsSync(abs), `决策面缺少 ${name}`).toBe(true);
    }
  });

  it('组件不硬编码 outcomes/actions/decisions 路径且无第二套 fetch，取数一律经单源 client', () => {
    // decisions.tsx 为纯排版壳（子面板各自取数），不强制 import client
    const requiredImports: Record<string, string[]> = {
      'ActionFormPanel.tsx': ['scenarioActionClient'],
      'DecisionBoardPanel.tsx': ['scenarioActionClient'],
      'OutcomeQuadView.tsx': ['cognitive/outcomesApi', 'cognitiveClient'],
    };
    const offenders: string[] = [];
    for (const [name, abs] of Object.entries(FILE_BY_BASE)) {
      const code = codeOnly(readFileSync(abs, 'utf8'));
      if (HARDCODE_API.test(code)) offenders.push(`${name}: 硬编码 outcomes/actions/decisions 路径`);
      if (DIRECT_FETCH.test(code)) offenders.push(`${name}: 直接 fetch`);
      for (const dep of requiredImports[name] ?? []) {
        if (!code.includes(dep)) offenders.push(`${name}: 未 import ${dep}`);
      }
    }
    expect(offenders, offenders.join('\n')).toEqual([]);
  });

  it('DemoBadge 判定单源：业务面不传 force，翻转只改 forecastScenarioMode 一处', () => {
    for (const [name, abs] of Object.entries(FILE_BY_BASE)) {
      const code = codeOnly(readFileSync(abs, 'utf8'));
      if (!code.includes('DemoBadge')) continue;
      expect(code, `${name} 禁给 DemoBadge 传 force（演示徽标判定必须单源）`).not.toMatch(
        /<DemoBadge[^>]*\bforce=/,
      );
    }
    // 判定实现本身只允许一处真值来源
    const mode = codeOnly(
      readFileSync(join(process.cwd(), 'src', 'services', 'forecastScenarioMode.ts'), 'utf8'),
    );
    expect(mode).toMatch(/isForecastDemoMode/);
  });

  it('isForecastDemoMode 调用面唯一：只允许 DemoBadge 一处消费（防各页各自三元漂移）', () => {
    const walk = (dir: string, acc: string[] = []): string[] => {
      for (const e of readdirSync(dir)) {
        const full = join(dir, e);
        if (statSync(full).isDirectory()) {
          if (e !== 'node_modules') walk(full, acc);
        } else if ((e.endsWith('.ts') || e.endsWith('.tsx')) && !/\.(spec|test)\.[jt]sx?$/.test(e)) {
          acc.push(full);
        }
      }
      return acc;
    };
    const srcRoot = join(process.cwd(), 'src');
    const allowed = new Set([
      join('src', 'components', 'common', 'status', 'DemoBadge.tsx'),
      join('src', 'services', 'forecastScenarioMode.ts'),
    ]);
    const consumers = walk(srcRoot)
      .map((f) => f.slice(process.cwd().length + 1))
      .filter((rel) => !allowed.has(rel))
      .filter((rel) => codeOnly(readFileSync(join(process.cwd(), rel), 'utf8')).includes('isForecastDemoMode'));
    expect(consumers, `演示态判定只应出自 DemoBadge，实际: ${consumers.join(', ')}`).toEqual([]);
  });

  it('outcomes 路径全 src 无第二处字面量（只允许 cognitiveClient.ts 与 cognitive/outcomesApi.ts）', () => {
    const allowed = new Set([
      join('src', 'services', 'cognitiveClient.ts'),
      join('src', 'services', 'cognitive', 'outcomesApi.ts'),
    ]);
    const walk = (dir: string, acc: string[] = []): string[] => {
      for (const e of readdirSync(dir)) {
        const full = join(dir, e);
        if (statSync(full).isDirectory()) {
          if (e !== 'node_modules') walk(full, acc);
        } else if ((e.endsWith('.ts') || e.endsWith('.tsx')) && !/\.(spec|test)\.[jt]sx?$/.test(e)) {
          acc.push(full);
        }
      }
      return acc;
    };
    const srcRoot = join(process.cwd(), 'src');
    const offenders = walk(srcRoot)
      .filter((f) => {
        const rel = f.slice(process.cwd().length + 1);
        if (allowed.has(rel)) return false;
        return /["'`]\/api\/v1\/cognitive\/outcomes/.test(codeOnly(readFileSync(f, 'utf8')));
      })
      .map((f) => f.slice(srcRoot.length + 1).replace(/\\/g, '/'));
    expect(offenders, `outcomes 路径不应有第二处硬编码: ${offenders.join(', ')}`).toEqual([]);
  }, 30_000);
});
