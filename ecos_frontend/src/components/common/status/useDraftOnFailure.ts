/**
 * useDraftOnFailure — 离线/超时草稿保留 hook（详设-08 §2.3「离线/超时」行：
 * BFF 504 / fetch abort 时**保留已填内容**，验收标识 `offline.spec › keeps local draft on 504`）。
 *
 * 语义：
 * - 保存请求失败且失败形态为「后端不可达类」（HTTP 504 / AbortError / 网络 TypeError / NetworkError）
 *   时，调用 preserve(draft, failure) 把当前草稿快照留存在本 hook（内存态，随组件生命周期），
 *   返回 true 表示草稿已保留；
 * - 其它失败（4xx 业务错误等）不吞草稿，返回 false，由调用方走 ErrorPanel；
 * - 恢复连通并成功提交后调用 discard() 清除快照。
 *
 * 不做 localStorage 伪持久化（前端规范：禁 localStorage 伪持久化通道承载业务真值；
 * 本 hook 只是「失败瞬间的内容闸门」）。
 * @license Apache-2.0
 */

import { useCallback, useState } from "react";

/** 失败信号的最小结构（不要求具体 Error 类型，status/name 够用） */
export interface DraftFailureLike {
  status?: number;
  name?: string;
  message?: string;
}

/**
 * 判定是否「后端不可达类」失败：
 * 504 Gateway Timeout / AbortError（fetch abort）/ NetworkError / TypeError（fetch 网络层）
 */
export function isDraftPreservingFailure(failure: DraftFailureLike | Error): boolean {
  const status = (failure as DraftFailureLike).status;
  if (status === 504) return true;
  if (failure.name === "AbortError") return true;
  if (failure.name === "NetworkError") return true;
  if (failure instanceof TypeError) return true;
  return false;
}

export interface UseDraftOnFailureResult<T> {
  /** 是否处于「草稿已保留」态（可据此渲染 OfflineNotice） */
  preserving: boolean;
  /** 保留的草稿快照；非保留态为 null */
  snapshot: T | null;
  /**
   * 保存失败时调用：判定失败形态，符合「不可达类」则留存草稿快照。
   * @returns true=草稿已保留；false=普通业务失败，不留存
   */
  preserve: (draft: T, failure: DraftFailureLike | Error) => boolean;
  /** 成功提交/恢复后清除快照 */
  discard: () => void;
}

export function useDraftOnFailure<T>(): UseDraftOnFailureResult<T> {
  const [snapshot, setSnapshot] = useState<{ value: T } | null>(null);

  const preserve = useCallback((draft: T, failure: DraftFailureLike | Error): boolean => {
    if (isDraftPreservingFailure(failure)) {
      setSnapshot({ value: draft });
      return true;
    }
    return false;
  }, []);

  const discard = useCallback(() => setSnapshot(null), []);

  return {
    preserving: snapshot !== null,
    snapshot: snapshot ? snapshot.value : null,
    preserve,
    discard,
  };
}
