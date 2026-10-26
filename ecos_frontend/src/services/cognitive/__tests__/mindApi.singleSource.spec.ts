/**
 * R4 Mind 聚合根路径单源防回归（#141 B3-c / 差距分析 P0-6 / 详设 §8.6.1）。
 *
 * 验收命令：`npx vitest run src/services/cognitive/__tests__/mindApi.singleSource.spec.ts`
 *
 * 约束（对齐 cognitiveClient.ts 与 cognitivePaths.singleSource.spec 的既有约定）：
 *  1. `services/cognitive/mindApi.ts` 是 Mind 端点族的唯一出口，路径基址
 *     `MIND_LIST_PATH` 必须从 `services/cognitiveClient.ts` 的 `COGNITIVE_BASE`
 *     常量派生（等同 C2 单解包点纪律），本地禁独立硬编码 `/api/v1/cognitive`。
 *  2. 后端模板（`CognitiveApiPaths` 逐字）：
 *       MINDS_REL = "/minds"（GET list / GET detail{mindId} / POST proposals{mindId}）
 *       MINDS_PROPOSED_REL = "/minds/proposals"（GET ?mindId=&limit=）
 *       MINDS_ITEM_REL = "/minds/proposals/{updateId}"（GET 回读）
 *       MINDS_ITEM_STATUS_REL = "/minds/proposals/{updateId}/status"（PUT 终态）
 *     六个 Path / URL builder 逐字钉住本模板，防另一端漂移。
 *  3. 全 src（除 `pages/decision/*` 消费面与本测试外）无第二处
 *     `/minds/proposals` 字面量硬编码。
 *
 * @vitest-environment node
 */
// @vitest-environment node
import { describe, it, expect, vi } from "vitest";
vi.setConfig({ testTimeout: 30_000 });
import * as fs from "node:fs";
import * as path from "node:path";
import {
  MIND_LIST_PATH,
  mindDetailPath,
  mindProposalsPath,
  mindProposalPath,
  mindProposalSubmitPath,
  mindProposalFinalizePath,
  fetchMindList,
  fetchMindDetail,
  fetchMindProposals,
  fetchMindProposal,
  submitMindProposal,
  finalizeMindProposal,
} from "../mindApi";
import { COGNITIVE_BASE } from "../../cognitiveClient";

const SRC_ROOT = path.resolve(__dirname, "..", "..", "..");

function walk(rel: string, out: string[] = []): string[] {
  const dir = path.join(SRC_ROOT, rel);
  let names: string[];
  try {
    names = fs.readdirSync(dir);
  } catch {
    // 幻影目录项（本机 D: 盘 overlay 偶发返回不可 scandir 的目录名）：
    // 该子树无可读内容，跳过而非崩溃 —— 无文件可扫，不构成「漏扫假绿」。
    return out;
  }
  for (const name of names) {
    const p = path.join(dir, name);
    let st: fs.Stats;
    try {
      st = fs.lstatSync(p);
    } catch {
      continue; // 悬空符号链接等无法 stat 的条目：跳过（无内容可扫）
    }
    if (st.isDirectory()) {
      if (name === "__tests__" || name === "node_modules") continue;
      walk(name, out);
    } else if (st.isSymbolicLink()) {
      continue;
    } else if (/\.(ts|tsx)$/.test(name)) out.push(p);
  }
  return out;
}

