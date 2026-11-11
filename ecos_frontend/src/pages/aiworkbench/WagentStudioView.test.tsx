// @vitest-environment jsdom
/**
 * WagentStudioView 组件门禁（批 C，2026-11-11 WAgent 三块差距计划）。
 *
 * 钉四件事（对应 UI 的真值纪律）：
 * ① 挂载即拉两路真实数据（tools / candidates），空态如实显示，不伪造占位；
 * ② 残留旧假实现（goal 发 {intent, description}、publish 读 {status, trace_id}）不得回归；
 * ③ 状态断档如实：generated 候选评审按钮禁用 + 原因可见；approved 候选才放行发布票据；
 * ④ 服务端 403/409 原样呈现在横幅，禁本地推进状态。
 *
 * @license Apache-2.0
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import React from "react";
import { render, screen, cleanup, act, fireEvent } from "@testing-library/react";
import { ThemeProvider } from "../../components/ThemeContext";
import { LanguageProvider } from "../../components/LanguageContext";
import WagentStudioView from "./WagentStudioView";

vi.mock("../../services/wagentClient", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../services/wagentClient")>();
  return {
    ...actual,
    searchWagentTools: vi.fn(),
    listWagentCandidates: vi.fn(),
    createWagentGoal: vi.fn(),
    createWagentCandidate: vi.fn(),
    createApprovalToken: vi.fn(),
    reviewWagentCandidate: vi.fn(),
    publishWagentCandidate: vi.fn(),
    validateWagentCandidate: vi.fn(),
  };
});

import {
  createApprovalToken,
  createWagentCandidate,
  createWagentGoal,
  listWagentCandidates,
  publishWagentCandidate,
  searchWagentTools,
  validateWagentCandidate,
  type WagentCandidateRow,
} from "../../services/wagentClient";

const mockTools = searchWagentTools as unknown as ReturnType<typeof vi.fn>;
const mockCands = listWagentCandidates as unknown as ReturnType<typeof vi.fn>;
const mockGoal = createWagentGoal as unknown as ReturnType<typeof vi.fn>;
const mockCreate = createWagentCandidate as unknown as ReturnType<typeof vi.fn>;
const mockToken = createApprovalToken as unknown as ReturnType<typeof vi.fn>;
const mockPublish = publishWagentCandidate as unknown as ReturnType<typeof vi.fn>;
const mockValidate = validateWagentCandidate as unknown as ReturnType<typeof vi.fn>;

function setLocale(locale: "zh" | "en") {
  (globalThis as unknown as { __ecos_test_locale?: "zh" | "en" }).__ecos_test_locale = locale;
}

function renderView() {
  return render(
    <LanguageProvider>
      <ThemeProvider>
        <WagentStudioView />
      </ThemeProvider>
    </LanguageProvider>,
  );
}

async function flush() {
  await act(async () => {
    await Promise.resolve();
  });
  await act(async () => {
    await new Promise<void>((r) => setTimeout(r, 0));
  });
}

function cand(id: string, status: string, type = "KNOWLEDGE_ITEM"): WagentCandidateRow {
  return {
    candidate_id: id,
    candidate_type: type,
    target_engine: "kb",
    status,
    batch_id: "b",
    payload_hash: "h",
    published_ref_text: null,
    published_git_ref: null,
    expire_time: null,
  };
}

beforeEach(() => {
  setLocale("zh");
  mockTools.mockReset();
  mockCands.mockReset();
  mockGoal.mockReset();
  mockCreate.mockReset();
  mockToken.mockReset();
  mockPublish.mockReset();
  mockValidate.mockReset();
  mockTools.mockResolvedValue([]);
  mockCands.mockResolvedValue({ candidates: [], total: 0 });
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("WagentStudioView 真值纪律", () => {
  it("挂载即走两路真实端点；空态如实显示（不伪造工具/候选）", async () => {
    renderView();
    await flush();
    expect(mockTools).toHaveBeenCalledTimes(1);
    expect(mockCands).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("wagent-studio")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: /装配/ }));
    await flush();
    expect(screen.getByText(/无可选工具契约/)).toBeTruthy();
  });

  it("① 目标步：提交调用 createWagentGoal；成功后进入装配步", async () => {
    mockGoal.mockResolvedValue({ goal_id: "g-1" });
    renderView();
    await flush();
    fireEvent.change(screen.getByPlaceholderText(/提升华东区项目利润率/), {
      target: { value: "提升利润率" },
    });
    fireEvent.click(screen.getByRole("button", { name: /创建目标/ }));
    await flush();
    expect(mockGoal).toHaveBeenCalledWith(
      expect.objectContaining({ name: "提升利润率", goalType: "FORECAST" }),
    );
    expect(screen.getByPlaceholderText(/关键字过滤/)).toBeTruthy();
  });

  it("② 装配：留空目标引用 ⇒ 不上行该字段（conflict 段由服务端判 skipped）", async () => {
    mockTools.mockResolvedValue([
      { name: "kb.search", version: "1.0.0", engine: "kb", category: "query", status: "active" },
    ]);
    mockCreate.mockResolvedValue(cand("c-new", "generated"));
    renderView();
    await flush();
    fireEvent.click(screen.getByRole("button", { name: /装配/ }));
    await flush();
    fireEvent.click(screen.getByText("kb.search"));
    fireEvent.click(screen.getByRole("button", { name: /装配并创建候选/ }));
    await flush();
    const arg = mockCreate.mock.calls[0][0] as Record<string, unknown>;
    // undefined 在 JSON.stringify 后整键消失，线上不会出现该字段
    expect(arg.targetObjectRefJson).toBeUndefined();
  });

  it("② 装配：填目标引用 ⇒ 逐字上行并在创建结果里回显服务端规范化形态", async () => {
    mockTools.mockResolvedValue([
      { name: "kb.search", version: "1.0.0", engine: "kb", category: "query", status: "active" },
    ]);
    mockCreate.mockResolvedValue({
      ...cand("c-ref", "generated"),
      target_object_ref_json: '{"engine":"ontology","objectId":"o-1"}',
    });
    renderView();
    await flush();
    fireEvent.click(screen.getByRole("button", { name: /装配/ }));
    await flush();
    fireEvent.click(screen.getByText("kb.search"));
    fireEvent.change(screen.getByPlaceholderText(/"engine":"ontology"/), {
      target: { value: '{"objectId":"o-1","engine":"ontology"}' },
    });
    fireEvent.click(screen.getByRole("button", { name: /装配并创建候选/ }));
    await flush();
    const arg = mockCreate.mock.calls[0][0] as Record<string, unknown>;
    expect(arg.targetObjectRefJson).toBe('{"objectId":"o-1","engine":"ontology"}');
    // 回显的是服务端返回的规范化形态（键序已重排），不是本地输入原文
    expect(screen.getByText(/落库（规范化）: \{"engine":"ontology","objectId":"o-1"\}/)).toBeTruthy();
  });

  it("③ generated 候选不可直接评审（按钮禁用）；须先走 validate 通道", async () => {
    mockCands.mockResolvedValue({ candidates: [cand("c-gen", "generated")], total: 1 });
    renderView();
    await flush();
    // 切到评审步
    fireEvent.click(screen.getByRole("button", { name: /评审/ }));
    await flush();
    expect(screen.getByText("c-gen")).toBeTruthy();
    fireEvent.click(screen.getByText("c-gen"));
    await flush();
    const approve = screen.getByRole("button", { name: /评审通过/ }) as HTMLButtonElement;
    expect(approve.disabled).toBe(true);
    // 校验入口对 generated 可用，且提示文案指名校验为评审前置
    const validateBtn = screen.getByTestId("wagent-validate") as HTMLButtonElement;
    expect(validateBtn.disabled).toBe(false);
    expect(screen.getByText(/评审前必经五段校验/)).toBeTruthy();
    expect(mockToken).not.toHaveBeenCalled();
  });

  it("③a validate 返回 200 但停在 validating ⇒ 评审仍禁用（以服务端 status 为准，禁本地放行）", async () => {
    mockCands.mockResolvedValue({ candidates: [cand("c-val", "generated")], total: 1 });
    mockValidate.mockResolvedValue({
      ...cand("c-val", "validating"),
      validation: {
        structure: "fail",
        reference: "pass",
        conflict: "skipped",
        impact: "pass",
        regression: "skipped",
      },
    });
    renderView();
    await flush();
    fireEvent.click(screen.getByRole("button", { name: /评审/ }));
    await flush();
    fireEvent.click(screen.getByText("c-val"));
    fireEvent.click(screen.getByTestId("wagent-validate"));
    await flush();
    expect(mockValidate).toHaveBeenCalledWith("c-val");
    expect(screen.getByText(/structure=fail/)).toBeTruthy();
    const approve = screen.getByRole("button", { name: /评审通过/ }) as HTMLButtonElement;
    expect(approve.disabled).toBe(true);
  });

  it("③a validate 推进到 validated ⇒ 评审按钮放行", async () => {
    mockCands.mockResolvedValue({ candidates: [cand("c-ok", "generated")], total: 1 });
    mockValidate.mockResolvedValue({
      ...cand("c-ok", "validated"),
      validation: {
        structure: "pass",
        reference: "pass",
        conflict: "skipped",
        impact: "pass",
        regression: "skipped",
      },
    });
    renderView();
    await flush();
    fireEvent.click(screen.getByRole("button", { name: /评审/ }));
    await flush();
    fireEvent.click(screen.getByText("c-ok"));
    fireEvent.click(screen.getByTestId("wagent-validate"));
    await flush();
    const approve = screen.getByRole("button", { name: /评审通过/ }) as HTMLButtonElement;
    expect(approve.disabled).toBe(false);
  });

  it("③ approved 候选才放行发布票据；非 approved 时票据按钮禁用", async () => {
    mockCands.mockResolvedValue({ candidates: [cand("c-appr", "approved")], total: 1 });
    mockToken.mockResolvedValue({ token: "ecos-apv-ok", approvalId: "a1" });
    mockPublish.mockResolvedValue({
      candidateId: "c-appr",
      status: "published",
      publishedRef: "sha",
      gitRef: "sha",
      gitTag: "v1",
      repositoryId: "kb-asset-tree",
      traceId: "tr-9",
    });
    renderView();
    await flush();
    fireEvent.click(screen.getByRole("button", { name: /评审/ }));
    await flush();
    fireEvent.click(screen.getByText("c-appr"));
    fireEvent.click(screen.getByRole("button", { name: /发布/ }));
    await flush();

    const issue = screen.getByRole("button", { name: /签发审批凭证/ }) as HTMLButtonElement;
    expect(issue.disabled).toBe(false);
    fireEvent.click(issue);
    await flush();
    expect(mockToken).toHaveBeenCalledWith(
      expect.objectContaining({ scopeType: "CANDIDATE_PUBLISH", scopeId: "c-appr" }),
    );
    expect(screen.getByTestId("wagent-approval-token").textContent).toContain("ecos-apv-ok");

    fireEvent.click(screen.getByRole("button", { name: /确认发布/ }));
    await flush();
    expect(mockPublish).toHaveBeenCalledWith("c-appr", "ecos-apv-ok");
    expect(screen.getByTestId("wagent-publish-result").textContent).toContain("published");
  });

  it("④ 服务端 403 原样呈现（四眼/策略拒绝不得被吞成成功）", async () => {
    mockCands.mockResolvedValue({ candidates: [cand("c-pend", "review_pending")], total: 1 });
    mockToken.mockRejectedValue(new Error("四眼原则：审批人不得给自己发起的事项签发凭证"));
    renderView();
    await flush();
    fireEvent.click(screen.getByRole("button", { name: /评审/ }));
    await flush();
    fireEvent.click(screen.getByText("c-pend"));
    await flush();
    fireEvent.click(screen.getByRole("button", { name: /评审通过/ }));
    await flush();
    expect(screen.getAllByText(/四眼原则/).length).toBeGreaterThanOrEqual(2);
    expect(mockPublish).not.toHaveBeenCalled();
  });
});
