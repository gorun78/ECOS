/**
 * EngineConfigTab — 平台治理「引擎配置」页签（route #/platform-governance）。
 *
 * 消费契约（只读，后端单源，不修改）：
 *   GET /api/v1/system/config  经 fetchSysConfigs() → SysConfigGrouped[]
 *   （默认值事实源为后端；本组件无编辑 UI，fail-closed）。
 *
 * 渲染策略：按 config_group 逐分组一节（分组标题 + item 表格）；
 *   - 四态：loading / error（重试按钮重拉）/ empty（fetchSysConfigs 吞错返回 [] → 空态）/ data
 *   - 每行：key / value（空 → 破折号）/ type / edition / isConsumed 徽标
 *   - Delta：value 与 default_value 不一致（default 非空时）→ 细粒 warning 标记
 *
 * 文案仅用 platform.governance.* 既有 locale 键（不新增键）；
 * 颜色仅 useTheme() 语义 token（禁硬编码色板 / hex）。
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */
import React, { useCallback, useEffect, useState } from "react";
import { KeyRound, RefreshCw, SlidersHorizontal } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { fetchSysConfigs, type SysConfigGrouped, type SysConfigItem } from "../../api/dictConfigClient";
import logger from "../../services/logger";

function errMsg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

export default function EngineConfigTab() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [groups, setGroups] = useState<SysConfigGrouped[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchSysConfigs();
      setGroups(data);
    } catch (e) {
      // fetchSysConfigs 本身吞错返回 []；此处罩住意外异常才落 error 态
      logger.error("governance-config", e);
      setGroups([]);
      setError(errMsg(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  /** value 与后端 default_value 偏离（默认值存在且非空才判定）。 */
  const isDelta = (item: SysConfigItem): boolean => {
    if (!item.default_value || item.default_value === "") return false;
    return item.value !== item.default_value;
  };

  if (loading) {
    return (
      <div className="flex-1 min-h-0 flex flex-col items-center justify-center gap-2 p-4">
        <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent opacity-60" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex-1 min-h-0 flex flex-col gap-3 p-4">
        <div className={`rounded-lg border p-4 flex flex-col gap-2 ${styles.dangerBg} ${styles.dangerBorder}`}>
          <div className={`flex items-center gap-2 ${styles.dangerText}`}>
            <KeyRound size={14} />
            <span className="text-xs font-bold">{t("platform.governance.config.loadFailed")}</span>
          </div>
          <span className="text-[11px] font-mono break-words">{error}</span>
          <button
            type="button"
            onClick={() => void load()}
            className={`inline-flex items-center gap-1 self-start rounded border px-2.5 py-1 text-[11px] font-semibold cursor-pointer ${styles.dangerBorder} ${styles.dangerText} hover:opacity-80`}
          >
            <RefreshCw size={12} />
            {t("platform.governance.deployment.retry")}
          </button>
        </div>
      </div>
    );
  }

  if (groups.length === 0) {
    return (
      <div className="flex-1 min-h-0 flex flex-col gap-3 p-4 overflow-y-auto">
        <p className="text-xs">{t("platform.governance.config.desc")}</p>
        <div className={`rounded-lg border p-8 text-center text-xs ${styles.cardBg} ${styles.cardBorder} ${styles.cardTextMuted}`}>
          {t("platform.governance.config.empty")}
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 min-h-0 flex flex-col gap-3 p-4 overflow-y-auto">
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs shrink-0">{t("platform.governance.config.desc")}</p>
        <button
          type="button"
          onClick={() => void load()}
          className={`inline-flex items-center gap-1 rounded border px-2.5 py-1 text-[11px] font-semibold cursor-pointer shrink-0 ${styles.cardBorder} ${styles.cardTextMuted} hover:opacity-80`}
        >
          <RefreshCw size={12} />
          {t("platform.governance.deployment.retry")}
        </button>
      </div>

      {groups.map((g) => (
        <div key={g.group} className={`rounded-lg border overflow-hidden ${styles.cardBg} ${styles.cardBorder}`}>
          <div
            className={`flex items-center gap-1.5 border-b px-3 py-2 ${styles.cardBorder}`}
            data-group={g.group}
          >
            <SlidersHorizontal size={12} className={styles.cardTextMuted} />
            <span className="text-xs font-bold uppercase">{g.group}</span>
          </div>
          <table className="w-full text-[11px]">
            <thead>
              <tr className={`border-b text-left ${styles.cardBorder} ${styles.cardTextMuted}`}>
                <th className="px-3 py-2 font-bold uppercase">{t("platform.governance.config.group")}</th>
                <th className="px-3 py-2 font-bold uppercase">{t("platform.dictionary.value")}</th>
                <th className="px-3 py-2 font-bold uppercase">{t("platform.dictionary.type")}</th>
                <th className="px-3 py-2 font-bold uppercase">{t("platform.gateway.edition.col.edition")}</th>
              </tr>
            </thead>
            <tbody>
              {g.items.map((item) => (
                <tr key={item.key} className={`border-b last:border-b-0 ${styles.cardBorder}`}>
                  <td className="px-3 py-2">
                    <span className="inline-flex items-center gap-1.5 flex-wrap">
                      <KeyRound size={11} className={styles.cardTextMuted} />
                      <span className="font-mono font-bold break-all">{item.key}</span>
                      <span
                        className={`inline-flex items-center rounded border px-1.5 py-0.5 text-[10px] font-mono font-bold ${item.isConsumed ? `${styles.successBg} ${styles.successText}` : `${styles.infoBg} ${styles.infoText}`} ${styles.cardBorder}`}
                      >
                        {t(item.isConsumed ? "platform.gateway.routes.yes" : "platform.gateway.routes.no")}
                      </span>
                      {isDelta(item) && (
                        <span
                          className={`inline-block h-1.5 w-1.5 rounded-full align-middle ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}
                          title={`${item.default_value} → ${item.value}`}
                        />
                      )}
                    </span>
                  </td>
                  <td className={`px-3 py-2 font-mono break-all ${item.value ? styles.cardText : styles.cardTextMuted}`}>
                    {item.value ? item.value : t("platform.resource.countUnknown")}
                  </td>
                  <td className="px-3 py-2 font-mono">{item.type}</td>
                  <td className={`px-3 py-2 font-mono ${item.edition ? styles.cardText : styles.cardTextMuted}`}>
                    {item.edition ? item.edition : t("platform.resource.countUnknown")}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ))}
    </div>
  );
}
