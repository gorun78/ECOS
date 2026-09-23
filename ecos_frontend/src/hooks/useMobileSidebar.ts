/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * 移动端侧边栏状态 Hook（薄壳）
 *
 * 基于通用 useMediaQuery 实现，对外签名保持：
 * - isMobile: 当前是否移动端（max-width: 767px）
 * - sidebarOpen: 抽屉是否已展开
 * - toggleSidebar: 切换抽屉
 * - closeSidebar: 关闭抽屉
 *
 * 行为语言：
 * - isMobile 转为 false（移动→桌面）时自动关闭抽屉
 * - 桌面态下 sidebarOpen 不渲染（消费方按 isMobile 判断），不影响桌面布局
 */

import { useState, useEffect } from "react";
import { useMediaQuery } from "./useMediaQuery";

const MOBILE_QUERY = "(max-width: 767px)";

export function useMobileSidebar() {
  const isMobile = useMediaQuery(MOBILE_QUERY);
  const [sidebarOpen, setSidebarOpen] = useState<boolean>(false);

  const toggleSidebar = () => setSidebarOpen((prev) => !prev);
  const closeSidebar = () => setSidebarOpen(false);

  // 视口从移动尺寸变为桌面尺寸时自动关闭抽屉，避免抽屉残留在大屏幕上
  useEffect(() => {
    if (!isMobile && sidebarOpen) {
      setSidebarOpen(false);
    }
  }, [isMobile, sidebarOpen]);

  return { isMobile, sidebarOpen, toggleSidebar, closeSidebar };
}
