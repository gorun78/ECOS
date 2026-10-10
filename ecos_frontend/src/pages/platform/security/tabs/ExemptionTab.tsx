/**
 * ExemptionTab — ST03-A 豁免登记表（只读）。
 * 设计 B §3.1 #1：豁免 tab 只读，登记表为唯一源，页面无写入口。
 * 数据来源 = security asset 目录中 exemption_ref 非空的条目
 * （/api/v1/security/assets），列 = 对象键 / 种类 / 敏感级 / guard 组合 /
 * 豁免登记号 / 属主角色。
 */
import React, { useCallback, useEffect, useState } from "react";
import { RefreshCw, Inbox, FileSearch } from "lucide-react";
import { useLanguage } from "../../../../components/LanguageContext";
import { useTheme } from "../../../../components/ThemeContext";
import { listSecurityAssets, type SecurityAsset } from "../../../../services/security";

export default function ExemptionTab() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [assets, setAssets] = useState<SecurityAsset[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const all = await listSecurityAssets();
      // 豁免登记 = exemption_ref 非空的资产
      setAssets(all.filter((a) => a.exemptionRef && a.exemptionRef.trim().length > 0));
    } catch (e) {
      setError(String((e as Error)?.message ?? ""));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading && !assets.length) {
    return <div className={`h-24 rounded border ${styles.appBorder} animate-pulse`} aria-busy="true" />;
  }
  if (error && !assets.length) {
    return <p role="alert" className={`text-xs ${styles.dangerText}`}>{error}</p>;
  }

  return (
    <div className="space-y-3" data-testid="exemption-tab">
      <div className="flex items-center justify-between gap-2">
        <span className={`inline-flex items-center gap-1.5 text-xs ${styles.muted}`}>
          <FileSearch className="w-3.5 h-3.5" />
          {t("platform.security.exemption.readonly")}
        </span>
        <button
          type="button"
          onClick={() => void load()}
          aria-label={t("platform.gateway.action.refresh")}
          className={`p-1.5 border rounded ${styles.inputBorder} ${styles.inputText} cursor-pointer hover:opacity-80`}
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
        </button>
      </div>
      {assets.length === 0 ? (
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-md p-6 flex flex-col items-center gap-2`}>
          <Inbox className={`w-7 h-7 ${styles.muted}`} />
          <div className={`text-sm ${styles.cardText}`}>{t("platform.security.exemption.empty")}</div>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className={`w-full text-xs border-collapse ${styles.cardText}`} data-testid="exemption-table">
            <thead>
              <tr className={`border-b ${styles.appBorder} text-[10px] uppercase tracking-wider ${styles.muted}`}>
                <th className="text-left px-2 py-1.5">{t("platform.security.exemption.col.assetKey")}</th>
                <th className="text-left px-2 py-1.5">{t("platform.security.exemption.col.kind")}</th>
                <th className="text-left px-2 py-1.5">{t("platform.security.exemption.col.sensitivity")}</th>
                <th className="text-left px-2 py-1.5">{t("platform.security.exemption.col.guard")}</th>
                <th className="text-left px-2 py-1.5">{t("platform.security.exemption.col.exemptionRef")}</th>
                <th className="text-left px-2 py-1.5">{t("platform.security.exemption.col.owner")}</th>
              </tr>
            </thead>
            <tbody className={`divide-y ${styles.appBorder}`}>
              {assets.map((a) => (
                <tr key={a.assetKey}>
                  <td className="px-2 py-1.5 font-mono break-all max-w-xs">{a.assetKey}</td>
                  <td className="px-2 py-1.5 font-mono">{a.kind ?? "—"}</td>
                  <td className="px-2 py-1.5 font-mono">{a.sensitivity ?? "—"}</td>
                  <td className="px-2 py-1.5 font-mono">{a.guardCombo ?? "—"}</td>
                  <td className="px-2 py-1.5 font-mono">{a.exemptionRef}</td>
                  <td className="px-2 py-1.5">{a.ownerRole || "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
