/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { useState, useEffect } from "react";

export function useMobileSidebar() {
  const [isMobile, setIsMobile] = useState<boolean>(false);
  const [sidebarOpen, setSidebarOpen] = useState<boolean>(false);

  useEffect(() => {
    const mql = window.matchMedia("(max-width: 767px)");

    const handleChange = (e: MediaQueryListEvent | MediaQueryList) => {
      const mobile = e.matches;
      setIsMobile(mobile);
      // On resize from mobile → desktop, auto-close sidebar
      if (!mobile) {
        setSidebarOpen(false);
      }
    };

    // Set initial value
    handleChange(mql);

    mql.addEventListener("change", handleChange);
    return () => mql.removeEventListener("change", handleChange);
  }, []);

  const toggleSidebar = () => setSidebarOpen((prev) => !prev);
  const closeSidebar = () => setSidebarOpen(false);

  return { isMobile, sidebarOpen, toggleSidebar, closeSidebar };
}
