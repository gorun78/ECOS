/**
 * 场景认知路径单源防回归（design-05 F05-17 验收 / B.9-1、B.9-2）。
 *
 * 验收命令：`npx vitest run src/api/__tests__/cognitivePaths.singleSource.spec.ts`
 *
 * 约束：前端所有场景认知路径必须由唯一常量模块
 * `services/cognitiveClient.ts`（`SCENARIO_COGNITION_BASE` +
 * `scenarioCognitionUrl()`）派生，形状对齐后端模板
 * `CognitiveApiPaths.SCENARIO_COGNITION_TEMPLATE = "/api/v1/business/scenarios/%s/cognition"`；
 * 其他模块出现 `/api/v1/business/scenarios` 路径字面量、或 cognition Tab
 * 组件自行 `fetch(` / 硬编码 `/cognition` 子路径，均判失败。
 *
 * 环境：node —— 本用例只做 fs-walk + 正则断言，不需要 DOM。沿用仓内既有约定
 * （`src/__tests__/file-size-lint.test.ts` 亦声明 `@vitest-environment node`，
 * 理由是该文件只跑 Node 契约检查）。附带规避：本机 node 20.18.1 下 jsdom 依赖链
 * 存在 ERR_REQUIRE_ESM（`html-encoding-sniffer` require ESM-only 的 `@exodus/bytes`），
 * node 环境的用例不受该环境影响。
 */
// @vitest-environment node
import { describe, it, expect } from "vitest";
import * as fs from "node:fs";
import * as path from "node:path";
import {
  SCENARIO_COGNITION_BASE,
  scenarioCognitionUrl,
  COGNITIVE_ASSET_CATALOG_PATH,
  cognitiveAssetResolvePath,
  cognitiveAssetProposalsPath,
  cognitiveAssetTransitionPath,
  CONTEXT_BUILD_PATH,
} from "../../services/cognitiveClient";

const SRC_ROOT = path.resolve(__dirname, "..", "..");

/** 后端模板：/api/v1/business/scenarios/{id}/cognition[/detect|/operation-eval|/hypotheses|/beliefs]（可带 query） */
const BACKEND_TEMPLATE_RE =
  /^\/api\/v1\/business\/scenarios\/[^/?]+\/cognition(\/(detect|operation-eval|hypotheses|beliefs))?(\?.*)?$/;

function walk(dir: string, out: string[] = []): string[] {
  for (const name of fs.readdirSync(dir)) {
    const p = path.join(dir, name);
    const st = fs.statSync(p);
    if (st.isDirectory()) {
      if (name === "__tests__" || name === "node_modules") continue;
      walk(p, out);
    } else if (/\.(ts|tsx)$/.test(name)) out.push(p);
  }
  return out;
}

