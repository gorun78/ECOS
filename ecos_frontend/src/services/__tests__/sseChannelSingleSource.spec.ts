/**
 * SSE 承流通道单源静态门 —— D-B5-11（前端第二实现面）收口闸。
 *
 * 两条判据（只认代码面，不认注释里的词，以免 prose 造成误报或漏报）：
 * - 承流面 = 出现 `.getReader(` 或 `for await (... of x.body`，即本文件自己在读响应流。
 *   承流面必须 (a) 从 `…/sseParser` 引入线路级单源，(b) 体内不得残留手切形态。
 * - 全域禁 `/** ^data: …` 形式的 SSE 行正则 —— 它是「逐行手工匹配 data」的指纹，
 *   非承流面同样不许出现（否则新增通道可先用别的方式取流再手切，绕开上一条）。
 *
 * 手切形态的失效面不是报错而是静默失真：CRLF 流一个事件都派发不出来、断包 token 少一截、
 * 多行 data 被当两件事件 —— 界面上只表现为「少一句话」。
 *
 * 两道形状自检：缺陷原形必须变红（fixture 注入），收口形状不得误报（真实 src 全绿）。
 * @license Apache-2.0
 */
// @vitest-environment node
import { describe, it, expect } from "vitest";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join, posix, sep } from "node:path";

const SRC_ROOT = fileURLToPath(new URL("../../", import.meta.url));

