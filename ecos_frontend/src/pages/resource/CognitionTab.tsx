/**
 * CognitionTab — 资源中心「认知资产」页签（2026-10-14 菜单重构）。
 *
 * 数据形态说明（非假真值）：认知资产实例清单由场景工作台候选评审链路管理，
 * 本页只呈现**词表层**（catalog：layers 分层图例 + C 对象目录大小 +
 * governedAssetClasses + lifecycleStates），全部来自
 * GET /api/v1/cognitive/assets/catalog，四态 fail-closed（loading/error/empty/data）。
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */
import React, { useCallback, useEffect, useState } from "react";
import { Brain, RefreshCw } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { fetchCognitiveAssetCatalog, type CognitiveAssetCatalog } from "../../services/cognitiveClient";
import logger from "../../services/logger";

interface ListState {
  loading: boolean;
  error: string | null;
}

export default function CognitionTab() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [catalog, setCatalog] = useState<CognitiveAssetCatalog | null>(null);
  const [state, setState] = useState<ListState>({ loading: true, error: null });

  const load = useCallback(async () => {
    setState((p) => ({ ...p, loading: true, error: null }));
    try {
      const c = await fetchCognitiveAssetCatalog();
      setCatalog(c);
      setState({ loading: false, error: null });
    } catch (e) {
      logger.error("resource-cognition-tab", e);
      setCatalog(null);
      setState({ loading: false, error: e instanceof Error ? e.message : String(e) });
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const layerEntries = catalog ? Object.entries(catalog.layers ?? {}) : [];
  const objectCount = catalog ? catalog.objects.length : 0;
  const hasAny =
    layerEntries.length > 0 ||
    objectCount > 0 ||
    (catalog != null && (catalog.governedAssetClasses.length > 0 || catalog.lifecycleStates.length > 0));

  return (
    <div className="flex-1 min-h-0 flex flex-col gap-3 p-4 overflow-y-auto">
      <div className={`flex items-center justify-between rounded-md border px-3 py-2 ${styles.cardBg} ${styles.cardBorder}`}>
        <span className={`inline-flex items-center gap-1.5 text-xs font-bold uppercase ${styles.cardTextMuted}`}>
          <Brain size={13} />
          {t("platform.resource.tab.cognitive")}
        </span>
        <button
          type="button"
          onClick={() => void load()}
          className={`p-1 rounded border cursor-pointer ${styles.cardBorder} ${styles.cardTextMuted} hover:opacity-80`}
          title={t("platform.decision.retry")}
        >
          <RefreshCw size={12} />
        </button>
      </div>

      {/* 四态主体：loading / error / empty / data */}
      <div className={`rounded-lg border p-4 ${styles.cardBg} ${styles.cardBorder}`}>
        {state.loading ? (
          <div className="py-8 text-[11px] flex justify-center">
            <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent opacity-60" />
          </div>
        ) : state.error ? (
          <div className={`rounded-md px-3 py-4 text-[11px] break-words ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
            <span>{t("platform.decision.loadFailed")}{state.error}</span>
            <button
              type="button"
              onClick={() => void load()}
              className={`ml-2 rounded border px-2 py-0.5 cursor-pointer align-middle ${styles.inputBorder} ${styles.inputText}`}
            >
              {t("platform.decision.retry")}
            </button>
          </div>
        ) : !hasAny ? (
          <p className={`py-8 text-center text-[11px] ${styles.cardTextMuted}`}>{t("platform.resource.cognitive.empty")}</p>
        ) : (
          <div className="grid gap-3 grid-cols-1 md:grid-cols-2 xl:grid-cols-3">
            {/* C 类对象目录规模（Brain 图标 + 对象计数，无额外文案） */}
            <div className={`rounded border p-3 flex items-center justify-between ${styles.cardBorder}`}>
              <Brain size={18} className={styles.cardTextMuted} />
              <span className="text-2xl font-mono font-bold">{objectCount}</span>
            </div>
            {/* 分层图例（layers：{A,B,C} → 服务端下发文案，只读词表） */}
            {layerEntries.length > 0 && (
              <div className={`rounded border p-3 ${styles.cardBorder}`}>
                <div className="grid gap-1.5 content-start">
                  {layerEntries.map(([layer, label]) => (
                    <div key={layer} className="flex items-center gap-2 text-[11px] min-w-0">
                      <span className={`shrink-0 inline-flex items-center justify-center w-5 h-5 rounded font-mono font-bold ${styles.accentBg} ${styles.cardText}`}>
                        {layer}
                      </span>
                      <span className={`${styles.cardText} truncate`}>{label}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
            {/* 治理资产类 + 生命周期态（服务端单源词表，chip 展示不翻译） */}
            <div className={`rounded border p-3 flex flex-wrap gap-1.5 content-start ${styles.cardBorder}`}>
              {(catalog?.governedAssetClasses ?? []).map((cls) => (
                <span key={cls} className={`text-[10px] font-mono border rounded px-1.5 py-0.5 ${styles.accentText} ${styles.accentBorder}`}>
                  {cls}
                </span>
              ))}
              {(catalog?.lifecycleStates ?? []).map((st) => (
                <span key={st} className={`text-[10px] font-mono border rounded px-1.5 py-0.5 ${styles.cardBorder}`}>
                  {st}
                </span>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