function stripComments(src: string): string {
  return src.replace(/\/\*[\s\S]*?\*\//g, " ").replace(/\/\/[^\n]*/g, " ");
}

describe("场景认知路径单源（cognitiveClient.ts 唯一出口）", () => {
  it("scenarioCognitionUrl 与后端 CognitiveApiPaths 模板逐字对齐", () => {
    expect(SCENARIO_COGNITION_BASE).toBe("/api/v1/business/scenarios");
    expect(scenarioCognitionUrl("s1")).toBe("/api/v1/business/scenarios/s1/cognition");
    for (const suffix of ["", "/detect", "/operation-eval", "/hypotheses", "/beliefs"] as const) {
      const url = scenarioCognitionUrl("sc-1", suffix);
      expect(url, `模板不匹配: ${url}`).toMatch(BACKEND_TEMPLATE_RE);
    }
    // scenarioId 必须 URL 编码（路径注入防呆）
    expect(scenarioCognitionUrl("a b/../c")).toBe(
      "/api/v1/business/scenarios/a%20b%2F..%2Fc/cognition",
    );
  });

  it("全 src 内 `/api/v1/business/scenarios` 路径字面量只允许出现在 services/cognitiveClient.ts", () => {
    const offenders = walk(SRC_ROOT)
      .filter((f) => {
        const code = stripComments(fs.readFileSync(f, "utf8"));
        return /["'`]\/api\/v1\/business\/scenarios/.test(code);
      })
      .map((f) => path.relative(SRC_ROOT, f).replace(/\\/g, "/"));
    expect(
      offenders,
      `路径字面量应单源于 cognitiveClient.ts，实际还出现在: ${offenders.join(", ")}`,
    ).toEqual(["services/cognitiveClient.ts"]);
    // 全 src 同步读在并行 worker + 本机负载下可超 vitest 默认 5s ⇒ 显式放宽（与 token 门禁同口径）
  }, 30_000);

  it("右栏四 Tab 组件不自行 fetch/硬编码 cognition 路径，数据入口一律经 cognitiveClient 单源", () => {
    const cognitionDir = path.join(SRC_ROOT, "pages", "scenario", "cognition");
    const files = [
      "CognitiveRightPanel.tsx",
      "DetectTab.tsx",
      "OperationEvalTab.tsx",
      "HypothesesTab.tsx",
      "BeliefsTab.tsx",
    ];
    // DetectTab 无独立请求（消费 shell 经 props 传入的 cognitiveClient 结果），不强制 import
    const mustImportClient = new Set([
      "CognitiveRightPanel.tsx",
      "OperationEvalTab.tsx",
      "HypothesesTab.tsx",
      "BeliefsTab.tsx",
    ]);
    for (const name of files) {
      const code = stripComments(fs.readFileSync(path.join(cognitionDir, name), "utf8"));
      expect(code, `${name} 不得直接调用 fetch（必须经 cognitiveClient）`).not.toMatch(/\bfetch\(/);
      expect(code, `${name} 不得硬编码 cognition 子路径`).not.toMatch(/["'`]\/cognition/);
      if (mustImportClient.has(name)) {
        expect(code, `${name} 必须 import services/cognitiveClient`).toContain(
          "services/cognitiveClient",
        );
      }
    }
  });
});

describe("认知资产只读引用路径单源（补充设计 §8、§13 SUP-COG-01/02）", () => {
  it("catalog 常量与 resolve 路径构造逐字对齐后端模板", () => {
    expect(COGNITIVE_ASSET_CATALOG_PATH).toBe("/api/v1/cognitive/assets/catalog");
    // assetId 必须 URL 编码（parseRef 前缀 bel/hyp/mod，冒号需转义）
    expect(cognitiveAssetResolvePath("bel:abc")).toBe(
      "/api/v1/cognitive/assets/bel%3Aabc/resolve",
    );
    expect(cognitiveAssetResolvePath("mod x/../y")).toBe(
      "/api/v1/cognitive/assets/mod%20x%2F..%2Fy/resolve",
    );
    // proposals / transitions（§3.5 候选评审面；对齐 ASSETS_PROPOSALS_REL / ASSETS_TRANSITION_REL）
    expect(cognitiveAssetProposalsPath("bel:abc")).toBe(
      "/api/v1/cognitive/assets/bel%3Aabc/proposals",
    );
    expect(cognitiveAssetTransitionPath("hyp x/../y")).toBe(
      "/api/v1/cognitive/assets/hyp%20x%2F..%2Fy/transitions",
    );
  });

  it("全 src 无第二处硬编码 `/api/v1/cognitive/assets` 字面量（一律经 cognitiveClient 导出常量派生）", () => {
    // cognitiveClient.ts 用 `${COGNITIVE_BASE}/assets/...` 模板构造，仓内不应再出现任何
    // 完整 `/api/v1/cognitive/assets` 字面量（含 __tests__ 目录外）——即无第二处来源。
    const offenders = walk(SRC_ROOT)
      .filter((f) => {
        const code = stripComments(fs.readFileSync(f, "utf8"));
        return /["'`]\/api\/v1\/cognitive\/assets/.test(code);
      })
      .map((f) => path.relative(SRC_ROOT, f).replace(/\\/g, "/"));
    expect(
      offenders,
      `认知资产路径不应有第二处硬编码字面量，实际出现在: ${offenders.join(", ")}`,
    ).toEqual([]);
  }, 30_000);

  it("认知资产面组件不得直接 fetch / 硬编码 assets 子路径，一律经 cognitiveClient 单源", () => {
    const dir = path.join(SRC_ROOT, "pages", "knowledge");
    const files = [
      path.join("pages", "CognitiveAssetPage.tsx"),
      path.join("components", "CognitiveAssetResolveDrawer.tsx"),
      path.join("components", "CandidateReviewPanel.tsx"),
    ];
    for (const rel of files) {
      const code = stripComments(fs.readFileSync(path.join(dir, rel), "utf8"));
      expect(code, `${rel} 不得直接调用 fetch`).not.toMatch(/\bfetch\(/);
      expect(code, `${rel} 不得硬编码 /assets 路径`).not.toMatch(/["'`]\/api\/v1\/cognitive\/assets/);
      expect(code, `${rel} 必须 import services/cognitiveClient`).toContain("services/cognitiveClient");
    }
  });

  it("context/build 路径常量和后端 CognitiveApiPaths.CONTEXT_BUILD 逐字对齐，全 src 无第二处字面量", () => {
    expect(CONTEXT_BUILD_PATH).toBe("/api/v1/cognitive/context/build");
    const offenders = walk(SRC_ROOT)
      .filter((f) => {
        const code = stripComments(fs.readFileSync(f, "utf8"));
        return /["'`]\/api\/v1\/cognitive\/context/.test(code);
      })
      .map((f) => path.relative(SRC_ROOT, f).replace(/\\/g, "/"));
    expect(offenders, `context 路径字面量应无第二处来源: ${offenders.join(", ")}`).toEqual([]);
  }, 30_000);
});
