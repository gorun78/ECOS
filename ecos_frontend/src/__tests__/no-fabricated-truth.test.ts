// @vitest-environment node
import { describe, it, expect } from "vitest";
import { readFileSync, readdirSync, statSync } from "node:fs";
import path from "node:path";

// C186/W204 (M0, 不等裁决直改, 铁律 :14 虚假验收禁令)：
// 前端渲染路径不得出现"随机/手写真值"式的假数据（Q13）。
//
// 判定红线（按语义类别扫描，而非逐点黑名单）：
//   ① Math.floor(Math.random()) ± 相邻行提到 tokensUsed / inputTokens / outputTokens / latencyMs
//      —— 属"伪造计量指标"红线
//   ② buildMockAgentReply 或任何 *Mock*Reply/*Mock*Response* 函数在渲染路径中被 import
//      —— 属"伪造 LLM 回复"红线
//
// 存量登记（R-51 存量资产"只定性、不擅动"）：
//   AgentStudio.tsx:232      tokensUsed + Math.floor(MR * 500 + 200)
//   AgentStudioView.tsx:268  inputTokens = MR*500 + 400
//   useAgentStudio.ts:292    inputTokens = MR*500 + 400
//   AgentStudioView.tsx:278  buildMockAgentReply 调用
//   （agentStudioHelpers.ts 定义本身属于共享工具；其被 import 命中 ②）
// 上述已知 4 点需逐点复核——**放行要求**：文件内自证"仅演示/draft 模式"或等待权限接入
// 后端真接口后（=C187 待复核面同款），并在【校訂】块内登记理由与预计收口时间。
const SRC = path.resolve(__dirname, "..");

function walk(dir: string, out: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    if (entry === "node_modules" || entry.startsWith(".")) continue;
    const full = path.join(dir, entry);
    const st = statSync(full);
    if (st.isDirectory()) walk(full, out);
    else if (/\.tsx?$/.test(entry) && !/\.test\.tsx?$/.test(entry) && !/\.spec\.tsx?$/.test(entry)) out.push(full);
  }
  return out;
}

// 已知存量点（相对 SRC 的路径 + 精确行号，仅在复核放行期间允许存在）
const KNOWN_GRAPH_TOKEN_FAKES: Array<{ file: string; line: number; reason: string }> = [
  { file: "pages/AgentStudio.tsx",            line: 232, reason: "演示/草稿 mode 无后端 token 数据源；收口=F10-21 Kill Switch 切真实 metering" },
  { file: "pages/aiworkbench/AgentStudioView.tsx", line: 268, reason: "演示 mode 无后端 inputTokens 写回；收口=o1 册 evaluate 落地后 换 real metering" },
  { file: "pages/aiworkbench/useAgentStudio.ts",  line: 292, reason: "同上（useAgentStudio 演示路径）" },
];
const KNOWN_MOCK_REPLY_IMPORTERS: Array<{ file: string; reason: string }> = [
  { file: "pages/aiworkbench/AgentStudioView.tsx", reason: "演示 mode 缺 o1 册会话/流式回退" },
];

// 三档假真值红线之外的**其它演示档 Math.random**（不在 token/metrics 域，但同属 Q13 假真值族）。
// 本窗按 R-51 存量"只定性、不擅动"原则登记，等 P-4 会话与 metering 通道齐后按【校訂】统一收口。
const KNOWN_RANDOM_FAKES: Array<{ file: string; line: number; reason: string }> = [
  { file: "components/pipeline/StepSchedule.tsx",         line: 37, reason: "演示 mode 忙闲分布" },
  { file: "pages/aiworkbench/ChatbotStudioView.tsx",       line: 84, reason: "演示 mode RAG 同步 chunks 计数" },
  { file: "pages/aiworkbench/DashboardView.tsx",           line: 184, reason: "演示 mode 分数 60-90" },
  { file: "pages/aiworkbench/DashboardView.tsx",           line: 185, reason: "演示 mode 延迟 200-1000ms" },
  { file: "pages/knowledge/tabs/SyncTab.tsx",              line: 113, reason: "演示 mode 知识 chunks 数" },
];

const files = walk(SRC);

function matchLines(file: string, re: RegExp): Array<{ line: number; text: string }> {
  const src = readFileSync(file, "utf8");
  const out: Array<{ line: number; text: string }> = [];
  src.split(/\r?\n/).forEach((text, i) => {
    if (re.test(text)) out.push({ line: i + 1, text });
  });
  return out;
}

