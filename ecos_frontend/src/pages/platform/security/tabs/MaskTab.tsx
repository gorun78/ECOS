/**
 * MaskTab — 数据脱敏规则 + 内置示例（原始值 → 脱敏值对照）。
 * 只读展示：脱敏策略由 security-engine 内置 8 类（email/phone/idCard/bankCard/
 * amount/address/staffRef/freeText），页面展示支持规则清单 + 逐类示例。
 */
import React, { useEffect, useState } from "react";
import { RefreshCw, Inbox, Eye, EyeOff } from "lucide-react";
import { useLanguage } from "../../../../components/LanguageContext";
import { useTheme } from "../../../../components/ThemeContext";
import { fetchMaskingDemo, type MaskingDemo } from "../../../../services/security";

export default function MaskTab() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [data, setData] = useState<MaskingDemo | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await fetchMaskingDemo());
    } catch (e) {
      setError(String((e as Error)?.message ?? ""));
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    void load();
  }, []);

  if (loading && !data) {
    return <div className={`h-24 rounded border ${styles.appBorder} animate-pulse`} aria-busy="true" />;
  }
  if (error && !data) {
    return <p role="alert" className={`text-xs ${styles.dangerText}`}>{error}</p>;
  }
  if (!data) return null;

  return (
    <div className="space-y-4" data-testid="mask-tab">
      <div className="flex items-center justify-between gap-2">
        <span className={`text-xs ${styles.muted}`}>{data.description ?? ""}</span>
        <button
          type="button"
          onClick={() => void load()}
          aria-label={t("platform.gateway.action.refresh")}
          className={`p-1.5 border rounded ${styles.inputBorder} ${styles.inputText} cursor-pointer hover:opacity-80`}
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
        </button>
      </div>

      <div>
        <div className={`text-[10px] uppercase tracking-wider ${styles.muted} mb-1.5`}>
          {t("platform.security.mask.supportedRules")}
        </div>
        <div className="flex flex-wrap gap-1.5">
          {data.supportedRules.map((r) => (
            <span key={r} data-testid={`mask-rule-${r}`} className={`inline-flex items-center gap-1 px-2 py-0.5 rounded border ${styles.appBorder} ${styles.accentText} text-[11px] font-mono`}>
              <Eye className="w-3 h-3" />
              {r}
            </span>
          ))}
        </div>
      </div>

      <div>
        <div className={`text-[10px] uppercase tracking-wider ${styles.muted} mb-1.5`}>
          {t("platform.security.mask.samples")}
        </div>
        {data.samples.length === 0 ? (
          <div className={`flex items-center gap-2 text-xs ${styles.muted} p-4`}>
            <Inbox className="w-4 h-4" />{t("platform.security.state.empty")}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className={`w-full text-xs border-collapse ${styles.cardText}`} data-testid="mask-table">
              <thead>
                <tr className={`border-b ${styles.appBorder} text-[10px] uppercase tracking-wider ${styles.muted}`}>
                  <th className="text-left px-2 py-1.5">{t("platform.security.mask.rule")}</th>
                  <th className="text-left px-2 py-1.5">{t("platform.security.mask.raw")}</th>
                  <th className="text-left px-2 py-1.5">{t("platform.security.mask.masked")}</th>
                </tr>
              </thead>
              <tbody className={`divide-y ${styles.appBorder}`}>
                {data.samples.map((s, i) => (
                  <tr key={`${s.rule}-${i}`}>
                    <td className="px-2 py-1.5 font-mono">{s.rule}</td>
                    <td className="px-2 py-1.5 font-mono break-all">
                      <span className={`inline-flex items-center gap-1 ${styles.dangerText}`}><EyeOff className="w-3 h-3" />{s.raw}</span>
                    </td>
                    <td className="px-2 py-1.5 font-mono break-all">
                      <span className={`inline-flex items-center gap-1 ${styles.successText}`}><Eye className="w-3 h-3" />{s.masked}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
