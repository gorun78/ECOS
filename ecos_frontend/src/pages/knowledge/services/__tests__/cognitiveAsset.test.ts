/**
 * cognitiveAsset 归一/派生逻辑单测（议题1 / 补充设计 §8、§13 SUP-COG-01/02）。
 *
 * 覆盖「无假真理 P0」核心契约：
 *   - normalizeAssetRows 丢弃无 id 行（不造假行）
 *   - label 缺失语义字段时回落真实 assetId（非占位假名）
 *   - 三态响应（裸数组 / {rows} / {data}）兼容
 *   - probability 缺失即 null（禁合成 0）
 *   - assetStatusTone / isUncalibratedKind / isLegacyKind 与服务端枚举词表对齐
 *
 * 环境：node —— 纯函数无 DOM。
 */
// @vitest-environment node
import { describe, it, expect } from "vitest";
import {
  normalizeAssetRows,
  assetStatusLabelKey,
  probKindLabelKey,
  isUncalibratedKind,
  isLegacyKind,
  assetStatusTone,
} from "../cognitiveAsset";

describe("normalizeAssetRows", () => {
  it("裸数组：belief 行取 variableName/probability/lifecycleState", () => {
    const rows = normalizeAssetRows(
      [
        { beliefId: "bel:1", variableName: "demand", probability: 0.72, lifecycleState: "ACTIVE", updatedAt: "2026-10-01T00:00:00Z" },
      ],
      "BELIEF",
    );
    expect(rows).toHaveLength(1);
    expect(rows[0].assetId).toBe("bel:1");
    expect(rows[0].label).toBe("demand");
    expect(rows[0].status).toBe("ACTIVE");
    expect(rows[0].probability).toBe(0.72);
    expect(rows[0].assetClass).toBe("BELIEF");
  });

  it("{rows} / {data} 包装形态均可解析", () => {
    const wrap = (payload: unknown) =>
      normalizeAssetRows(payload, "HYPOTHESIS").map(r => r.assetId);
    expect(wrap({ rows: [{ hypothesisId: "hyp:1", statement: "s" }] })).toEqual(["hyp:1"]);
    expect(wrap({ data: [{ id: "hyp:2", statement: "s" }] })).toEqual(["hyp:2"]);
  });

  it("丢弃无 id 行（不显示假行 / 无法 resolve）", () => {
    const rows = normalizeAssetRows([{ statement: "orphan" }], "HYPOTHESIS");
    expect(rows).toHaveLength(0);
  });

  it("无语义字段时 label 回落真实 assetId（非占位假名）", () => {
    const rows = normalizeAssetRows([{ modelId: "mod:9" }], "MODEL");
    expect(rows).toHaveLength(1);
    expect(rows[0].label).toBe("mod:9");
    expect(rows[0].detail).toBeNull();
    expect(rows[0].probability).toBeNull();
  });

  it("status 优先 lifecycleState，回落 status；均缺 → null", () => {
    const a = normalizeAssetRows([{ id: "bel:1", lifecycleState: "DRAFT", status: "X" }], "BELIEF")[0];
    expect(a.status).toBe("DRAFT");
    const b = normalizeAssetRows([{ id: "bel:2", status: "CANDIDATE" }], "BELIEF")[0];
    expect(b.status).toBe("CANDIDATE");
    const c = normalizeAssetRows([{ id: "bel:3" }], "BELIEF")[0];
    expect(c.status).toBeNull();
  });

  it("非数组 / 非法入参 → 空集（不抛异常、不造假）", () => {
    expect(normalizeAssetRows(null, "MODEL")).toEqual([]);
    expect(normalizeAssetRows("string", "MODEL")).toEqual([]);
    expect(normalizeAssetRows({}, "MODEL")).toEqual([]);
  });
});

describe("i18n key + 枚举派生", () => {
  it("status/probKind key 前缀与服务端枚举名对齐", () => {
    expect(assetStatusLabelKey("ACTIVE")).toBe("knowledge.cognitiveAsset.status.ACTIVE");
    expect(assetStatusLabelKey(null)).toBeNull();
    expect(probKindLabelKey("UNCALIBRATED_CONFIDENCE")).toBe(
      "knowledge.cognitiveAsset.probKind.UNCALIBRATED_CONFIDENCE",
    );
    expect(probKindLabelKey(null)).toBeNull();
  });

  it("isUncalibratedKind / isLegacyKind 命中 BeliefProbabilityKind 语义族", () => {
    expect(isUncalibratedKind("UNCALIBRATED_CONFIDENCE")).toBe(true);
    expect(isUncalibratedKind("UNCALIBRATED_MODEL_ESTIMATE")).toBe(true);
    expect(isUncalibratedKind("STATISTICAL_PROBABILITY")).toBe(false);
    expect(isUncalibratedKind(null)).toBe(false);
    expect(isLegacyKind("UNKNOWN_LEGACY")).toBe(true);
    expect(isLegacyKind("ACTIVE")).toBe(false);
  });

  it("assetStatusTone 覆盖 12 态映射（不改原值，仅语义色分类）", () => {
    expect(assetStatusTone("ACTIVE")).toBe("positive");
    expect(assetStatusTone("APPROVED")).toBe("positive");
    expect(assetStatusTone("DRAFT")).toBe("pending");
    expect(assetStatusTone("CANDIDATE")).toBe("pending");
    expect(assetStatusTone("EVIDENCE_CHECKED")).toBe("pending");
    expect(assetStatusTone("VALIDATED")).toBe("pending");
    expect(assetStatusTone("CONTRADICTED")).toBe("warning");
    expect(assetStatusTone("EXPIRED")).toBe("warning");
    expect(assetStatusTone("SUSPENDED")).toBe("warning");
    expect(assetStatusTone("SUPERSEDED")).toBe("terminal");
    expect(assetStatusTone("REJECTED")).toBe("terminal");
    expect(assetStatusTone("DEPRECATED")).toBe("terminal");
    expect(assetStatusTone("UNKNOWN_X")).toBe("unknown");
    expect(assetStatusTone(null)).toBe("unknown");
  });
});
