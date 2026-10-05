// @vitest-environment node
import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { readdirSync, statSync } from "node:fs";
import path from "node:path";

/**
 * 分册09 前端消费侧 · 场景年度经营预测 API 单通道护栏。
 *
 * <p>承载 §2.2/§5.1 消费侧契约：P07/P08/P09 与口径 Banner 消费 workspace :18090
 * 19 端点<b>一律经 {@code services/workspaceForecastApi.ts}，禁止各页面 raw fetch
 * {@code /api/v1/workspace/*}</b>（同 C181 gitService 单通道纪律）。本护栏离线可跑
 * （纯文件扫描，不联网、不触库），把"契约唯一入口 + 19 端点全覆盖"锁成 fail-loud。</p>
 */
const SRC = path.resolve(__dirname, "..");
const CLIENT = path.join(SRC, "services/workspaceForecastApi.ts");

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

describe("workspaceForecastApi 单通道 (分册09 消费侧)：/api/v1/workspace/* 必须在 client 内单源", () => {
  it("client 文件存在且是 /api/v1/workspace/{calibers|forecast-runs|backtests|periods}/* 的唯一直接引用点", () => {
    expect(readFileSync(CLIENT, "utf8").length).toBeGreaterThan(0);
    const violates: string[] = [];
    // 仅锁本册 4 个业务子域下的直接 URL 使用（`/api/v1/workspace/scenarios/*` 属卷 07/08，与本册无关）
    const re = /["'`]\/api\/v1\/workspace\/(calibers|forecast-runs|backtests|periods)(\/[A-Za-z0-9_\-{}?&=]+)?/;
    for (const f of files) {
      if (path.resolve(f) === CLIENT) continue;
      readFileSync(f, "utf8").split(/\r?\n/).forEach((line, i) => {
        const trimmed = line.trim();
        if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return;
        if (re.test(line)) violates.push(`${path.relative(SRC, f)}:${i + 1}`);
      });
    }
    expect(violates).toEqual([]);
  });

  it("19 端点 operationId 全覆盖（calibers 7 + forecast-runs 9 + backtests 1 + periods 1 + actions 1）", () => {
    const src = readFileSync(CLIENT, "utf8");
    const expected = [
      // calibers 7 (R-60②)
      "listCalibers", "getCaliber", "createCaliber", "submitCaliber",
      "approveCaliber", "supersedeCaliber", "validateCaliber",
      // forecast-runs 9 (C209 六要素 / C210 只读)
      "createForecastRun", "getForecastRun", "queryForecastResults",
      "traceForecastEvidence", "exportForecastRun", "copyForecastScenario",
      "compareForecastRuns", "getForecastAuditPack", "retryForecastRun",
      // backtests 1 + preview helper
      "listForecastBacktests", "previewBacktestMetrics",
      // periods 1
      "closePeriod",
      // actions 1 (FC-04 五必填 C206)
      "createFcAction",
    ];
    const missing = expected.filter((op) => !new RegExp(`export\\s+async\\s+function\\s+${op}\\b`).test(src));
    expect(missing).toEqual([]);
    expect(expected.length).toBeGreaterThanOrEqual(19);
  });

  it("金额必以字符串透传（禁 double，铁律 §0.6 / C204 / DB-04 精度）", () => {
    const src = readFileSync(CLIENT, "utf8");
    // 三值 + 回测指标 + 期望影响一律 string 字段，不得出现 amount*: number
    const badNumberField = /amountP\d+:\s*number|mae:\s*number|mape:\s*number|expectedImpact:\s*number/;
    expect(badNumberField.test(src)).toBe(false);
  });
});
