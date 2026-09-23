/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * 通用媒体查询 Hook
 *
 * 设计要点：
 * - SSR 安全：仅在浏览器环境注册监听器；服务端或 typeof window === 'undefined' 时返回 false
 * - 事件解绑清理：unmount 时 removeEventListener（兼容旧版 addListener 但此处只走标准 API）
 * - 参数化 query：业务方传任意 query（如 "(max-width: 767px)"、"(min-width: 768px) and (max-width: 1023px)"）
 * - 同组件多次挂载同 query 时各自独立（每次 new MQL 实例），无副作用
 */

import { useState, useEffect } from "react";

/**
 * 判读 media query 是否匹配。
 *
 * @param query - matchMedia 查询字符串，例如 "(max-width: 767px)"
 * @returns 当前是否匹配（boolean，初始值首次渲染即准确）
 */
export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState<boolean>(() => {
    // SSR 安全：typeof window 检查在模块顶层不可靠，放在函数体内每挂载时求值
    if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
      return false;
    }
    return window.matchMedia(query).matches;
  });

  useEffect(() => {
    // 二次守卫：useEffect 仅在客户端执行，但加 typeof 检查进一步保护 HMR 边缘情况
    if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
      return;
    }

    const mql = window.matchMedia(query);

    // 首帧以 mql.matches 为准（避免 setState 抖动）
    setMatches(mql.matches);

    const handleChange = (e: MediaQueryListEvent | MediaQueryList) => {
      setMatches(e.matches);
    };

    mql.addEventListener("change", handleChange);
    return () => {
      mql.removeEventListener("change", handleChange);
    };
  }, [query]);

  return matches;
}

export default useMediaQuery;
