/**
 * SdNodeDrawer.tsx — 节点详情侧抽屉 (Node Drawer)
 *
 * 选中节点后从右侧滑入，按节点 type 渲染：
 *   resource → 名称/状态/子项数 + 删除/绑定 hub 按钮
 *   hub      → 状态 + 四件套触发器
 *   mind     → label + active 状态 + 摘要
 *   insight  → hash + 摘要
 *
 * @license Apache-2.0
 */

import React from "react";
import { X, Eye, LineChart, Compass, ShieldCheck } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import type {
  SdBHubData,
  SdBMindData,
  SdBInsightData,
  SdBResourceData,
  CognitiveEp,
} from "./types";
import type { Node as RfNode } from "@xyflow/react";

// ── 4 件套 → icon + t() key 映射 ──────────────────────────

const EP_DRAWER: Record<
  CognitiveEp,
  {
    icon: React.ComponentType<{ size?: number; className?: string }>;
    i18nKey: `scenario.sandbox.insight.${CognitiveEp}`;
  }
> = {
  diagnose: { icon: Eye, i18nKey: "scenario.sandbox.insight.diagnose" },
  forecast: { icon: LineChart, i18nKey: "scenario.sandbox.insight.forecast" },
  simulate: { icon: Compass, i18nKey: "scenario.sandbox.insight.simulate" },
  policy: { icon: ShieldCheck, i18nKey: "scenario.sandbox.insight.policy" },
};

// ── 主组件 ────────────────────────────────────────────────

export interface SdNodeDrawerProps {
  node: RfNode;
  onClose: () => void;
  onDeleteRes: () => void;
  onLinkHub: () => void;
  onRunInsight: (ep: CognitiveEp) => void;
}

