/**
 * ObjectExplorerTimelineTab — 详情「时间线」页签（自 ObjectExplorer.tsx JSX 原样迁出，H6-T4）
 * @license Apache-2.0
 */

import type { Dispatch, SetStateAction } from "react";
import { Clock, Loader2, User, Calendar } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import type { TimelineEvent } from "../../api";
import { EVENT_LABELS } from "./helpers";

interface TimelineTabProps {
  detailTimeline: TimelineEvent[];
  timelineLoading: boolean;
  timelineTotal: number;
  setTimelinePage: Dispatch<SetStateAction<number>>;
}

export function TimelineTab({ detailTimeline, timelineLoading, timelineTotal, setTimelinePage }: TimelineTabProps) {
  const { styles } = useTheme();

  return (
    <div className="space-y-2 relative before:absolute before:left-[11px] before:top-2 before:bottom-2 before:w-px before:bg-[var(--card-border,#E2E8F0)]">
      {timelineLoading && detailTimeline.length === 0 ? (
        <div className={`text-center py-12 text-xs ${styles.cardTextMuted}`}>
          <Loader2 className={`w-6 h-6 mx-auto mb-2 ${styles.cardTextMuted} animate-spin`} />
          加载时间线...
        </div>
      ) : detailTimeline.length === 0 ? (
        <div className={`text-center py-12 text-xs ${styles.cardTextMuted}`}>
          <Clock className={`w-6 h-6 mx-auto mb-2 ${styles.cardTextMuted}`} />
          暂无操作记录
        </div>
      ) : (
        <>
          {detailTimeline.map(evt => (
            <div key={evt.id} className="flex items-start gap-3 pl-6 relative">
              <div className={`absolute left-[7px] top-1.5 w-[9px] h-[9px] rounded-full border-2 ${
                evt.eventType === "created" ? "bg-green-100 border-green-400" :
                evt.eventType === "deleted" ? "bg-red-100 border-red-400" :
                evt.eventType === "status_changed" ? "bg-blue-100 border-blue-400" :
                `${styles.appBg} ${styles.appBorder}`
              }`} />
              <div className="flex-1 min-w-0">
                <div className={`text-[11px] font-semibold ${styles.cardText}`}>
                  {EVENT_LABELS[evt.eventType] || evt.eventType}
                  {evt.eventType === "status_changed" && evt.eventDetail && (
                    <span className={`text-[10px] font-normal ${styles.cardTextMuted} ml-1`}>
                      {typeof evt.eventDetail === "object" ? `${evt.eventDetail.from} → ${evt.eventDetail.to}` : ""}
                    </span>
                  )}
                </div>
                <div className={`flex items-center gap-2 text-[10px] ${styles.cardTextMuted} mt-0.5`}>
                  <User className="w-2.5 h-2.5" />
                  <span>{evt.operator || "system"}</span>
                  <Calendar className="w-2.5 h-2.5 ml-1" />
                  <span>{evt.createdAt?.replace("T", " ").slice(0, 19)}</span>
                </div>
              </div>
            </div>
          ))}
          {/* Gap 3: Load more button */}
          {detailTimeline.length < timelineTotal && (
            <div className="pl-6 pt-2">
              <button type="button"
                onClick={() => setTimelinePage(prev => prev + 1)}
                disabled={timelineLoading}
                className={`w-full text-[10px] ${styles.appBg} ${styles.sidebarHoverBg} ${styles.cardText} border ${styles.cardBorder} rounded-lg px-3 py-2 font-semibold transition flex items-center justify-center gap-1.5 disabled:opacity-50`}
              >
                {timelineLoading ? (
                  <><Loader2 className="w-3 h-3 animate-spin" />加载中...</>
                ) : (
                  <>加载更多 ({detailTimeline.length}/{timelineTotal})</>
                )}
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
