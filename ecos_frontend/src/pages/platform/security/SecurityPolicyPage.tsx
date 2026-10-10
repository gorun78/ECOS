/**
 * SecurityPolicyPage — 安全策略管理页 (#/platform/security/policies)
 * 设计：docs/30-设计/详细设计-01-安全域-security与审计底座-2026-09-28.md §三 B 章 3.1。
 *
 * 6 Tab：rls | cls | mask | abac | crypto-audit | exemption。
 * 左竖排 Tab（lg）/ 横向（md）/ 折叠为下拉（sm）用 useMediaQuery。
 * 主题 token 走 useTheme()；i18n 键 platform.security.*；每个 Tab 独立文件 ≤800 行。
 * Tab role="tablist" + ←/→ 键盘；离线 503 显式穿透（各 Tab 独立错误条）。
 */
import React, { useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  ShieldCheck,
  Table2,
  EyeOff,
  GitBranch,
  KeyRound,
  FileSearch,
} from "lucide-react";
import { useLanguage } from "../../../components/LanguageContext";
import { useTheme } from "../../../components/ThemeContext";
import { useMediaQuery } from "../../../hooks/useMediaQuery";
import {
  listRlsPolicies,
  listClsPolicies,
  listAbacPolicies,
  type RlsPolicy,
  type ClsPolicy,
  type AbacPolicy,
} from "../../../services/security";
import RlsPolicyTab from "./tabs/RlsPolicyTab";
import ClsPolicyTab from "./tabs/ClsPolicyTab";
import MaskTab from "./tabs/MaskTab";
import AbacTab from "./tabs/AbacTab";
import CryptoAuditTab from "./tabs/CryptoAuditTab";
import ExemptionTab from "./tabs/ExemptionTab";

type TabId = "rls" | "cls" | "mask" | "abac" | "crypto-audit" | "exemption";
const TAB_IDS: TabId[] = ["rls", "cls", "mask", "abac", "crypto-audit", "exemption"];

const TAB_META: Record<TabId, { labelKey: string; icon: React.ComponentType<{ className?: string }> }> = {
  rls: { labelKey: "platform.security.tab.rls", icon: ShieldCheck },
  cls: { labelKey: "platform.security.tab.cls", icon: Table2 },
  mask: { labelKey: "platform.security.tab.mask", icon: EyeOff },
  abac: { labelKey: "platform.security.tab.abac", icon: GitBranch },
  "crypto-audit": { labelKey: "platform.security.tab.cryptoAudit", icon: KeyRound },
  exemption: { labelKey: "platform.security.tab.exemption", icon: FileSearch },
};

function normalizeTab(v: string | null): TabId {
  return TAB_IDS.includes(v as TabId) ? (v as TabId) : "rls";
}

