// @vitest-environment node
/**
 * 场景蓝图路径单源守卫（node 环境，设计方案 §3.9「无假真理/路径单源」）。
 *
 * <p>约束 pages/workbench/[scenarioId]/blueprint/*.tsx：
 * ① 不出现 `"/api` 或 `/v1/business/scenarios` / `/v1/ai/capabilities` 字面量
 *    （一律经 scenarioBlueprintClient / capabilityClient 派生）；
 * ② 不直接 fetch（无第二套传输/auth 拼接）；
 * ③ 消费面必须 import 对应单源 client（蓝图面 → scenarioBlueprintClient；
 *    能力面 CapabilityPanel → capabilityClient）。</p>
 */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, sep } from 'node:path';
import { describe, expect, it } from 'vitest';

const BP_DIR = join(process.cwd(), 'src', 'pages', 'workbench', '[scenarioId]', 'blueprint');

/** 被测面对象 = 组件源文件；测试自身（__tests__ / *.spec.* / *.test.*）不是组件，须排除。 */
function isGuardTestFile(name: string): boolean {
  return /\.spec\.[jt]sx?$/.test(name) || /\.test\.[jt]sx?$/.test(name);
}

function tsxFiles(dir: string): string[] {
  const out: string[] = [];
  for (const e of readdirSync(dir)) {
    if (e === '__tests__') continue;
    const full = join(dir, e);
    if (statSync(full).isDirectory()) out.push(...tsxFiles(full));
    else if ((e.endsWith('.tsx') || e.endsWith('.ts')) && !isGuardTestFile(e)) out.push(full);
  }
  return out;
}

const HARDCODE_API = /["'`]\/api\b|\/v1\/business\/scenarios|\/v1\/ai\/capabilities|\/v1\/cognitive\/context/;
const DIRECT_FETCH = /\bfetch\s*\(/;

describe('场景蓝图面 — 路径/传输单源', () => {
  it('目录下存在组件文件', () => {
    expect(tsxFiles(BP_DIR).length).toBeGreaterThan(0);
  });

  it('组件不硬编码 /api 或蓝图/能力/上下文路径前缀，且经单源 client 取数', () => {
    const offenders: string[] = [];
    for (const f of tsxFiles(BP_DIR)) {
      const src = readFileSync(f, 'utf8');
      const rel = f.split(sep).join('/');
      const base = rel.split('/').pop() ?? rel;
      // 允许注释行豁免：仅对非注释命中的实质代码计违规
      const codeLines = src
        .split('\n')
        .filter((l) => !l.trim().startsWith('//') && !l.trim().startsWith('*') && !l.trim().startsWith('/*'));
      const code = codeLines.join('\n');
      if (HARDCODE_API.test(code)) offenders.push(`${rel}: 硬编码 API/蓝图/能力/上下文路径`);
      if (DIRECT_FETCH.test(code)) offenders.push(`${rel}: 直接 fetch`);
      // 能力面面板经 capabilityClient；上下文盘点面板经 cognitiveClient（并 import 蓝图类型）；
      // 其余蓝图面经 scenarioBlueprintClient。三者都是路径/auth 单源出口。
      const requiredImports =
        base === 'CapabilityPanel.tsx'
          ? ['capabilityClient']
          : base === 'ContextBuildPanel.tsx'
            ? ['cognitiveClient', 'scenarioBlueprintClient']
            : ['scenarioBlueprintClient'];
      for (const dep of requiredImports) {
        if (!new RegExp(dep).test(code)) offenders.push(`${rel}: 未 import ${dep}`);
      }
    }
    expect(offenders, offenders.join('\n')).toEqual([]);
  });
});
