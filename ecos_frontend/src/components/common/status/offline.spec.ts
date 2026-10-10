/**
 * offline.spec — 「离线/超时」状态矩阵单元测试门禁
 * （详设-08 §2.3 离线行：BFF 504 / 请求 abort 时**保留本地草稿**，不得清空用户已填内容）。
 *
 * 冻结用例名（逐字一致，不得改名）：
 *   - keeps local draft on 504
 *
 * 测试策略：vitest mock 全局 fetch 返回 504（网关超时），走保存流程
 * （components/common/status/useDraftOnFailure），断言草稿快照仍在。
 * @license Apache-2.0
 */

import { renderHook, act } from "@testing-library/react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { useDraftOnFailure, isDraftPreservingFailure } from "./useDraftOnFailure";

/** 草稿形态（任意业务表单的投影，测试只关心「内容是否留存」） */
interface Draft {
  notes: string;
  amount: number;
}

const DRAFT: Draft = { notes: "半填写的报销说明", amount: 0 };

/** 模拟保存通道：经 fetch 提交，失败时把响应 status 交给 preserve 判定 */
async function saveWithDraft(
  draft: Draft,
  preserve: (d: Draft, failure: { status?: number; name?: string }) => boolean
): Promise<boolean> {
  const res = await fetch("/api/v1/wagent/draft", {
    method: "POST",
    body: JSON.stringify(draft),
  });
  if (!res.ok) {
    return preserve(draft, { status: res.status });
  }
  return false;
}

function mockFetchStatus(status: number) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: status >= 200 && status < 300,
      status,
      json: async () => ({}),
      text: async () => "",
    })
  );
}

describe("offline draft retention (详设-08 §2.3 离线/超时)", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("keeps local draft on 504", async () => {
    mockFetchStatus(504);

    const { result } = renderHook(() => useDraftOnFailure<Draft>());

    let kept: boolean | undefined;
    await act(async () => {
      kept = await saveWithDraft(DRAFT, result.current.preserve);
    });

    // 失败形态 = 504 → 草稿必须被保留（不是清空、不是假成功）
    expect(kept).toBe(true);
    expect(result.current.preserving).toBe(true);
    expect(result.current.snapshot).not.toBeNull();
    expect(result.current.snapshot).toEqual(DRAFT);
    // 草稿字段逐字留存：0 金额也是真实输入，不得被归一化丢弃
    expect(result.current.snapshot?.notes).toBe("半填写的报销说明");
    expect(result.current.snapshot?.amount).toBe(0);
  });

  it("keeps local draft on aborted request (AbortError)", async () => {
    const abortError = new Error("request aborted");
    abortError.name = "AbortError";
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(abortError));

    const { result } = renderHook(() => useDraftOnFailure<Draft>());

    let kept: boolean | undefined;
    await act(async () => {
      try {
        await fetch("/api/v1/wagent/draft", { method: "POST", body: JSON.stringify(DRAFT) });
        kept = false;
      } catch (err) {
        kept = result.current.preserve(DRAFT, err as Error);
      }
    });

    expect(kept).toBe(true);
    expect(result.current.snapshot).toEqual(DRAFT);
  });

  it("does not treat 4xx business failure as draft-preserving", async () => {
    mockFetchStatus(400);

    const { result } = renderHook(() => useDraftOnFailure<Draft>());

    let kept: boolean | undefined;
    await act(async () => {
      kept = await saveWithDraft(DRAFT, result.current.preserve);
    });

    // 4xx 是业务失败（走 ErrorPanel），不属于「后端不可达」，preserve 必须返回 false
    expect(kept).toBe(false);
    expect(result.current.preserving).toBe(false);
    expect(result.current.snapshot).toBeNull();
  });

  it("clears preserved draft after successful resubmit", async () => {
    // 先 504 保留，再恢复成功提交 → discard 清除快照
    mockFetchStatus(504);
    const { result } = renderHook(() => useDraftOnFailure<Draft>());
    await act(async () => {
      await saveWithDraft(DRAFT, result.current.preserve);
    });
    expect(result.current.preserving).toBe(true);

    await act(async () => {
      result.current.discard();
    });
    expect(result.current.preserving).toBe(false);
    expect(result.current.snapshot).toBeNull();
  });

  it("isDraftPreservingFailure recognises unreachable-class failures only", () => {
    expect(isDraftPreservingFailure({ status: 504 })).toBe(true);
    expect(isDraftPreservingFailure({ status: 502 })).toBe(false);
    expect(isDraftPreservingFailure({ status: 400 })).toBe(false);
    expect(isDraftPreservingFailure(Object.assign(new Error("aborted"), { name: "AbortError" }))).toBe(true);
    expect(isDraftPreservingFailure(Object.assign(new Error("no net"), { name: "NetworkError" }))).toBe(true);
    // fetch 网络层失败在浏览器里表现为 TypeError
    expect(isDraftPreservingFailure(new TypeError("Failed to fetch"))).toBe(true);
  });
});