describe("no-fabricated-truth (C186/W204) 渲染路径禁 Math.random token 或 *Mock*Reply import（M0 铁律 :14）", () => {
  it("① 新文件/新行不得出现 seeds×token 维度组合（Math.random + tokens 关键字 邻近）", () => {
    const violations: string[] = [];
    const randRe = /Math\.random\(\)/;
    const tokenRe = /tokensUsed|inputTokens|outputTokens|tokens\s*[:=]/i;
    for (const f of files) {
      const src = readFileSync(f, "utf8");
      const lines = src.split(/\r?\n/);
      const rel = path.relative(SRC, f).replace(/\\/g, "/");
      lines.forEach((text, i) => {
        if (!randRe.test(text)) return;
        // 检查 ±3 行内是否有 tokens 维度
        const window = lines.slice(Math.max(0, i - 3), Math.min(lines.length, i + 4)).join("\n");
        if (!tokenRe.test(window)) return;
        // 已知存量点且行号精确命中 → 跳过（走【校訂】登记）
        const known = KNOWN_GRAPH_TOKEN_FAKES.find(
          (k) => k.file === rel && k.line === i + 1,
        );
        if (known) return;
        violations.push(`${rel}:${i + 1}  [Math.random 邻近 tokens 维度]`);
      });
    }
    expect(violations).toEqual([]);
  });

  it("② 新文件不得 import buildMockAgentReply 或任何 *Mock*Reply 类型 (渲染路径红线)", () => {
    const violations: string[] = [];
    for (const f of files) {
      const src = readFileSync(f, "utf8");
      const rel = path.relative(SRC, f).replace(/\\/g, "/");
      if (rel === "pages/aiworkbench/agent-studio/agentStudioHelpers.ts") continue; // 定义源不进扫描
      const imps = src.match(/import\s+.*\bbuildMockAgentReply\b.*from|import\s+{[^}]*(Mock\w*Reply|Mock\w*Response)[^}]*}/g) || [];
      if (imps.length === 0) continue;
      const known = KNOWN_MOCK_REPLY_IMPORTERS.find((k) => k.file === rel);
      if (known) continue;
      violations.push(`${rel}  [import *Mock*Reply]`);
    }
    expect(violations).toEqual([]);
  });

  it("③ 已知存量清除登记表必须匹配实存（若上游已清某点，本测试的红线也随之收紧）", () => {
    // 断言：每个 KNOWN_* 表里的 file + line 必须实存对应 to-be-removed 结构（否则表陈旧需删除）
    for (const k of KNOWN_GRAPH_TOKEN_FAKES) {
      const abs = path.join(SRC, k.file);
      expect(readFileSync(abs, "utf8"), `允许登记的行仍须实存：${k.file}`).toBeTruthy();
    }
    for (const k of KNOWN_MOCK_REPLY_IMPORTERS) {
      const abs = path.join(SRC, k.file);
      const content = readFileSync(abs, "utf8");
      expect(content).toMatch(/buildMockAgentReply/);
    }
    for (const k of KNOWN_RANDOM_FAKES) {
      const abs = path.join(SRC, k.file);
      const lines = readFileSync(abs, "utf8").split(/\r?\n/);
      const line = k.line - 1;
      if (line >= 0 && line < lines.length) {
        expect(lines[line], `allow-list 行 ${k.file}:${k.line} 已清，需从 KNOWN_RANDOM_FAKES 删除`).toMatch(/Math\.random/);
      }
    }
  });

  it("④ 全仓不得新增'未登 Any 代 Math.floor(Math.random()*N)'伪造模式（允许已登记的所有存量点）", () => {
    const violations: string[] = [];
    for (const f of files) {
      const rel = path.relative(SRC, f).replace(/\\/g, "/");
      const hits = matchLines(f, /Math\.floor\(\s*Math\.random\(\)\s*\*/);
      for (const h of hits) {
        const known =
          KNOWN_GRAPH_TOKEN_FAKES.find((k) => k.file === rel && k.line === h.line) ||
          KNOWN_RANDOM_FAKES.find((k) => k.file === rel && k.line === h.line);
        if (known) continue;
        violations.push(`${rel}:${h.line}`);
      }
    }
    expect(violations).toEqual([]);
  });
});
