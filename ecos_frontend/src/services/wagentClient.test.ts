// @vitest-environment node
/**
 * wagentClient 契约对齐门禁（批 C，2026-11-11 WAgent 三块差距计划）。
 *
 * 钉三件事：
 * ① 路径逐字 = WAgentApiPaths.java（/api/v1/wagent/...），禁自造前缀/动作段；
 * ② 请求体字段名逐字 = 服务端 record（GoalCreateRequest / CandidateCreateRequest /
 *    ApprovalTokenRequest / CandidateReviewRequest / CandidatePublishRequest），
 *    特别锁 publish 的 camelCase `approvalToken`（@JsonAlias 兼容 snake_case 是服务端兜底，
 *    前端只发契约正形）；
 * ③ 响应字段消费逐字 = 服务端 VO @JsonProperty（IssuedToken / CandidatePublishResult）。
 *
 * 假真理防线：apiFetchData 抛错必须原样冒泡（403/409/501 由 UI 如实呈现），禁本地兜底。
 *
 * @license Apache-2.0
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const calls: Array<{ url: string; init?: RequestInit }> = [];
let nextResult: unknown = undefined;
let nextError: unknown = null;
let tokenCounter = 0;

vi.mock("../api", () => ({
  API_BASE: "/api",
  apiFetchData: async (url: string, init?: RequestInit) => {
    calls.push({ url, init });
    if (nextError) throw nextError;
    return nextResult;
  },
}));

import {
  WAGENT_GOAL_INTENTS,
  createApprovalToken,
  createWagentCandidate,
  createWagentGoal,
  getWagentCandidate,
  listWagentCandidates,
  publishWagentCandidate,
  reviewWagentCandidate,
  searchWagentTools,
} from "./wagentClient";

function bodyOf(i: number): Record<string, unknown> {
  const raw = calls[i]?.init?.body;
  expect(typeof raw, "请求体必须是 JSON 字符串").toBe("string");
  const parsed: unknown = JSON.parse(String(raw));
  expect(parsed !== null && typeof parsed === "object").toBe(true);
  return parsed as Record<string, unknown>;
}

describe("wagentClient 路径与字段契约", () => {
  beforeEach(() => {
    calls.length = 0;
    nextError = null;
    nextResult = { ok: true };
    tokenCounter = 0;
  });

  it("八意图词表与 WAgentEnums.GoalIntent 同序同值", () => {
    expect([...WAGENT_GOAL_INTENTS]).toEqual([
      "FORECAST",
      "DIAGNOSE",
      "SIMULATE",
      "RANK",
      "MONITOR",
      "BUILD",
      "DECIDE",
      "REVIEW",
    ]);
  });

  it("createGoal 打 /api/v1/wagent/goals，字段名 = GoalCreateRequest（name/goalType/scopeJson）", async () => {
    nextResult = { goal_id: "g-1" };
    const g = await createWagentGoal({
      name: "提升利润率",
      goalType: "RANK",
      scopeJson: '{"scope":"east"}',
    });
    expect(g.goal_id).toBe("g-1");
    expect(calls[0].url).toBe("/api/v1/wagent/goals");
    expect(calls[0].init?.method).toBe("POST");
    const body = bodyOf(0);
    expect(Object.keys(body).sort()).toEqual(["goalType", "name", "scopeJson"].sort());
    expect(body.name).toBe("提升利润率");
    expect(body.goalType).toBe("RANK");
  });

  it("createCandidate 打 /api/v1/wagent/candidates，字段名 = CandidateCreateRequest", async () => {
    nextResult = { candidate_id: "c-1", status: "generated" };
    const c = await createWagentCandidate({
      candidateType: "KNOWLEDGE_ITEM",
      payloadJson: '{"probe":1}',
      targetEngine: "kb",
      batchId: "b-1",
    });
    expect(c.candidate_id).toBe("c-1");
    expect(calls[0].url).toBe("/api/v1/wagent/candidates");
    const body = bodyOf(0);
    expect(Object.keys(body).sort()).toEqual(
      ["batchId", "candidateType", "payloadJson", "targetEngine"].sort(),
    );
    expect(body).not.toHaveProperty("payloadHash");
  });

  it("createCandidate 传 targetObjectRefJson 时逐字上行（conflict 段比对键，服务端负责规范化）", async () => {
    nextResult = { candidate_id: "c-2", status: "generated" };
    const ref = '{"objectId":"o-1","engine":"ontology"}';
    await createWagentCandidate({
      candidateType: "ENTITY",
      payloadJson: "{}",
      targetObjectRefJson: ref,
    });
    expect(bodyOf(0).targetObjectRefJson).toBe(ref);
  });

  it("listCandidates 查询参数 = candidateType/status/batchId/runId（服务端 @RequestParam 同名）", async () => {
    nextResult = { candidates: [], total: 0 };
    await listWagentCandidates({ candidateType: "METRIC", status: "approved", batchId: "b", runId: "r" });
    expect(calls[0].url).toBe(
      "/api/v1/wagent/candidates?candidateType=METRIC&status=approved&batchId=b&runId=r",
    );
    expect(calls[0].init?.method).toBeUndefined();
  });

  it("getCandidate 走 /candidates/{id} 且 id 做 URL 编码", async () => {
    nextResult = { candidate_id: "a b" };
    await getWagentCandidate("a b");
    expect(calls[0].url).toBe("/api/v1/wagent/candidates/a%20b");
  });

  it("reviewCandidate 打 /review，APPROVE 必带 approvalToken", async () => {
    nextResult = { candidate_id: "c-2", status: "approved" };
    await reviewWagentCandidate("c-2", {
      decision: "APPROVE",
      approvalToken: "ecos-apv-x",
      commentText: "ok",
    });
    expect(calls[0].url).toBe("/api/v1/wagent/candidates/c-2/review");
    const body = bodyOf(0);
    expect(body.decision).toBe("APPROVE");
    expect(body.approvalToken).toBe("ecos-apv-x");
    expect(body.commentText).toBe("ok");
  });

  it("publishCandidate 打 /publish，请求体字段是 camelCase approvalToken（契约正形）", async () => {
    nextResult = {
      candidateId: "c-3",
      status: "published",
      publishedRef: "abc",
      gitRef: "abc",
      gitTag: "v1",
      repositoryId: "kb-asset-tree",
      traceId: "t-1",
    };
    const r = await publishWagentCandidate("c-3", "ecos-apv-y");
    expect(calls[0].url).toBe("/api/v1/wagent/candidates/c-3/publish");
    const body = bodyOf(0);
    expect(Object.keys(body)).toEqual(["approvalToken"]);
    expect(body.approvalToken).toBe("ecos-apv-y");
    // 响应消费字段逐字 = CandidatePublishResult
    expect(r.status).toBe("published");
    expect(r.gitRef).toBe("abc");
    expect(r.traceId).toBe("t-1");
  });

  it("createApprovalToken 打 /approval-tokens，字段名 = ApprovalTokenRequest", async () => {
    nextResult = { approvalId: "a-1", token: "ecos-apv-z", scopeType: "CANDIDATE_PUBLISH", scopeId: "c-1" };
    const issued = await createApprovalToken({
      scopeType: "CANDIDATE_PUBLISH",
      scopeId: "c-1",
      requestedBy: "generator",
      ttlSeconds: 600,
    });
    expect(calls[0].url).toBe("/api/v1/wagent/approval-tokens");
    const body = bodyOf(0);
    expect(Object.keys(body).sort()).toEqual(["requestedBy", "scopeId", "scopeType", "ttlSeconds"].sort());
    expect(issued.token).toBe("ecos-apv-z");
  });

  it("searchTools 兼容数组与 {tools} 双形态，并把 {tools} 解包成数组", async () => {
    nextResult = { tools: [{ name: "t1", version: "1.0.0", engine: "kb", category: "read", status: "active" }] };
    const wrapped = await searchWagentTools({ keyword: "profit" });
    expect(calls[0].url).toBe("/api/v1/wagent/tools?keyword=profit");
    expect(wrapped).toHaveLength(1);
    expect(wrapped[0].name).toBe("t1");

    nextResult = [{ name: "t2", version: "1", engine: "kb", category: "read", status: "active" }];
    const bare = await searchWagentTools();
    expect(bare[0].name).toBe("t2");
  });

  it("服务端错误原样冒泡（403/409/501 一律不吞，禁本地兜底成功）", async () => {
    const err = Object.assign(new Error("E-POLICY: 四眼原则"), { httpStatus: 403 });
    nextError = err;
    await expect(
      createApprovalToken({ scopeType: "CANDIDATE_PUBLISH", scopeId: "c", requestedBy: "me" }),
    ).rejects.toBe(err);
    await expect(publishWagentCandidate("c", "tok")).rejects.toBe(err);
    tokenCounter += 1;
    expect(tokenCounter).toBe(1);
  });
});