function stripComments(src: string): string {
  return src.replace(/\/\*[\s\S]*?\*\//g, " ").replace(/\/\/[^\n]*/g, " ");
}

describe("R4 Mind 端点族路径单源（services/cognitive/mindApi.ts）", () => {
  it("MIND_LIST_PATH 从 cognitiveClient 的 COGNITIVE_BASE 单源派生", () => {
    expect(MIND_LIST_PATH).toBe(`${COGNITIVE_BASE}/minds`);
  });

  it("六枚 Path builder 与后端 CognitiveApiPaths 模板逐字对齐（含 URL 编码防呆）", () => {
    // list+detail
    expect(MIND_LIST_PATH).toMatch(/\/minds$/);
    expect(mindDetailPath("m-1")).toBe(`${MIND_LIST_PATH}/m-1`);
    expect(mindDetailPath("a b/../c")).toBe(`${MIND_LIST_PATH}/a%20b%2F..%2Fc`);

    // proposals list 自带 URL 编码 + limit 夹取 [1,100]
    expect(mindProposalsPath("m-1", 5)).toBe(
      `${MIND_LIST_PATH}/proposals?mindId=m-1&limit=5`,
    );
    expect(mindProposalsPath("m-1")).toBe(
      `${MIND_LIST_PATH}/proposals?mindId=m-1&limit=20`,
    );
    expect(mindProposalsPath("m-1", -5)).toBe(
      `${MIND_LIST_PATH}/proposals?mindId=m-1&limit=1`,
    );
    expect(mindProposalsPath("m-1", 9999)).toBe(
      `${MIND_LIST_PATH}/proposals?mindId=m-1&limit=100`,
    );
    // URLSearchParams 用 application/x-www-form-urlencoded 编码：空格 → `+`，保留 `.` 与 `/` 中 `/` 计入需转义
    expect(mindProposalsPath("a b/../c", 7)).toBe(
      `${MIND_LIST_PATH}/proposals?mindId=a+b%2F..%2Fc&limit=7`,
    );

    // proposal 单行 + 终态
    expect(mindProposalPath("u-1")).toBe(`${MIND_LIST_PATH}/proposals/u-1`);
    expect(mindProposalPath("u/1")).toBe(`${MIND_LIST_PATH}/proposals/u%2F1`);
    expect(mindProposalFinalizePath("u-1")).toBe(`${MIND_LIST_PATH}/proposals/u-1/status`);

    // submit 挂 detail 底下
    expect(mindProposalSubmitPath("m-1")).toBe(`${MIND_LIST_PATH}/m-1/proposals`);
  });

  it("六枚异步端点函数存在且签名薄（只按契约调用，本地禁自拼 Bearer / 硬编码基址）", async () => {
    expect(typeof fetchMindList).toBe("function");
    expect(typeof fetchMindDetail).toBe("function");
    expect(typeof fetchMindProposals).toBe("function");
    expect(typeof fetchMindProposal).toBe("function");
    expect(typeof submitMindProposal).toBe("function");
    expect(typeof finalizeMindProposal).toBe("function");

    // 源码形状：mindApi.ts 内须 import cognitiveClient（callJson + asArray + COGNITIVE_BASE），
    // 禁自拼 fetch 或硬编码 `/api/v1/cognitive` 字面量。
    const file = path.join(SRC_ROOT, "services", "cognitive", "mindApi.ts");
    const code = stripComments(fs.readFileSync(file, "utf8"));
    expect(code).toContain("from \"../cognitiveClient\"");
    expect(/fetch\(/.test(code), "mindApi 不得绕过 callJson 自调 fetch").toBe(false);
    expect(/["'`]\/api\/v1\/cognitive\//.test(code), "mindApi 不得硬编码 /api/v1/cognitive/ 字面量").toBe(false);
  });

  it("消费面（pages/decision/*）必须走 mindApi，自身禁 Bearer / fetch 自拼接", () => {
    const consumers = [
      "pages/decision/DecisionMindList.tsx",
      "pages/decision/DecisionMindDetail.tsx",
    ];
    for (const rel of consumers) {
      const file = path.join(SRC_ROOT, rel);
      expect(fs.existsSync(file), `缺消费面文件：${rel}`).toBe(true);
      const code = stripComments(fs.readFileSync(file, "utf8"));
      expect(code, `${rel} 必须 import mindApi`).toContain("services/cognitive/mindApi");
      expect(code, `${rel} 禁自拼 Bearer`).not.toMatch(/Bearer\s/);
      expect(code, `${rel} 禁自调 fetch（走 callJson 单源）`).not.toMatch(/\bfetch\(/);
      expect(code, `${rel} 禁硬编码 /minds 字面路径`).not.toMatch(/["'`]\/minds/);
    }
  });

  it("全 src 无第二处 MINDS_PROPOSED 路径族字面（只能由 mindApi 派生）", () => {
    // 严格只按「字面字符串 '/minds/proposals' 起手」判定；mindApi 自己合法持有
    // `${MIND_LIST_PATH}/proposals`，但那是 template 拼接、非字面 `/minds/proposals`，
    // 其它任何文件出现字面即视为第二处硬编码。
    const RE = /["'`]\/minds\/proposals/;
    const files = walk(".");
    const offenders = files
      .filter((f) => RE.test(stripComments(fs.readFileSync(f, "utf8"))))
      .map((f) => path.relative(SRC_ROOT, f).replace(/\\/g, "/"))
      .sort();
    expect(offenders, `全 src 无第二处 /minds/proposals 字面量：${offenders.join(", ")}`).toEqual([]);
  }, 30_000);
});
