/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { createContext, useContext, useState, useEffect } from "react";
import ontologyZh from "../locales/ontology/zh-CN.json";
import aiworkbenchZh from "../locales/aiworkbench/zh-CN.json";
import dwZh from "../locales/dw/zh-CN.json";
import knowledgeZh from "../locales/knowledge/zh-CN.json";
import secZh from "../locales/sec/zh-CN.json";
import commonZh from "../locales/common/zh-CN.json";
import copilotZh from "../locales/copilot/zh-CN.json";
import dashboardZh from "../locales/dashboard/zh-CN.json";
import scenarioZh from "../locales/scenario/zh-CN.json";
import cognitionZh from "../locales/cognition/zh-CN.json";
import platformZh from "../locales/platform/zh-CN.json";
import workspaceZh from "../locales/workspace/zh-CN.json";
import navigationZh from "../locales/navigation/zh-CN.json";
import ontologyEn from "../locales/ontology/en.json";
import aiworkbenchEn from "../locales/aiworkbench/en.json";
import dwEn from "../locales/dw/en.json";
import knowledgeEn from "../locales/knowledge/en.json";
import secEn from "../locales/sec/en.json";
import commonEn from "../locales/common/en.json";
import copilotEn from "../locales/copilot/en.json";
import dashboardEn from "../locales/dashboard/en.json";
import scenarioEn from "../locales/scenario/en.json";
import cognitionEn from "../locales/cognition/en.json";
import platformEn from "../locales/platform/en.json";
import workspaceEn from "../locales/workspace/en.json";
import navigationEn from "../locales/navigation/en.json";

export type Locale = "zh" | "en";

interface LanguageContextType {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  /** Translate a key; optionally interpolate params, or supply an inline fallback string. */
  t: {
    (key: string, params?: Record<string, string | number>): string;
    (key: string, fallback: string): string;
  };
}

const TRANSLATIONS: Record<Locale, Record<string, string>> = {
  zh: { ...ontologyZh, ...aiworkbenchZh, ...dwZh, ...knowledgeZh, ...secZh, ...commonZh, ...copilotZh, ...dashboardZh, ...scenarioZh, ...cognitionZh, ...platformZh, ...workspaceZh, ...navigationZh },
  en: { ...ontologyEn, ...aiworkbenchEn, ...dwEn, ...knowledgeEn, ...secEn, ...commonEn, ...copilotEn, ...dashboardEn, ...scenarioEn, ...cognitionEn, ...platformEn, ...workspaceEn, ...navigationEn },
};

const LanguageContext = createContext<LanguageContextType | undefined>(undefined);

/**
 * 当前生效 locale 的非 React 解析口 —— 与 LanguageProvider 的初始态同源同序：
 * 测试注入 `__ecos_test_locale` 优先，其次 localStorage 持久值，兜底中文。
 */
function resolveLocale(): Locale {
  const g = globalThis as unknown as { __ecos_test_locale?: Locale };
  if (g.__ecos_test_locale) return g.__ecos_test_locale;
  try {
    const saved = localStorage.getItem("ecos_locale");
    return (saved as Locale) || "zh";
  } catch {
    // storage 不可用（SSR/隐私模式）→ 默认中文，与 Provider 口径一致
    return "zh";
  }
}

/** 与 Provider 内 `t` 同一实现的取词核（供 React 之外的 services 层错误文案同步取词）。 */
function translate(
  locale: Locale,
  key: string,
  paramsOrFallback?: Record<string, string | number> | string,
): string {
  let translation = TRANSLATIONS[locale][key];
  if (translation === undefined) {
    // Fallback to English if missing, then to an inline fallback string, then to the key itself
    translation = TRANSLATIONS["en"][key] ?? (typeof paramsOrFallback === "string" ? paramsOrFallback : key);
  }
  if (paramsOrFallback && typeof paramsOrFallback === "object") {
    for (const [k, v] of Object.entries(paramsOrFallback)) {
      translation = translation.replace(new RegExp(`\\{${k}\\}`, 'g'), String(v));
    }
  }
  return translation;
}

/**
 * tStatic — React 树之外（services / httpClient / 事件回调）的取词单源。
 *
 * <p>铁律「UI 可见文本一律 t()」的落地口：传输层抛出的错误 message 会直接进
 * 登录页/Toast（检视报告 §4.8「Session expired… 等硬编码文案」），故必须与界面
 * 语言一致；本函数按当前 locale 解析，缺词回落英文再回落键名，行为与 `t` 完全同构。</p>
 */
export function tStatic(
  key: string,
  paramsOrFallback?: Record<string, string | number> | string,
): string {
  return translate(resolveLocale(), key, paramsOrFallback);
}

/** 读取当前 locale（与 tStatic 同一解析口，供非 React 代码判断语言）。 */
export function getActiveLocale(): Locale {
  return resolveLocale();
}

export const LanguageProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [locale, setLocaleState] = useState<Locale>(resolveLocale);

  const setLocale = (newLocale: Locale) => {
    setLocaleState(newLocale);
    localStorage.setItem("ecos_locale", newLocale);
  };

  const t = (key: string, paramsOrFallback?: Record<string, string | number> | string): string =>
    translate(locale, key, paramsOrFallback);

  return (
    <LanguageContext.Provider value={{ locale, setLocale, t }}>
      {children}
    </LanguageContext.Provider>
  );
};

export const useLanguage = () => {
  const context = useContext(LanguageContext);
  if (!context) {
    throw new Error("useLanguage must be used within a LanguageProvider");
  }
  return context;
};