/** 承流面特征：出现任一即视为「本文件自己在读响应流」 */
const STREAM_FACE_PATTERNS: RegExp[] = [
  /\.getReader\s*\(/,
  /for\s+await\s*\([^)]*of\s+[\w.]*\.body\b/,
];

/** 承流面内禁止的手切形态 */
const HANDCUT_PATTERNS: { readonly name: string; readonly re: RegExp }[] = [
  { name: "空行块切 \\n\\n", re: /(?:indexOf|lastIndexOf|split)\(\s*(['"])\\n\\n\1\s*\)/ },
  { name: "逐行切分 split('\\n')", re: /split\(\s*(['"])\\n\1\s*\)/ },
];

/** 全域指纹：逐行匹配 data 的正则字面量 */
const DATA_LINE_REGEX = /\/\^data:/;

/** 单源引入判定只看 import 路径尾段，禁被注释里的文件名糊过去 */
const PARSER_IMPORT = /from\s+['"][^'"]*\/sseParser['"]/;

/**
 * 剥注释、留字符串（一趟扫描，`//` 出现在字符串里不算注释起点）。
 * 判据只认代码面：文档注释里描述缺陷原形（`indexOf('\n\n')`）不该把闸弄红，
 * 反过来注释里写「已收口」也不能把真实的闸糊绿。
 */
function codeOnly(source: string): string {
  let out = "";
  let i = 0;
  const n = source.length;
  while (i < n) {
    const c = source[i];
    const d = source[i + 1];
    if (c === "/" && d === "/") {
      while (i < n && source[i] !== "\n") i += 1;
      continue;
    }
    if (c === "/" && d === "*") {
      i += 2;
      while (i < n && !(source[i] === "*" && source[i + 1] === "/")) i += 1;
      i += 2;
      continue;
    }
    if (c === '"' || c === "'" || c === "`") {
      out += c;
      i += 1;
      while (i < n) {
        if (source[i] === "\\") {
          out += source[i] + (source[i + 1] ?? "");
          i += 2;
          continue;
        }
        out += source[i];
        if (source[i] === c) {
          i += 1;
          break;
        }
        i += 1;
      }
      continue;
    }
    out += c;
    i += 1;
  }
  return out;
}

function walk(dir: string): string[] {
  const out: string[] = [];
  for (const name of readdirSync(dir)) {
    const abs = join(dir, name);
    const st = statSync(abs);
    if (st.isDirectory()) {
      // 用例与快照产物不是生产承流面
      if (name === "__tests__" || name === "__snapshots__") continue;
      out.push(...walk(abs));
      continue;
    }
    if (!/\.tsx?$/.test(name) || /\.(test|spec)\.tsx?$/.test(name)) continue;
    out.push(abs);
  }
  return out;
}

const SRC_ROOT_NORM = SRC_ROOT.split(sep).join(posix.sep).replace(/\/+$/, "");

function rel(abs: string): string {
  return abs.split(sep).join(posix.sep).slice(SRC_ROOT_NORM.length + 1);
}

/** 单文件判据（纯函数：真实树与 fixture 树共用同一份，杜绝两套规则漂移） */
function faceVerdict(path: string, source: string): string[] {
  const code = codeOnly(source);
  const violations: string[] = [];
  if (DATA_LINE_REGEX.test(code)) {
    violations.push(`${path}: 出现 SSE 逐行匹配正则 /**^data:…`);
  }
  const isStreamFace = STREAM_FACE_PATTERNS.some((re) => re.test(code));
  if (!isStreamFace) return violations;
  if (!PARSER_IMPORT.test(code)) {
    violations.push(`${path}: 承流面未引用线路级单源 sseParser`);
  }
  for (const { name, re } of HANDCUT_PATTERNS) {
    if (re.test(code)) violations.push(`${path}: 残留手切形态（${name}）`);
  }
  return violations;
}

/** 廉价前置过滤：整棵树里可能违例的文件极少，只有含承流指纹的文件才进判据 */
function worthJudging(source: string): boolean {
  return (
    source.includes("getReader") || source.includes("for await") || source.includes("^data:")
  );
}

function candidates(): { path: string; src: string }[] {
  return walk(SRC_ROOT)
    .map((abs) => ({ path: rel(abs), src: readFileSync(abs, "utf8") }))
    .filter((f) => worthJudging(f.src));
}

function scanSrc(): string[] {
  const violations: string[] = [];
  for (const f of candidates()) {
    violations.push(...faceVerdict(f.path, f.src));
  }
  return violations;
}

describe("SSE 承流通道单源（D-B5-11）", () => {
  it("生产承流面全部走 sseParser 单源且无手切残留", () => {
    const violations = scanSrc();
    expect(violations).toEqual([]);
  });

  it("缺陷原形必须变红（收口前的三处手切形态各自都被抓到）", () => {
    const bad = faceVerdict(
      "pages/demo/leak.ts",
      [
        "const reader = res.body!.getReader();",
        "while ((sep = buffer.indexOf('\\n\\n')) >= 0) {",
        "  const lines = raw.split('\\n');",
        "  const m = line.match(/^data:\\s*(.+)$/);",
        "}",
      ].join("\n"),
    );
    expect(bad).toHaveLength(4);
    expect(bad.join("\n")).toContain("未引用线路级单源");
    expect(bad.some((v) => v.includes("空行块切"))).toBe(true);
    expect(bad.some((v) => v.includes("逐行切分"))).toBe(true);
    expect(bad.some((v) => v.includes("逐行匹配正则"))).toBe(true);
  });

  it("收口形状不得误报（引单源 + 无手切）", () => {
    const good = faceVerdict(
      "pages/demo/ok.ts",
      [
        "import { createSseParser } from '../../services/sseParser';",
        "const reader = res.body!.getReader();",
        "const parser = createSseParser();",
        "for (const evt of parser.push(chunk)) dispatch(evt);",
      ].join("\n"),
    );
    expect(good).toEqual([]);
  });

  it("非承流面不得被牵连（markdown 正文分段不是 SSE 手切）", () => {
    const markdown = faceVerdict(
      "components/demo/Body.tsx",
      "const title = msg.content.split('\\n\\n')[0];\nreturn <p>{title}</p>;",
    );
    expect(markdown).toEqual([]);
  });

  it("换一种取流方式也绕不过门（for await 读 body 同样算承流面）", () => {
    const alt = faceVerdict(
      "services/demo/alt.ts",
      "for await (const chunk of res.body) { buffer += chunk; }",
    );
    expect(alt).toHaveLength(1);
    expect(alt[0]).toContain("未引用线路级单源");
  });

  it("注释既糊不住门、也不把闸弄红（判据只认代码面）", () => {
    const sneak = faceVerdict(
      "pages/demo/sneak.ts",
      [
        "// 已收口到 sseParser，无手切",
        "import { createSseParser } from '../../services/sseParser';",
        "const reader = res.body!.getReader();",
        "const parts = buffer.split('\\n\\n');",
      ].join("\n"),
    );
    expect(sneak).toHaveLength(1);
    expect(sneak[0]).toContain("空行块切");

    const prose = faceVerdict(
      "pages/demo/prose.ts",
      [
        "/** 历史上此处用过 buffer.indexOf('\\n\\n') 与 /^data:/ 逐行手切，现已收口 */",
        "import { createSseParser } from '../../services/sseParser';",
        "const reader = res.body!.getReader();",
        "for (const evt of parser.push(chunk)) dispatch(evt);",
      ].join("\n"),
    );
    expect(prose).toEqual([]);
  });

  it("承流面清单实测非空（防空判据：真实树里确有承流文件被扫到）", () => {
    const faces = candidates()
      .filter((f) => STREAM_FACE_PATTERNS.some((re) => re.test(codeOnly(f.src))))
      .map((f) => f.path);
    expect(faces.length).toBeGreaterThanOrEqual(4);
    expect(faces).toContain("services/agentLoopStream.ts");
    // W 通道 run 流是第四个承流面（#143/B5-5b-2）：新增通道必须显式在册，不许只做「数量够」的弱断言
    expect(faces).toContain("services/wagentRunStream.ts");
    expect(faces).toContain("pages/aiworkbench/api.ts");
    expect(faces).toContain("pages/knowledge/services/govern.ts");
  });
});
