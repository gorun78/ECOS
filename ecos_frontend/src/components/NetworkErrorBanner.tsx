/**
 * NetworkErrorBanner — PMO-43 T5 top banner for offline / backend-down.
 * Subscribes to `ecos-network-down` / `ecos-network-up` (api.ts) and the
 * browser `online` / `offline` events. `down` clears ONLY on an explicit
 * up-signal — never on a timer (G1-C-001).
 */
import React, { useCallback, useEffect, useRef, useState } from "react";
import { WifiOff, RotateCw, Wifi } from "lucide-react";
import { useTheme } from "./ThemeContext";
import { useLanguage } from "./LanguageContext";

const RESUMED_MS = 15000;
export default function NetworkErrorBanner(): React.ReactElement | null {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [offline, setOffline] = useState(typeof navigator !== "undefined" ? !navigator.onLine : false);
  const [down, setDown] = useState(false);
  const [resumed, setResumed] = useState(false);
  const resumeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const showResumed = useCallback(() => {
    setResumed(true);
    if (resumeTimerRef.current) clearTimeout(resumeTimerRef.current);
    resumeTimerRef.current = setTimeout(() => setResumed(false), RESUMED_MS);
  }, []);
  useEffect(() => {
    const onDown = () => {
      setDown(true);
      setResumed(false);
    };
    // Recovery: explicit up-signal only (G1-C-001) — no timer reset.
    const onUp = () => {
      setDown(false);
      setOffline(false);
      showResumed();
    };
    const onOffline = () => setOffline(true);

    window.addEventListener("ecos-network-down", onDown);
    window.addEventListener("ecos-network-up", onUp);
    window.addEventListener("online", onUp);
    window.addEventListener("offline", onOffline);
    return () => {
      window.removeEventListener("ecos-network-down", onDown);
      window.removeEventListener("ecos-network-up", onUp);
      window.removeEventListener("online", onUp);
      window.removeEventListener("offline", onOffline);
      if (resumeTimerRef.current) clearTimeout(resumeTimerRef.current);
    };
  }, [showResumed]);
  if (!(offline || down) && !resumed) return null;
  if (resumed) {
    return (
      <div
        role="status"
        aria-live="polite"
        className={`fixed top-0 left-0 right-0 z-[100] flex items-center gap-2 px-4 py-2 text-sm font-medium border-b ${styles.successBg} ${styles.successText} ${styles.successBorder}`}
      >
        <Wifi className="w-4 h-4 shrink-0" />
        <span>{t("network.resumed.title")}</span>
      </div>
    );
  }
  const title = offline ? t("network.reconnect.title") : t("network.error.title");
  const desc = offline ? t("network.reconnect.description") : t("network.error.description");
  return (
    <div
      role="alert"
      aria-live="assertive"
      className={`fixed top-0 left-0 right-0 z-[100] flex flex-wrap items-center gap-x-3 gap-y-1 px-4 py-2 text-sm font-medium border-b ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}
    >
      <WifiOff className="w-4 h-4 shrink-0" />
      <span className="font-semibold">{title}</span>
      <span className="opacity-80 text-xs hidden sm:inline">{desc}</span>
      <span className="ml-auto flex items-center gap-1.5 text-xs">
        <RotateCw className="w-3.5 h-3.5" />
        {t("network.retry")}
      </span>
    </div>
  );
}