const SdNodeDrawer: React.FC<SdNodeDrawerProps> = ({
  node,
  onClose,
  onDeleteRes,
  onLinkHub,
  onRunInsight,
}) => {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const nodeDataRaw = node.data as unknown as
    | SdBHubData
    | SdBResourceData
    | SdBMindData
    | SdBInsightData
    | undefined;
  const hubData = node.type === "hub" ? (nodeDataRaw as SdBHubData | undefined) : null;
  const resData = node.type === "resource" ? (nodeDataRaw as SdBResourceData | undefined) : null;
  const mindData = node.type === "mind" ? (nodeDataRaw as SdBMindData | undefined) : null;
  const insData = node.type === "insight" ? (nodeDataRaw as SdBInsightData | undefined) : null;

  const kindLabel =
    node.type === "hub" ? t("scenario.sandbox.drawer.hub") :
    node.type === "resource" ? t("scenario.sandbox.drawer.resource") :
    node.type === "mind" ? t("scenario.sandbox.drawer.mind") :
    node.type === "insight" ? t("scenario.sandbox.drawer.insight") :
    t("scenario.sandbox.drawer.resource");

  const fieldLabel = `text-[10px] font-semibold uppercase tracking-wide ${styles.muted}`;
  const fieldValue = `text-xs mt-0.5 ${styles.cardText}`;

  return (
    <div className="absolute right-0 top-0 bottom-0 z-40 flex flex-col">
      <div
        className={`w-[340px] max-w-[90vw] shadow-2xl h-full ${styles.cardBg} ${styles.cardBorder} border-l flex flex-col`}
        role="dialog"
        aria-label={t("scenario.sandbox.drawer.title")}
      >
        <div
          className={`px-4 py-3 border-b ${styles.cardBorder} flex items-center justify-between`}
        >
          <h3 className={`text-sm font-bold ${styles.cardText}`}>
            {t("scenario.sandbox.drawer.title")}
          </h3>
          <button
            type="button"
            onClick={onClose}
            className={`p-1 rounded cursor-pointer ${styles.sidebarHoverBg}`}
            title={t("scenario.sandbox.action.remove")}
          >
            <X size={14} className={styles.cardText} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-4 py-3 space-y-4">
          {/* 公共：id + kind */}
          <div>
            <div className={fieldLabel}>{t("scenario.sandbox.drawer.id")}</div>
            <div className={`text-xs font-mono mt-0.5 ${styles.cardText} break-all`}>
              {node.id}
            </div>
          </div>
          <div>
            <div className={fieldLabel}>{t("scenario.sandbox.drawer.category")}</div>
            <div className={`text-xs mt-0.5 ${styles.accentText}`}>{kindLabel}</div>
          </div>

          {resData && (
            <>
              <div>
                <div className={fieldLabel}>{t("scenario.sandbox.drawer.name")}</div>
                <div className={fieldValue}>{resData.targetName ?? "—"}</div>
              </div>
              <div>
                <div className={fieldLabel}>{t("scenario.sandbox.drawer.status")}</div>
                <div className={fieldValue}>{resData.status ?? "ok"}</div>
              </div>
              {typeof resData.childCount === "number" && (
                <div>
                  <div className={fieldLabel}>{t("scenario.sandbox.drawer.children")}</div>
                  <div className={fieldValue}>{resData.childCount}</div>
                </div>
              )}
              <div className="flex gap-2 pt-2">
                <button
                  type="button"
                  onClick={onDeleteRes}
                  className={`px-3 py-1.5 rounded font-medium text-xs cursor-pointer ${styles.dangerBg} ${styles.dangerText}`}
                >
                  {t("scenario.sandbox.action.remove")}
                </button>
                <button
                  type="button"
                  onClick={onLinkHub}
                  className={`px-3 py-1.5 rounded font-medium text-xs cursor-pointer ${styles.badgeBg} ${styles.badgeText}`}
                >
                  {t("scenario.sandbox.action.link")}
                </button>
              </div>
            </>
          )}

          {hubData && (
            <>
              <div>
                <div className={fieldLabel}>{t("scenario.sandbox.drawer.status")}</div>
                <div className={fieldValue}>{hubData.status}</div>
              </div>
              <div>
                <div className={fieldLabel}>{t("scenario.sandbox.hub.fourEp")}</div>
                <div className="grid grid-cols-2 gap-1.5 mt-1">
                  {(Object.keys(hubData.epEnabled) as CognitiveEp[]).map((ep) => (
                    <SdInsightRunRow key={ep} ep={ep} onRun={onRunInsight} />
                  ))}
                </div>
              </div>
              <div className={`text-[10px] ${styles.muted}`}>
                {t("scenario.sandbox.drawer.bindHint")}
              </div>
            </>
          )}

          {mindData && (
            <>
              <div>
                <div className={fieldLabel}>{t("scenario.sandbox.drawer.name")}</div>
                <div className={fieldValue}>{mindData.label ?? "—"}</div>
              </div>
              <div>
                <div className={fieldLabel}>{t("scenario.sandbox.mind.activeRadio")}</div>
                <div
                  className={`text-xs mt-0.5 ${
                    mindData.active ? styles.successText : styles.muted
                  }`}
                >
                  {mindData.active
                    ? t("scenario.sandbox.status.ACTIVE")
                    : t("scenario.sandbox.status.DRAFT")}
                </div>
              </div>
              {mindData.threeFactorSummary && (
                <div>
                  <div className={fieldLabel}>summary</div>
                  <div className={`text-xs mt-0.5 ${styles.cardTextMuted}`}>
                    {mindData.threeFactorSummary}
                  </div>
                </div>
              )}
            </>
          )}

          {insData && (
            <>
              <div>
                <div className={fieldLabel}>{t("scenario.sandbox.insight.stub")}</div>
                <div className={`text-xs mt-0.5 font-mono ${styles.cardText} break-all`}>
                  {insData.payloadHash || "—"}
                </div>
              </div>
              {insData.summary && (
                <div>
                  <div className={fieldLabel}>summary</div>
                  <div className={`text-xs mt-0.5 ${styles.cardTextMuted}`}>
                    {insData.summary}
                  </div>
                </div>
              )}
            </>
          )}
        </div>

        <div className={`px-4 py-2 text-[10px] ${styles.muted} border-t ${styles.cardBorder}`}>
          {t("scenario.sandbox.drawer.bindHint")}
        </div>
      </div>
    </div>
  );
};

// ── Insight 触发按钮（hub drawer 内）──────────────────────

const SdInsightRunRow: React.FC<{
  ep: CognitiveEp;
  onRun: (ep: CognitiveEp) => void;
}> = ({ ep, onRun }) => {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const meta = EP_DRAWER[ep];
  const Icon = meta.icon;
  return (
    <button
      type="button"
      onClick={() => onRun(ep)}
      className={`flex items-center gap-1.5 px-2 py-1.5 rounded border cursor-pointer text-xs ${styles.cardBg} ${styles.cardBorder}`}
      title={t("scenario.sandbox.action.run")}
    >
      <Icon size={13} className={styles.accentText} />
      <span>{t(meta.i18nKey)}</span>
    </button>
  );
};

export default SdNodeDrawer;
