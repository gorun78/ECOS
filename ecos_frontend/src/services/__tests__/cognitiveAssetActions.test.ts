// @vitest-environment node
/**
 * 认知资产候选提案 / 受控迁移客户端行为守卫（设计方案 §3.5 / 补充设计 SUP-COG-03、§8.4）。
 *
 * <p>用 fetch 桩核验：① 请求 URL 由单源路径构造器产出（proposals / transitions）；
 * ② 请求体字段与服务端 DTO 逐字对齐（AssetProposalDTO 五字段 / AssetTransitionRequest 四字段，
 * 空可选字段不得入体——后端把缺失与空串区分处理）；③ ApiResponse 信封解包后原样返回服务端回执，
 * 前端不合成 status；④ 409 归一为 CognitiveApiError(status=409, code=HTTP_ERROR) 供 UI 冲突分支。</p>
 *
 * <p>环境：node（本机 Node 20.18.1 jsdom 依赖链 ERR_REQUIRE_ESM，与仓内既有守卫同口径）。</p>
 */
import { describe, it, expect, vi, afterEach } from "vitest";
import {
  submitAssetProposal,
  transitionCognitiveAsset,
  cognitiveAssetProposalsPath,
  cognitiveAssetTransitionPath,
  isCognitiveApiError,
} from "../cognitiveClient";

type Captured = { url: string; method: string; body: Record<string, unknown> };

function stubFetch(capture: { req: Captured | null }, res: { ok: boolean; status: number; json: unknown }): void {
  const text = JSON.stringify(res.json);
  vi.stubGlobal("fetch", vi.fn(async (input: unknown, init?: RequestInit) => {
    capture.req = {
      url: String(input),
      method: String(init?.method ?? "GET"),
      body: init?.body ? (JSON.parse(String(init.body)) as Record<string, unknown>) : {},
    };
    return {
      ok: res.ok,
      status: res.status,
      text: async () => text,
      json: async () => res.json,
      headers: { get: (_k: string): string | null => null },
    };
  }));
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("submitAssetProposal（POST proposals，SUP-COG-03）", () => {
  it("URL 经单源构造器；请求体五字段对齐 AssetProposalDTO，空可选字段不入体", async () => {
    const cap: { req: Captured | null } = { req: null };
    stubFetch(cap, {
      ok: true,
      status: 200,
      json: { code: 200, data: { proposalId: "p-1", assetId: "bel:x", status: "PENDING_APPROVAL", carrier: "ecos.audit", eventRef: "CognitiveCandidateProposed" } },
    });
    const receipt = await submitAssetProposal("bel:x", {
      targetState: "APPROVED",
      reason: "evidence-backed update",
      evidenceRef: "ev:42",
    });
    expect(cap.req?.url).toBe(cognitiveAssetProposalsPath("bel:x"));
    expect(cap.req?.method).toBe("POST");
    expect(cap.req?.body).toEqual({ targetState: "APPROVED", reason: "evidence-backed update", evidenceRef: "ev:42" });
    // 回执 = 服务端原值，前端未合成
    expect(receipt.status).toBe("PENDING_APPROVAL");
    expect(receipt.proposalId).toBe("p-1");
  });
});

describe("transitionCognitiveAsset（POST transitions，§8.4）", () => {
  it("成功回执携带 fromState/toState 服务端原值", async () => {
    const cap: { req: Captured | null } = { req: null };
    stubFetch(cap, {
      ok: true,
      status: 200,
      json: { code: 200, data: { assetId: "hyp:7", assetClass: "HYPOTHESIS", fromState: "CANDIDATE", toState: "VALIDATED", traceId: "t-9", eventRef: "CognitiveAssetStatusChanged" } },
    });
    const r = await transitionCognitiveAsset("hyp:7", { targetState: "VALIDATED", reason: "checks passed", operator: "alice" });
    expect(cap.req?.url).toBe(cognitiveAssetTransitionPath("hyp:7"));
    expect(cap.req?.body).toEqual({ targetState: "VALIDATED", reason: "checks passed", operator: "alice" });
    expect(r.fromState).toBe("CANDIDATE");
    expect(r.toState).toBe("VALIDATED");
  });

  it("非法迁移 409 → CognitiveApiError(status=409)，UI 冲突分支可用", async () => {
    const cap: { req: Captured | null } = { req: null };
    stubFetch(cap, {
      ok: false,
      status: 409,
      json: { code: 409, errorCode: "ECOS-COG-409S", message: "illegal transition", data: { traceId: "t-err" } },
    });
    await expect(
      transitionCognitiveAsset("mod:1", { targetState: "ACTIVE", reason: "skip" }),
    ).rejects.toMatchObject({ status: 409, code: "HTTP_ERROR" });
    const err = await transitionCognitiveAsset("mod:1", { targetState: "ACTIVE", reason: "skip" }).catch((e) => e);
    expect(isCognitiveApiError(err)).toBe(true);
  });
});