export default function SecurityPolicyPage() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = normalizeTab(searchParams.get("tab"));
  const isMobile = useMediaQuery("(max-width: 767px)");

  // 分页数据
  const [rls, setRls] = useState<RlsPolicy[]>([]);
  const [rlsLoading, setRlsLoading] = useState(false);
  const [rlsError, setRlsError] = useState<string | undefined>(undefined);
  const [cls, setCls] = useState<ClsPolicy[]>([]);
  const [clsLoading, setClsLoading] = useState(false);
  const [abac, setAbac] = useState<AbacPolicy[]>([]);
  const [abacLoading, setAbacLoading] = useState(false);

  const tabRefs = useRef<Record<TabId, HTMLButtonElement | null>>({
    rls: null, cls: null, mask: null, abac: null, "crypto-audit": null, exemption: null,
  });

  const setTab = useCallback((next: TabId) => {
    setSearchParams((prev) => {
      const sp = new URLSearchParams(prev);
      sp.set("tab", next);
      return sp;
    });
    const el = tabRefs.current[next];
    el?.focus?.();
  }, [setSearchParams]);

  // 需要列表数据的 Tab
  const loadRls = useCallback(async () => {
    setRlsLoading(true); setRlsError(undefined);
    try { setRls(await listRlsPolicies()); }
    catch (e) { setRlsError(String((e as Error)?.message ?? "")); }
    finally { setRlsLoading(false); }
  }, []);
  const loadCls = useCallback(async () => {
    setClsLoading(true);
    try { setCls(await listClsPolicies()); }
    catch { /* 静默：表格空 */ }
    finally { setClsLoading(false); }
  }, []);
  const loadAbac = useCallback(async () => {
    setAbacLoading(true);
    try { setAbac((await listAbacPolicies()).items); }
    catch { setAbac([]); }
    finally { setAbacLoading(false); }
  }, []);

  useEffect(() => {
    if (tab === "rls") void loadRls();
    else if (tab === "cls") void loadCls();
    else if (tab === "abac") void loadAbac();
  }, [tab, loadRls, loadCls, loadAbac]);

  const onTabKeyDown = (e: React.KeyboardEvent) => {
    const idx = TAB_IDS.indexOf(tab);
    if (e.key === "ArrowRight" || e.key === "ArrowDown") {
      e.preventDefault(); setTab(TAB_IDS[(idx + 1) % TAB_IDS.length]);
    } else if (e.key === "ArrowLeft" || e.key === "ArrowUp") {
      e.preventDefault(); setTab(TAB_IDS[(idx - 1 + TAB_IDS.length) % TAB_IDS.length]);
    } else if (e.key === "Home") {
      e.preventDefault(); setTab(TAB_IDS[0]);
    } else if (e.key === "End") {
      e.preventDefault(); setTab(TAB_IDS[TAB_IDS.length - 1]);
    }
  };

  // 移动端：Tab 折叠为下拉选择
  if (isMobile) {
    return (
      <div className={`h-full flex flex-col ${styles.appBg} ${styles.appText}`}>
        <header className={`flex items-center gap-2 px-4 pt-4 pb-3 border-b ${styles.appBorder}`}>
          <ShieldCheck className={`w-4 h-4 ${styles.accentText}`} />
          <h1 className={`text-base font-semibold ${styles.cardText}`} data-testid="sp-page-title">
            {t("platform.security.title")}
          </h1>
        </header>
        <div className={`px-4 py-2 border-b ${styles.appBorder}`}>
          <select
            role="tablist"
            aria-label={t("platform.security.title")}
            data-testid="sp-tab-select"
            value={tab}
            onChange={(e) => setTab(e.target.value as TabId)}
            className={`w-full px-2 py-2 rounded border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} text-sm cursor-pointer`}
          >
            {TAB_IDS.map((id) => (
              <option key={id} value={id}>{t(TAB_META[id].labelKey)}</option>
            ))}
          </select>
        </div>
        <div className="flex-1 overflow-y-auto p-4 scrollbar-thin">
          <MobileTabBody tab={tab} rls={rls} rlsLoading={rlsLoading} rlsError={rlsError}
            cls={cls} clsLoading={clsLoading} abac={abac} abacLoading={abacLoading}
            onReloadRls={loadRls} onReloadCls={loadCls} />
        </div>
      </div>
    );
  }

  return (
    <div className={`h-full flex ${styles.appBg} ${styles.appText}`}>
      {/* 左竖排 Tab */}
      <nav
        role="tablist"
        aria-label={t("platform.security.title")}
        data-testid="sp-tablist"
        className={`w-[200px] shrink-0 border-r ${styles.appBorder} p-3 space-y-1 overflow-y-auto`}
      >
        {TAB_IDS.map((id) => {
          const Icon = TAB_META[id].icon;
          const active = tab === id;
          return (
            <button type="button"
              key={id}
              ref={(el) => { tabRefs.current[id] = el; }}
              role="tab"
              id={`sp-tab-${id}`}
              data-testid={`sp-tab-${id}`}
              aria-selected={active}
              aria-controls={`sp-tabpanel-${id}`}
              tabIndex={active ? 0 : -1}
              onClick={() => setTab(id)}
              onKeyDown={onTabKeyDown}
              className={`w-full flex items-center gap-2 px-3 py-2 rounded-md text-xs font-medium cursor-pointer transition
                ${active ? `${styles.accentBg} text-white` : `${styles.muted} hover:opacity-80`}`}
            >
              <Icon className="w-4 h-4" />
              {t(TAB_META[id].labelKey)}
            </button>
          );
        })}
      </nav>

      {/* 右内容面板 */}
      <div
        role="tabpanel"
        id={`sp-tabpanel-${tab}`}
        aria-labelledby={`sp-tab-${tab}`}
        className="flex-1 overflow-y-auto p-6 scrollbar-thin"
      >
        <h2 className={`text-lg font-semibold ${styles.cardText} mb-4`}>
          {t(TAB_META[tab].labelKey)}
        </h2>
        <TabBody tab={tab} rls={rls} rlsLoading={rlsLoading} rlsError={rlsError}
          cls={cls} clsLoading={clsLoading} abac={abac} abacLoading={abacLoading}
          onReloadRls={loadRls} onReloadCls={loadCls} />
      </div>
    </div>
  );
}

function TabBody(props: TabProps) {
  const { tab, rls, rlsLoading, rlsError, cls, clsLoading, abac, abacLoading, onReloadRls, onReloadCls } = props;
  switch (tab) {
    case "rls":
      return <RlsPolicyTab policies={rls} loading={rlsLoading} error={rlsError} onRefresh={onReloadRls} />;
    case "cls":
      return <ClsPolicyTab policies={cls} loading={clsLoading} />;
    case "mask":
      return <MaskTab />;
    case "abac":
      return <AbacTab policies={abac} loading={abacLoading} />;
    case "crypto-audit":
      return <CryptoAuditTab />;
    case "exemption":
      return <ExemptionTab />;
    default:
      return null;
  }
}

// 移动端 / 桌面共用内容分发（无视觉差异，只是内容 body）
function MobileTabBody(props: TabProps) {
  return <TabBody {...props} />;
}

interface TabProps {
  tab: TabId;
  rls: RlsPolicy[];
  rlsLoading: boolean;
  rlsError?: string;
  cls: ClsPolicy[];
  clsLoading: boolean;
  abac: AbacPolicy[];
  abacLoading: boolean;
  onReloadRls: () => void;
  onReloadCls: () => void;
}
