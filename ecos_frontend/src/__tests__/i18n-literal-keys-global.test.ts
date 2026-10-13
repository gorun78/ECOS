// @vitest-environment node
/**
 * i18n 全 src 字面量键存在性门禁（提交面零裸键）
 *
 * 背景：wave4 合并后全仓静态扫描发现 2 枚提交面裸键（workspace.l2.recent.go_decisions /
 * common.status.demo_badge），既有 i18n 门禁只覆盖认知域局部页面，所以残留无人拦截。
 * 本门禁把面收到整个 src/，断言"提交面"每个字面量 t("ns.key") 都能在合并语料里解析出来。
 *
 * 口径：只扫「已提交且无本地改动」的文件。git status 里为 M/M（含未跟踪 ??）的文件一律豁免
 * ——不变式是「提交面零裸键」，不是「工作树零裸键」；把并行会话的在途文件计入会把它们的
 * 瞬时状态写进本门禁的通过与否。
 *
 * 已知残留方向（可接受）：某干净源文件引用一个只存在于脏 locale 文件里的键 ⇒ 假绿；
 * 方向是放行而非误拦，源文件一旦提交而词条未提交，下一次门禁运行（该文件变干净）即拦截。
 *
 * 依赖 git 可执行（本仓测试面首处 child_process 用法，故显式声明：取不到 toplevel/status
 * 时抛错让用例失败，绝不静默放行）。
 * @license Apache-2.0
 */
import { describe, it, expect } from 'vitest';
import { execFileSync } from 'node:child_process';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const SRC = fileURLToPath(new URL('..', import.meta.url)); // src/

/** 合并全部 namespace 语料（与 LanguageContext 的合并注册同构）。 */
function loadCorpus(file: 'zh-CN.json' | 'en.json'): Set<string> {
  const keys = new Set<string>();
  const locales = path.join(SRC, 'locales');
  for (const ns of readdirSync(locales)) {
    const dir = path.join(locales, ns);
    if (!statSync(dir).isDirectory()) continue;
    try {
      const obj = JSON.parse(readFileSync(path.join(dir, file), 'utf8')) as Record<string, unknown>;
      for (const k of Object.keys(obj)) keys.add(k);
    } catch {
      // namespace without this locale file — skip
    }
  }
  return keys;
}

function git(cwd: string, args: string[]): string {
  try {
    return execFileSync('git', args, { cwd, encoding: 'utf8' });
  } catch (err) {
    throw new Error(
      `i18n-literal-keys-global: git ${args.join(' ')} 执行失败，无法界定提交面，拒绝放行：${String(err)}`,
    );
  }
}

/** 未提交（含未跟踪）文件的绝对路径集合，斜杠统一为 /。 */
function uncommittedPaths(): Set<string> {
  const repo = git(SRC, ['rev-parse', '--show-toplevel']).trim().replace(/\\/g, '/');
  const out = git(SRC, ['status', '--porcelain', '-uall']);
  const set = new Set<string>();
  for (const line of out.split('\n')) {
    if (!line) continue;
    let p = line.slice(3);
    if (p.includes(' -> ')) p = p.split(' -> ').pop() ?? p;
    p = p.replace(/^"|"$/g, '').replace(/\\/g, '/');
    set.add(path.posix.normalize(`${repo}/${p}`));
  }
  return set;
}

/** 递归收集 src 下待扫描源文件（排除测试、__tests__、locales、node_modules）。 */
function listSourceFiles(dir: string, out: string[] = []): string[] {
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (e.name === '__tests__' || e.name === 'node_modules' || e.name === 'locales') continue;
      listSourceFiles(p, out);
    } else if (/\.tsx?$/.test(e.name) && !/\.(test|spec)\./.test(e.name)) {
      out.push(p);
    }
  }
  return out;
}

// 字面量键：t("ns.key") / t('ns.key')，后随 , 或 )。
// (?<![\w$]) 词边界防止把 split("x") / emit("click") 之类误判为 t() 调用。
const literalKeyRe = /(?<![\w$])t\(\s*(['"])([a-zA-Z0-9_.]+)\1\s*[,)]/g;

describe('i18n 全 src 字面量键存在性（提交面零裸键）', () => {
  const zh = loadCorpus('zh-CN.json');
  const en = loadCorpus('en.json');
  const dirty = uncommittedPaths();
  const committed = listSourceFiles(SRC).filter(
    (f) => !dirty.has(path.resolve(f).replace(/\\/g, '/')),
  );

  /** 提交面用到的字面量键 → 使用处相对路径（去重）。 */
  function usedKeys(): Map<string, string[]> {
    const used = new Map<string, string[]>();
    for (const file of committed) {
      const rel = path.relative(SRC, file).replace(/\\/g, '/');
      const txt = readFileSync(file, 'utf8');
      let m: RegExpExecArray | null;
      literalKeyRe.lastIndex = 0;
      while ((m = literalKeyRe.exec(txt))) {
        const at = used.get(m[2]) ?? [];
        if (!at.includes(rel)) at.push(rel);
        used.set(m[2], at);
      }
    }
    return used;
  }
  const used = usedKeys();

  it('合并语料非空', () => {
    expect(zh.size).toBeGreaterThan(0);
    expect(en.size).toBeGreaterThan(0);
  });

  it('提交面扫描集非空（门禁面不得静默缩面）', () => {
    expect(committed.length).toBeGreaterThan(100);
    expect(used.size).toBeGreaterThan(100);
  });

  it('提交面每个字面量 t() 键都有中英成对词条', () => {
    const gaps: string[] = [];
    for (const [key, files] of used) {
      const inZh = zh.has(key);
      const inEn = en.has(key);
      if (inZh && inEn) continue;
      const which =
        !inZh && !inEn
          ? 'zh+en 均缺失（界面直接渲染键名）'
          : !inZh
            ? '仅缺 zh-CN（中文界面回落英文）'
            : '仅缺 en（英文界面渲染键名）';
      gaps.push(`${key} — ${which} @ ${files.join(', ')}`);
    }
    expect(gaps, `提交面 i18n 词条缺口：\n${gaps.join('\n')}`).toEqual([]);
  });

  it('wave4 收尾补齐的 2 枚键已成对在册（防回落）', () => {
    for (const key of ['workspace.l2.recent.go_decisions', 'common.status.demo_badge']) {
      expect(zh.has(key), `zh-CN 缺 ${key}`).toBe(true);
      expect(en.has(key), `en 缺 ${key}`).toBe(true);
    }
  });
});
