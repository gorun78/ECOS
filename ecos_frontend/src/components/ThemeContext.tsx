/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { createContext, useContext, useState, useEffect } from "react";

export type ThemeId = "slate-light" | "deep-space" | "cyber-terminal" | "royal-purple";

export interface ThemeStyles {
  appBg: string;
  appText: string;
  cardBg: string;
  cardBorder: string;
  cardText: string;
  cardTextMuted: string;
  sidebarBg: string;
  sidebarText: string;
  sidebarBorder: string;
  sidebarActiveBg: string;
  sidebarActiveText: string;
  sidebarHoverBg: string;
  accentBg: string;
  accentHover: string;
  accentText: string;
  accentBorder: string;
  badgeBg: string;
  badgeText: string;
  inputBg: string;
  inputText: string;
  inputBorder: string;
  /** Alias for cardTextMuted — used by newer pages */
  muted: string;
  /** Alias for sidebarBorder — used as generic border */
  appBorder: string;
}

interface ThemeContextType {
  activeTheme: ThemeId;
  setActiveTheme: (theme: ThemeId) => void;
  styles: ThemeStyles;
}

const THEME_PRESETS: Record<ThemeId, ThemeStyles> = {
  "slate-light": {
    appBg: "bg-slate-50/60",
    appText: "text-slate-800",
    cardBg: "bg-white",
    cardBorder: "border-[#E2E8F0]",
    cardText: "text-slate-800",
    cardTextMuted: "text-slate-500",
    sidebarBg: "bg-slate-100",
    sidebarText: "text-slate-600",
    sidebarBorder: "border-slate-200",
    sidebarActiveBg: "bg-indigo-50 border-l-indigo-650",
    sidebarActiveText: "text-indigo-600 font-bold",
    sidebarHoverBg: "hover:bg-slate-200/50",
    accentBg: "bg-indigo-600",
    accentHover: "hover:bg-indigo-700",
    accentText: "text-indigo-600",
    accentBorder: "border-indigo-200",
    badgeBg: "bg-indigo-50",
    badgeText: "text-indigo-700",
    inputBg: "bg-white",
    inputText: "text-slate-800",
    inputBorder: "border-[#E2E8F0]",
    muted: "text-slate-500",
    appBorder: "border-[#E2E8F0]"
  },
  "deep-space": {
    appBg: "bg-[#0B0F19]",
    appText: "text-slate-100",
    cardBg: "bg-[#141924]",
    cardBorder: "border-[#1E293B]",
    cardText: "text-slate-100",
    cardTextMuted: "text-slate-400",
    sidebarBg: "bg-[#0B0E14]",
    sidebarText: "text-slate-300",
    sidebarBorder: "border-[#1E293B]",
    sidebarActiveBg: "bg-[#1E293B] border-l-[#3B82F6]",
    sidebarActiveText: "text-[#3B82F6] font-bold",
    sidebarHoverBg: "hover:bg-[#1E293B]/60",
    accentBg: "bg-blue-600",
    accentHover: "hover:bg-blue-700",
    accentText: "text-blue-400",
    accentBorder: "border-blue-500/30",
    badgeBg: "bg-blue-950/40",
    badgeText: "text-blue-450",
    inputBg: "bg-[#1E2533]",
    inputText: "text-slate-100",
    inputBorder: "border-[#2D3748]",
    muted: "text-slate-400",
    appBorder: "border-[#1E293B]"
  },
  "cyber-terminal": {
    appBg: "bg-[#020202]",
    appText: "text-emerald-500",
    cardBg: "bg-[#080808]",
    cardBorder: "border-emerald-500/30",
    cardText: "text-emerald-400",
    cardTextMuted: "text-emerald-650",
    sidebarBg: "bg-[#000000]",
    sidebarText: "text-emerald-500",
    sidebarBorder: "border-emerald-650/50",
    sidebarActiveBg: "bg-[#0A1A0F] border-l-emerald-500",
    sidebarActiveText: "text-emerald-400 font-bold",
    sidebarHoverBg: "hover:bg-emerald-900/10",
    accentBg: "bg-emerald-650 border border-emerald-500",
    accentHover: "hover:bg-emerald-600",
    accentText: "text-emerald-400",
    accentBorder: "border-emerald-500/40",
    badgeBg: "bg-[#0A1A0F]",
    badgeText: "text-emerald-400",
    inputBg: "bg-[#050505]",
    inputText: "text-emerald-400",
    inputBorder: "border-emerald-600/40",
    muted: "text-emerald-650",
    appBorder: "border-emerald-650/50"
  },
  "royal-purple": {
    appBg: "bg-[#0F0C1B]",
    appText: "text-purple-100",
    cardBg: "bg-[#1C1530]",
    cardBorder: "border-purple-900/40",
    cardText: "text-purple-100",
    cardTextMuted: "text-purple-400",
    sidebarBg: "bg-[#0A0714]",
    sidebarText: "text-purple-300",
    sidebarBorder: "border-purple-950/60",
    sidebarActiveBg: "bg-purple-950/60 border-l-purple-500",
    sidebarActiveText: "text-purple-400 font-bold",
    sidebarHoverBg: "hover:bg-purple-900/20",
    accentBg: "bg-purple-650",
    accentHover: "hover:bg-purple-600",
    accentText: "text-purple-450",
    accentBorder: "border-purple-700/30",
    badgeBg: "bg-purple-950/50",
    badgeText: "text-purple-350",
    inputBg: "bg-[#251D3E]",
    inputText: "text-purple-200",
    inputBorder: "border-purple-800/40",
    muted: "text-purple-400",
    appBorder: "border-purple-900/40"
  }
};

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [activeTheme, setActiveThemeState] = useState<ThemeId>(() => {
    const saved = localStorage.getItem("ecos_theme");
    return (saved as ThemeId) || "slate-light";
  });

  const setActiveTheme = (theme: ThemeId) => {
    setActiveThemeState(theme);
    localStorage.setItem("ecos_theme", theme);
  };

  useEffect(() => {
    // Apply custom values or data-attributes on the high-level root wrapper if desired
    const root = document.documentElement;
    root.setAttribute("data-theme", activeTheme);
    if (activeTheme === "cyber-terminal") {
      root.classList.add("dark");
    } else if (activeTheme === "slate-light") {
      root.classList.remove("dark");
    } else {
      root.classList.add("dark");
    }
  }, [activeTheme]);

  return (
    <ThemeContext.Provider value={{ activeTheme, setActiveTheme, styles: THEME_PRESETS[activeTheme] }}>
      {children}
    </ThemeContext.Provider>
  );
};

export const useTheme = () => {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error("useTheme must be used within a ThemeProvider");
  }
  return context;
};
