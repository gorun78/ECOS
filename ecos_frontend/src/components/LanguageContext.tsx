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
import ontologyEn from "../locales/ontology/en.json";
import aiworkbenchEn from "../locales/aiworkbench/en.json";
import dwEn from "../locales/dw/en.json";
import knowledgeEn from "../locales/knowledge/en.json";
import secEn from "../locales/sec/en.json";
import commonEn from "../locales/common/en.json";

export type Locale = "zh" | "en";

interface LanguageContextType {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: string, params?: Record<string, string | number>) => string;
}

const TRANSLATIONS: Record<Locale, Record<string, string>> = {
  zh: { ...ontologyZh, ...aiworkbenchZh, ...dwZh, ...knowledgeZh, ...secZh, ...commonZh },
  en: { ...ontologyEn, ...aiworkbenchEn, ...dwEn, ...knowledgeEn, ...secEn, ...commonEn },
};

const LanguageContext = createContext<LanguageContextType | undefined>(undefined);

export const LanguageProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [locale, setLocaleState] = useState<Locale>(() => {
    // Chinese is the absolute default
    const saved = localStorage.getItem("ecos_locale");
    return (saved as Locale) || "zh";
  });

  const setLocale = (newLocale: Locale) => {
    setLocaleState(newLocale);
    localStorage.setItem("ecos_locale", newLocale);
  };

  const t = (key: string, params?: Record<string, string | number>): string => {
    let translation = TRANSLATIONS[locale][key];
    if (translation === undefined) {
      // Fallback to English if missing, then to key itself
      translation = TRANSLATIONS["en"][key] ?? key;
    }
    if (params) {
      for (const [k, v] of Object.entries(params)) {
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
