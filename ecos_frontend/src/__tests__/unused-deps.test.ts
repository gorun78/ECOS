/**
 * unused-deps.test.ts — 冻结门禁名 `graphQLDeclaredOnlyWhereEndpointExists`
 * （详设-08 §九 W211/C193，架构铁律 §2.5 底座收口；裁决 R-57②：
 *  `@apollo/client` + `graphql` 定性 **knownLegacy 停写不删**）
 *
 * 口径：全仓无 GraphQL 端点归属 ⇒ 存量依赖保留（不擅删，删除动作待 §14.4 授权），
 * 但**引用面冻结**：除登记的 legacy 载体文件外，任何新增 .ts/.tsx 引用
 * @apollo/client / graphql 即红（停写）。
 *
 * 基线（2026-10 实测，随 R-57 属主处置渐进收敛，清单只减不增）：
 *   providers/GraphQLProvider.tsx —— ApolloProvider 装配壳（当前无任何页面引用它，死接线）
 *   providers/graphqlClient.ts    —— ApolloClient/HttpLink 构造（同上死链路）
 * （types/declarations.d.ts 的 webpack-graphql 类型声明属 .d.ts，不在源码扫描面。）
 */
// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));

const BASELINE = new Set([
  'providers/GraphQLProvider.tsx',
  'providers/graphqlClient.ts',
]);

const REF_RE = /from\s*["'](@apollo\/client(?:\/[^"']*)?|graphql(?:\/[^"']*)?)["']/;

function walk(dir: string, out: string[]): void {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (entry === 'node_modules' || entry.startsWith('.')) continue;
    const st = statSync(full);
    if (st.isDirectory()) walk(full, out);
    else if ((entry.endsWith('.ts') || entry.endsWith('.tsx')) && !entry.endsWith('.d.ts')) out.push(full);
  }
}

function isTestFile(rel: string): boolean {
  return (
    rel.includes('__tests__') ||
    rel.endsWith('.test.ts') || rel.endsWith('.test.tsx') ||
    rel.endsWith('.spec.ts') || rel.endsWith('.spec.tsx') ||
    rel.startsWith('test/')
  );
}

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

describe('unused-deps — GraphQL 引用面冻结 (W211/C193, R-57②)', () => {
  it('graphQLDeclaredOnlyWhereEndpointExists', () => {
    const files: string[] = [];
    walk(SRC_ROOT, files);
    const newRefs: string[] = [];

    for (const f of files) {
      const rel = relative(SRC_ROOT, f).split(sep).join('/');
      if (isTestFile(rel) || BASELINE.has(rel)) continue;
      const lines = readFileSync(f, 'utf8').split('\n');
      lines.forEach((line, i) => {
        if (isComment(line)) return;
        if (REF_RE.test(line)) {
          newRefs.push(`${rel}:${i + 1} — ${line.trim().slice(0, 100)}`);
        }
      });
    }

    expect(
      newRefs,
      newRefs.length
        ? `R-57② knownLegacy 停写：graphql/Apollo 引用面新增 ${newRefs.length} 处（基线仅 providers 死链路，禁止扩散）：\n${newRefs.join('\n')}`
        : undefined,
    ).toEqual([]);
  });
});
