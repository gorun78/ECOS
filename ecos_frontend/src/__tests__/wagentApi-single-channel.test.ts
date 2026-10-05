// @vitest-environment node
import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { readdirSync, statSync } from "node:fs";
import path from "node:path";

/**
 * 分册10 前端消费侧 · W Agent 编排 API 单通道护栏。
 *
 * <p>承载 §5.2 24 端点消费侧契约：P01/P03/P04/P05/P10/P11 与 P06/认知结果页
 * 消费 ai-engine :18084 `/api/v1/wagent/*` <b>一律经
 * {@code services/wagentApi.ts}</b>，禁止各页面 raw fetch
 * {@code /api/v1/wagent/*}（同 C181 gitService / C174 API 单通道纪律）。
 * 本护栏离线可跑（纯文件扫描，不联网、不触库），
 * 把"契约唯一入口 + 24 端点全覆盖 + 金额/比率字符串透传"锁成 fail-loud。</p>
 */
const SRC = path.resolve(__dirname, "..");
const CLIENT = path.join(SRC, "services/wagentApi.ts");

function walk(dir: string, out: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    if (entry === "node_modules" || entry.startsWith(".")) continue;
    const full = path.join(dir, entry);
    const st = statSync(full);
    if (st.isDirectory()) walk(full, out);
    else if (/\.tsx?$/.test(entry)) out.push(full);
  }
  return out;
}

const files = walk(SRC);

describe("wagentApi 单通道 (分册10 消费侧)：/api/v1/wagent/* 必须在 client 内单源", () => {
  it("client 文件存在且是 /api/v1/wagent/{goals|questions|runs|flags|readiness|tools|candidates|claims|evidence|decisions|actions} 的唯一直接引用点", () => {
    expect(readFileSync(CLIENT, "utf8").length).toBeGreaterThan(0);
    const violates: string[] = [];
    // vite.config.ts 的 proxy 映射是 dev 配置，不是消费侧 fetch 端点引用；单独豁免
    const viteConfig = path.resolve(__dirname, "../vite.config.ts");
    const re =
      /["'`]\/api\/v1\/wagent\/(goals|questions|runs|flags|readiness|tools|candidates|claims|evidence|decisions|actions)(\/[A-Za-z0-9_\-{}?&=]+)?/;
    for (const f of files) {
      if (path.resolve(f) === CLIENT || path.resolve(f) === viteConfig) continue;
      readFileSync(f, "utf8").split(/\r?\n/).forEach((line, i) => {
        const trimmed = line.trim();
        if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return;
        if (re.test(line)) violates.push(`${path.relative(SRC, f)}:${i + 1}`);
      });
    }
    expect(violates).toEqual([]);
  });

  it("24 端点消费函数全覆盖（§5.2 operationId 24 条）", () => {
    const src = readFileSync(CLIENT, "utf8");
    const expected = [
      // goals 2
      "createGoal", "reviseGoal",
      // questions 2
      "createQuestion", "getQuestion",
      // runs 6（startRun / getRun / streamRunEvents / submitRunInput / approveRunGate / cancelRun）
      "startRun", "getRun", "streamRunEvents", "submitRunInput", "approveRunGate", "cancelRun",
      // kill switch 1
      "setKillSwitch",
      // readiness 2
      "getReadiness", "refillReadiness",
      // tools 3
      "searchTools", "describeTool", "validateToolContract",
      // candidates 4
      "listCandidates", "getCandidate", "reviewCandidate", "publishCandidate",
      // claims/evidence 2
      "listClaims", "traceEvidence",
      // decisions 1
      "recordDecision",
      // actions 2 (draftAction / commitAction)
      "draftAction", "commitAction",
    ];
    // §5.2 首批 24 端点；其中 #24 为 draftAction/commitAction 双端点，共 25 个 operationId / 消费函数
    expect(expected.length).toBe(25);
    const missing = expected.filter(
      (op) => !new RegExp(`export\\s+async\\s+function\\s+${op}\\b`).test(src)
    );
    expect(missing).toEqual([]);
  });

  it("金额/比率/概率/置信度字段必须以 string 透传（禁 number，铁律 §0.6 / C204 / NUMERIC 精度）", () => {
    const src = readFileSync(CLIENT, "utf8");
    // NUMERIC(18,6) numeric_value · NUMERIC(5,4) confidence/probability · NUMERIC(18,2) 金额
    // · NUMERIC(9,6) risk_delta · NUMERIC(5,4) cover_ratio —— 一律 string 透传
    const badNumberField =
      /numericValue:\s*number|numeric_value:\s*number|confidence:\s*number|probability:\s*number|coverRatio:\s*number|profitDelta:\s*number|revenueDelta:\s*number|costDelta:\s*number|cashImpact:\s*number|riskDelta:\s*number|headcountImpact:\s*number/;
    expect(badNumberField.test(src)).toBe(false);
  });
});
