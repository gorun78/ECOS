/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { createContext, useContext, useState, useEffect } from "react";
import zhTranslations from "../locales/zh-CN.json";
import enTranslations from "../locales/en.json";

export type Locale = "zh" | "en";

interface LanguageContextType {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: string) => string;
}

const TRANSLATIONS: Record<Locale, Record<string, string>> = {
  zh: zhTranslations,
  en: enTranslations,
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

  const t = (key: string): string => {
    const translation = TRANSLATIONS[locale][key];
    if (translation !== undefined) {
      return translation;
    }
    // Fallback to English if missing, then to key itself
    return TRANSLATIONS["en"][key] ?? key;
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
