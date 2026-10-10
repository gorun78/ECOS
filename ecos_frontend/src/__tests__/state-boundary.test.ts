/**
 * state-boundary.test.ts — 冻结门禁名 `serverDataNotInZustandStores`
 * （详设-08 §九 W213/C195，前端开发规范 §十：服务端态与客户端态分工双栈）
 *
 * 口径：src/stores/** 的 zustand store 只承载客户端 UI 态；
 * 服务端数据（HTTP 取数/写回）属 React Query / 页面局部 hook 态。违规判据（代码行，
 * 排除注释/测试/.d.ts）：
 *   a) 直接 HTTP 调用：fetch( / apiFetch / apiFetchData / EventSource( / axios.
 *   b) 对 services/ 或 api/ 模块的**值导入**（import type 不计——类型共享不违反边界）。
 *
 * 基线白名单（存量登记 3 个，属主 W213/B 批任务 #119 收敛，R-55②/H6-T5 口径
 * 清单只减不增，新增即红）：
 *   stores/agent/useAgentStore.ts         —— 值导入 services/agentMeshApi（fetch/create 系）
 *   stores/cognitive/useCognitiveStore.ts —— 值导入 services/worldModelGraphApi（fetch/post 系）
 *   stores/useWorkbenchStore.ts           —— 值导入 services/datanetMetadataApi、gitService、ontologyApi
 */
// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));
const STORES_DIR = join(SRC_ROOT, 'stores');

const BASELINE = new Set([
  'stores/agent/useAgentStore.ts',
  'stores/cognitive/useCognitiveStore.ts',
  'stores/useWorkbenchStore.ts',
]);

const HTTP_CALL_RE = /\b(?:apiFetch\w*|fetch(?:Json|Data)?)\s*\(|\bnew\s+EventSource\s*\(|\baxios\s*[.[]/;

function walk(dir: string, out: string[]): void {
  if (!existsSync(dir)) return;
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const st = statSync(full);
    if (st.isDirectory()) walk(full, out);
    else if ((entry.endsWith('.ts') || entry.endsWith('.tsx')) && !entry.endsWith('.d.ts')) out.push(full);
  }
}

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

/** 去除块注释包裹的多行 import 中的行首 *、// 已由 isComment 处理；
 *  这里按"整文件文本"匹配 import 语句，import type 显式排除。 */
function findServiceValueImports(content: string): string[] {
  const out: string[] = [];
  const re = /import\s+(type\s+)?\{([^}]*)\}\s*from\s*(["'][^"']*["'])/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(content)) !== null) {
    const isTypeOnly = Boolean(m[1]);
    const spec = m[3];
    if (isTypeOnly) continue;
    if (/\/(services|api)\//.test(spec) || /^["']@?\/?(?:\.\.\/)*services\//.test(spec)) {
      out.push(`值导入 ${spec}`);
    }
  }
  return out;
}

describe('state-boundary — zustand store 禁载服务端数据 (W213/C195, §十)', () => {
  it('serverDataNotInZustandStores', () => {
    const files: string[] = [];
    walk(STORES_DIR, files);
    expect(
      files.length,
      `未扫描到任何 store 文件 — 检查 STORES_DIR=${STORES_DIR}（目录迁移须同步本门禁）`,
    ).toBeGreaterThan(0);

    const newViolations: string[] = [];
    for (const f of files) {
      const rel = relative(SRC_ROOT, f).split(sep).join('/');
      if (rel.includes('__tests__') || rel.endsWith('.test.ts') || rel.endsWith('.spec.ts')) continue;
      if (BASELINE.has(rel)) continue;
      const content = readFileSync(f, 'utf8');
      const lines = content.split('\n');
      for (let i = 0; i < lines.length; i++) {
        if (isComment(lines[i])) continue;
        if (HTTP_CALL_RE.test(lines[i])) {
          newViolations.push(`${rel}:${i + 1} — store 内直接 HTTP 调用：${lines[i].trim().slice(0, 100)}`);
        }
      }
      for (const v of findServiceValueImports(content)) {
        newViolations.push(`${rel} — store ${v}（服务端数据请走 React Query/页面 hook）`);
      }
    }

    expect(
      newViolations,
      newViolations.length
        ? `新增 store 服务端态违规（基线 3 文件登记随 W213 渐进收口，新增即红）：\n${newViolations.join('\n')}`
        : undefined,
    ).toEqual([]);
  });
});
