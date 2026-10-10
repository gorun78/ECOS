/**
 * license-allowlist.test.ts — 冻结门禁名 `enterpriseDepsAreRegistered`
 * （详设-08 §九 W212/C194，依赖治理 PMO-C）
 *
 * 口径：商业/企业许可 npm 依赖必须在本文件登记许可属主与裁决档，未登记即红；
 * 同时 W212 已实证 `GEMINI_API_KEY` 系旧文档虚构（文档已更正，PRD-00），
 * 代码面应保持零引用——以本门禁防回流。
 *
 * 登记册（当前唯一企业依赖）：
 *   ag-grid-enterprise — SOC/Enterprise（Seed 许可），AG Grid 商业 EULA；
 *   属主 PMO-C 登记在位（W212/C194），替换/续期须经 PMO 裁决，不允许静默移除。
 */
// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));
const PKG_PATH = fileURLToPath(new URL('../../package.json', import.meta.url));

/** 企业/商业许可依赖登记册（键 = npm 包名）。新增企业依赖须先在此登记并挂 PMO 裁决档。 */
const ENTERPRISE_REGISTRY: Record<string, string> = {
  'ag-grid-enterprise': 'AG Grid Enterprise EULA（商业许可）— PMO-C 登记在位（W212/C194）',
};

/** 名称启发式：视为"需登记"的商业依赖特征（保守：enterprise 字样 + 已知商业包）。 */
function requiresRegistration(name: string): boolean {
  return /-enterprise\b|^enterprise-/i.test(name) || Object.keys(ENTERPRISE_REGISTRY).includes(name);
}

function walk(dir: string, out: string[]): void {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (entry === 'node_modules' || entry.startsWith('.')) continue;
    const st = statSync(full);
    if (st.isDirectory()) walk(full, out);
    else if (entry.endsWith('.ts') || entry.endsWith('.tsx')) out.push(full);
  }
}

describe('license-allowlist — 企业许可依赖登记 (W212/C194, PMO-C)', () => {
  it('enterpriseDepsAreRegistered', () => {
    const pkg = JSON.parse(readFileSync(PKG_PATH, 'utf8')) as {
      dependencies?: Record<string, string>;
      devDependencies?: Record<string, string>;
    };
    const allDeps = { ...pkg.dependencies, ...pkg.devDependencies };

    const unregistered = Object.keys(allDeps)
      .filter((name) => requiresRegistration(name) && !(name in ENTERPRISE_REGISTRY))
      .sort();
    expect(
      unregistered,
      unregistered.length
        ? `未登记的企业/商业许可依赖（须 PMO-C 登记后方可引入）：${unregistered.join(', ')}`
        : undefined,
    ).toEqual([]);

    // 登记册与 package.json 对账：登记的依赖必须真实在位（防止登记过期）。
    const stale = Object.keys(ENTERPRISE_REGISTRY).filter(
      (name) => requiresRegistration(name) && !(name in allDeps),
    );
    expect(stale, `登记册中存在但 package.json 已移除的依赖：${stale.join(', ')}`).toEqual([]);

    // W212：GEMINI_API_KEY 系旧文档虚构 ⇒ src 代码面零引用。
    const files: string[] = [];
    walk(SRC_ROOT, files);
    const geminiRefs: string[] = [];
    for (const f of files) {
      const rel = relative(SRC_ROOT, f).split(sep).join('/');
      if (rel.includes('__tests__') || rel.endsWith('.test.ts') || rel.endsWith('.spec.ts')) continue;
      if (readFileSync(f, 'utf8').includes('GEMINI_API_KEY')) geminiRefs.push(rel);
    }
    expect(
      geminiRefs,
      geminiRefs.length ? `GEMINI_API_KEY 虚构键回流代码面（W212 已更正文档）：${geminiRefs.join(', ')}` : undefined,
    ).toEqual([]);
  });
});
