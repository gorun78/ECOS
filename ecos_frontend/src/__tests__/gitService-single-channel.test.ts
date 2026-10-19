/**
 * gitService-single-channel.test.ts — 冻结门禁名 `noDirectGitEndpointCallOutsideGitService`
 * （详设-08 §九 W199/C181，前端开发规范 §十一：Git 面单通道 services/gitService.ts，
 *  ADR-12；铁律 #6）
 *
 * 口径：`/api/v1/ecos/git/*` 端点只允许 `services/gitService.ts` 直接调用，
 * 且只传 repositoryId（不传本地路径/凭据——§十一 契约，另见交付报告勘察项）。
 * 本门禁扫描 src 代码行（排除注释/测试/.d.ts）中出现该端点族的文件：
 *   - services/gitService.ts 为唯一正主（通道本体，不计违规）；
 *   - 其余出现即违规，存量登记见下 BASELINE。
 * 注：`/api/v1/engine/ontology/git/*` 属引擎面端点，非本通道管辖（services/ontology/
 * ontologyEngineApi.ts 有注释说明），不判违规。
 *
 * 判据形态（#143/B5-19，收口台账 D-B5-40）**：本门原判据 `\/api\/v1\/ecos\/git\b` 只认
 * 裸字面量形态。C1 基址单源把 `services/gitService.ts` 全部端点改写为
 * `` `${API_BASE}/v1/ecos/git/…` `` 派生态后（#143/B5-18），原判据在**整个 src 内已无任何
 * 可命中对象** ⇒ 门恒绿、基线成死豁免、新文件再私拼 git 端点也不会红。**本批实跑读出**
 * （`grep -rn "ecos/git" src --include=*.ts(x)`，排除 `__tests__`/`*.test.*`/`*.spec.*`）：
 * 命中 40 行 = 注释行 29（被 isComment 剔除）＋ 代码行 11（11 枚全在 `services/gitService.ts`
 * 的 `${API_BASE}/v1/ecos/git` 派生态），裸引号形态代码行 **0** ⇒ 旧判据命中面为空。
 * 判据遂改为同时认两种形态：
 *   `["'`]/api/v1/ecos/git`（裸字面量）与 `${API_BASE}/v1/ecos/git`（派生态）。
 * 双向自校验 leg 把这条教训钉成可跑的证据：派生态 DEFECT 行必须被新判据命中，
 * 而旧判据对它零命中 —— 防止后人把判据"简化"回只认引号形态。
 *
 * 基线白名单（存量登记，属主 W199/B 批任务 #119 收敛为经 gitService 调用；
 * R-55②/H6-T5 口径清单只减不增，新增即红）：
 *   ~~components/data-workbench/GitPanel.tsx:206/:209~~ ⇒ **#143/B5-19 实测归零**：
 *   GitPanel 早已改经 gitService（该文件 :208 现只剩一条"本文件不再拼字符串"的说明注释），
 *   留着 = 「基线预算豁免一个已不存在的违例」= 未来回归的藏身处，故剪空并加
 *   `基线必须停在空集` 自检 leg（与 privateTransportRatchet 收口批同款口径）。
 */
// @vitest-environment node
import { describe, it, expect, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));

const CANONICAL = 'services/gitService.ts';

/**
 * #143/B5-19 归零：基线必须停在空集。
 * 唯一登记项 `components/data-workbench/GitPanel.tsx` 的违例早已改经 gitService，
 * 该文件现在只在注释里提到端点（被 isComment 剔除）⇒ 豁免位保护的是一个不存在的违例。
 * 新增条目必须与「该文件真实存在非注释命中」同批出现，且要说明为什么不改走单通道。
 */
const BASELINE: ReadonlySet<string> = new Set([]);

/** 双形态判据：裸字面量 `"/api/v1/ecos/git…"` ∨ 派生态 `${API_BASE}/v1/ecos/git…`。 */
const GIT_ENDPOINT_RE = /(?:["'`]\/api\/v1\/ecos\/git|\$\{API_BASE\}\/v1\/ecos\/git)/;

/** 已作废的旧判据（只认裸字面量），仅用于自校验 leg 证明判据扩面真实生效。 */
const LEGACY_RAW_ONLY_RE = /\/api\/v1\/ecos\/git\b/;

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
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///') || t.startsWith('/**');
}

describe('gitService-single-channel — ecos/git 端点单通道 (W199/C181, §十一/ADR-12)', () => {
  it('noDirectGitEndpointCallOutsideGitService', () => {
    const files: string[] = [];
    walk(SRC_ROOT, files);
    const violations: string[] = [];

    for (const f of files) {
      const rel = relative(SRC_ROOT, f).split(sep).join('/');
      if (rel === CANONICAL || isTestFile(rel)) continue;
      const lines = readFileSync(f, 'utf8').split('\n');
      lines.forEach((line, i) => {
        if (isComment(line)) return;
        if (GIT_ENDPOINT_RE.test(line)) {
          if (!BASELINE.has(rel)) {
            violations.push(`${rel}:${i + 1} — ecos/git 端点直调（§十一：请改经 services/gitService 且只传 repositoryId）`);
          }
        }
      });
    }

    expect(
      violations,
      violations.length
        ? `Git 单通道违规 ${violations.length} 处（#143/B5-19 起基线为空集，任何非注释命中即红）：\n${violations.join('\n')}`
        : undefined,
    ).toEqual([]);

    // 通道本体存在性哨兵：gitService.ts 必须仍在位，防止"删通道保绿"。
    const hasCanonical = files.some((f) => relative(SRC_ROOT, f).split(sep).join('/') === CANONICAL);
    expect(hasCanonical, 'services/gitService.ts 单通道本体缺失（不允许移除通道来通过门禁）').toBe(true);
  });

  it('判据双形态自校验：派生态 DEFECT 命中、旧判据对它零命中（D-B5-40 防回归）', () => {
    const DERIVED = '  return apiFetchData(`${API_BASE}/v1/ecos/git/status?q=1`);';
    const RAW = "  const url = '/api/v1/ecos/git/commit';";
    const CLEAN = [
      "import { API_BASE } from './httpClient';",
      '  // 说明：/api/v1/ecos/git 面只经 services/gitService 单通道',
      '  const repoId = selected.id;',
    ];

    expect(GIT_ENDPOINT_RE.test(DERIVED), '新判据必须命中派生态').toBe(true);
    expect(LEGACY_RAW_ONLY_RE.test(DERIVED), '旧判据对派生态必须失配（否则本 leg 无证据力）').toBe(false);
    expect(GIT_ENDPOINT_RE.test(RAW), '新判据必须保留裸字面量形态').toBe(true);
    for (const line of CLEAN) {
      expect(GIT_ENDPOINT_RE.test(line), `CLEAN 行不得命中：${line}`).toBe(false);
    }
  });

  it('基线自身可校验：停在空集，且键形态合规', () => {
    expect(BASELINE.size, '#143/B5-19 收口后基线必须为空集；新增豁免须与该文件真实非注释命中同批').toBe(0);
    for (const rel of BASELINE) {
      expect(rel.startsWith('./')).toBe(false);
      expect(rel.includes('\\')).toBe(false);
      expect(isTestFile(rel)).toBe(false);
      expect(rel).not.toBe(CANONICAL);
    }
  });
});
