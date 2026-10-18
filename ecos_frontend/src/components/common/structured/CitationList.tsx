/**
 * CitationList — 引用帧累积列表（篇13 §13.4.5：`citation` 帧累积到 `<EvidenceDrawer>` 引用列表）。
 *
 * 真值纪律：
 * - 数据只从 props 传入（`services/sseFrames` 解析出的 citation 帧），组件不合成引用；
 * - 四要素不齐（`isCitationUsable=false`）显式标注「引用要素不齐」，不折叠掉、不补齐；
 * - 空列表按 `completed` 分两种诚实态：流未收 done ⇒「等待引用」；已收 done ⇒「本轮无引用」
 *   （把"还没到"渲染成"没有"就是假绿）。
 * @license Apache-2.0
 */

import React from "react";
import { Quote } from "lucide-react";
import { useTheme } from "../../ThemeContext";
import { useLanguage } from "../../LanguageContext";
import {
  CITATION_REQUIRED_KEYS,
  isCitationUsable,
  type AgentLoopCitationFrame,
} from "../../../services/sseFrames";

export interface CitationListProps {
  citations: AgentLoopCitationFrame[];
  /** done 帧是否已收到（决定空列表的诚实态文案） */
  completed: boolean;
  /** 点开某条引用（父级据此打开 EvidenceDrawer） */
  onSelect: (index: number) => void;
  /** 当前选中项（高亮用，null 表示抽屉未打开） */
  selected?: number | null;
}

/** 缺失要素点名（把缺的键名列出来，便于定位是引擎未上报还是解析走样） */
function missingKeys(frame: AgentLoopCitationFrame): string {
  return CITATION_REQUIRED_KEYS.filter(
    (key) => (frame as unknown as Record<string, unknown>)[key] === "-",
  ).join(", ");
}

export default function CitationList({
  citations,
  completed,
  onSelect,
  selected = null,
}: CitationListProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  if (citations.length === 0) {
    return (
      <p className={`text-[11px] ${styles.cardTextMuted}`}>
        {completed ? t("common.evidence.list.none") : t("common.evidence.list.waiting")}
      </p>
    );
  }

  return (
    <ul className="space-y-1" aria-label={t("common.evidence.list.title")}>
      {citations.map((frame, index) => {
        const usable = isCitationUsable(frame);
        return (
          <li key={`${frame.forecastRunId}-${index}`}>
            <button
              type="button"
              onClick={() => onSelect(index)}
              aria-current={selected === index}
              className={`w-full flex items-start gap-2 rounded-lg border px-2 py-1.5 text-left cursor-pointer ${styles.cardBorder} ${
                selected === index ? styles.cardBg : ""
              }`}
            >
              <Quote className={`w-3 h-3 shrink-0 mt-0.5 ${usable ? styles.accentText : styles.warningText}`} aria-hidden="true" />
              <span className="flex-1 min-w-0 space-y-0.5">
                <span className={`block truncate font-mono text-[10px] ${styles.cardText}`}>
                  {frame.forecastRunId} · {frame.caliberId}
                </span>
                <span className={`block text-[10px] ${usable ? styles.cardTextMuted : styles.warningText}`}>
                  {usable
                    ? `${frame.version} · ${frame.asOfTime}`
                    : t("common.evidence.list.incomplete", { missing: missingKeys(frame) })}
                </span>
              </span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
