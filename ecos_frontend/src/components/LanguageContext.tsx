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
  zh: { ...ontologyZh, ...aiworkbenchZh, ...dwZh, ...knowledgeZh, ...secZh, ...commonZh, ...copilotZh, ...dashboardZh, ...scenarioZh, ...cognitionZh },
  en: { ...ontologyEn, ...aiworkbenchEn, ...dwEn, ...knowledgeEn, ...secEn, ...commonEn, ...copilotEn, ...dashboardEn, ...scenarioEn, ...cognitionEn },
};

const LanguageContext = createContext<LanguageContextType | undefined>(undefined);

export const LanguageProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [locale, setLocaleState] = useState<Locale>(() => {
    // Chinese is the absolute default; tests can force via `__ecos_test_locale`
    // (set on globalThis by *.test.tsx beforeEach) to assert i18n keys.
    const g = globalThis as unknown as { __ecos_test_locale?: Locale };
    if (g.__ecos_test_locale) {
      return g.__ecos_test_locale;
    }
    const saved = localStorage.getItem("ecos_locale");
    return (saved as Locale) || "zh";
  });

  const setLocale = (newLocale: Locale) => {
    setLocaleState(newLocale);
    localStorage.setItem("ecos_locale", newLocale);
  };

  const t = (key: string, paramsOrFallback?: Record<string, string | number> | string): string => {
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
  };

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
