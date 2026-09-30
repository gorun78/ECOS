/**
 * ObjectExplorerDetailPanel — 右侧详情面板（头部/状态流转/页签/页签内容调度，自 ObjectExplorer.tsx JSX 原样迁出，H6-T4）
 * @license Apache-2.0
 */

import type { Dispatch, SetStateAction } from "react";
import {
  Box, AlertCircle, X, Edit3, Loader2, ArrowRightLeft,
  ChevronRight, FileText, Link2, Clock, Trash2, ChevronDown,
} from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import type { ObjectData, SchemaProperty, TimelineEvent } from "../../api";
import { STATUS_COLORS, type Relation } from "./helpers";
import type { AvailableTransition, RelFormData } from "./objectExplorerTypes";
import { PropertiesTab } from "./ObjectExplorerPropertiesTab";
import { RelationsTab } from "./ObjectExplorerRelationsTab";
import { TimelineTab } from "./ObjectExplorerTimelineTab";

/** Derive display name from object data */
const displayName = (obj: ObjectData) => obj.name || obj.code || obj.id?.slice(0, 8) || "—";

interface DetailPanelProps {
  entityCode: string;
  selectedId: string | null;
  detail: ObjectData | null;
  detailLoading: boolean;
  schema: SchemaProperty[];
  activeDetailTab: "properties" | "relations" | "timeline";
  setActiveDetailTab: Dispatch<SetStateAction<"properties" | "relations" | "timeline">>;
  detailRelations: Relation[];
  detailTimeline: TimelineEvent[];
  timelineLoading: boolean;
  timelineTotal: number;
  setTimelinePage: Dispatch<SetStateAction<number>>;
  availableTransitions: AvailableTransition[];
  showStatusDropdown: boolean;
  setShowStatusDropdown: Dispatch<SetStateAction<boolean>>;
  statusChanging: boolean;
  handleStatusChange: (transitionCode: string) => Promise<void>;
  openEditForm: () => void;
  handleDelete: (id: string) => Promise<void>;
  setRelFormData: Dispatch<SetStateAction<RelFormData>>;
  setShowRelationForm: Dispatch<SetStateAction<boolean>>;
  setSelectedId: Dispatch<SetStateAction<string | null>>;
  navigateToRelated: (targetEntityCode: string, targetObjectId: string) => void;
}

export function ObjectExplorerDetailPanel({
  entityCode,
  selectedId,
  detail,
  detailLoading,
  schema,
  activeDetailTab,
  setActiveDetailTab,
  detailRelations,
  detailTimeline,
  timelineLoading,
  timelineTotal,
  setTimelinePage,
  availableTransitions,
  showStatusDropdown,
  setShowStatusDropdown,
  statusChanging,
  handleStatusChange,
  openEditForm,
  handleDelete,
  setRelFormData,
  setShowRelationForm,
  setSelectedId,
  navigateToRelated,
}: DetailPanelProps) {
  const { styles } = useTheme();

  return (
    <div className={`w-full lg:w-[420px] shrink-0 flex flex-col ${styles.inputBg} min-w-0`}>
      {!selectedId ? (
        <div className={`flex-1 flex items-center justify-center ${styles.cardTextMuted} text-xs`}>
          <div className="text-center">
            <ChevronRight className={`w-10 h-10 mx-auto mb-3 ${styles.cardTextMuted}`} />
            选择左侧对象查看详情
          </div>
        </div>
      ) : detailLoading ? (
        <div className="flex-1 flex items-center justify-center">
          <Loader2 className={`w-6 h-6 ${styles.cardTextMuted} animate-spin`} />
        </div>
      ) : detail ? (
        <>
          {/* Detail header */}
          <div className={`p-4 border-b ${styles.cardBorder} shrink-0`}>
            <div className="flex items-start justify-between mb-2">
              <div className="min-w-0">
                <h2 className={`text-sm font-bold ${styles.cardText} flex items-center gap-2 truncate`}>
                  <Box className="w-4 h-4 text-blue-500 shrink-0" />
                  {displayName(detail)}
                </h2>
                <div className={`text-[10px] ${styles.cardTextMuted} font-mono mt-0.5`}>
                  {detail.entityCode} · {detail.id}
                </div>
              </div>
            </div>

            {/* Status + Actions row */}
            <div className="flex items-center gap-2 flex-wrap">
              {/* Status badge + transition dropdown */}
              <div className="relative">
                <button
                  onClick={() => setShowStatusDropdown(!showStatusDropdown)}
                  disabled={statusChanging}
                  className={`text-[10px] px-2 py-1 rounded border font-semibold flex items-center gap-1 transition disabled:opacity-50 ${STATUS_COLORS[detail.status] || STATUS_COLORS.Draft}`}
                >
                  {detail.status || "Draft"}
                  <ChevronDown className="w-2.5 h-2.5" />
                </button>
                {showStatusDropdown && availableTransitions.length > 0 && (
                  <div className={`absolute top-full left-0 mt-1 ${styles.cardBg} border ${styles.cardBorder} rounded-lg shadow-lg py-1 z-30 min-w-[120px]`}>
                    {availableTransitions.map(t => (
                      <button
                        key={t.transitionCode}
                        onClick={() => handleStatusChange(t.transitionCode)}
                        className={`w-full text-left px-3 py-1.5 text-[10px] font-semibold ${styles.cardText} hover:bg-blue-50 hover:text-blue-500 transition flex items-center gap-2`}
                      >
                        <ArrowRightLeft className="w-2.5 h-2.5" />
                        → {t.toStatus}
                        {t.transitionName && <span className={`${styles.cardTextMuted} font-normal ml-1`}>({t.transitionName})</span>}
                      </button>
                    ))}
                  </div>
                )}
              </div>

              {/* Edit button */}
              <button
                onClick={openEditForm}
                className={`text-[10px] ${styles.appBg} ${styles.sidebarHoverBg} ${styles.cardText} border ${styles.cardBorder} rounded px-2 py-1 font-semibold transition flex items-center gap-1`}
              >
                <Edit3 className="w-2.5 h-2.5" />编辑
              </button>

              {/* Delete button */}
              <button
                onClick={() => handleDelete(detail.id)}
                className="text-[10px] bg-red-50 hover:bg-red-100 text-red-600 border border-red-200 rounded px-2 py-1 font-semibold transition flex items-center gap-1"
              >
                <Trash2 className="w-2.5 h-2.5" />删除
              </button>
            </div>
          </div>

          {/* Tab bar */}
          <div className={`flex border-b ${styles.cardBorder} shrink-0 px-4 gap-0`}>
            {(["properties", "relations", "timeline"] as const).map(tab => (
              <button
                key={tab}
                onClick={() => setActiveDetailTab(tab)}
                className={`px-3 py-2 text-[11px] font-semibold border-b-2 transition ${
                  activeDetailTab === tab
                    ? "border-blue-500 text-blue-500"
                    : `border-transparent ${styles.cardTextMuted} hover:text-blue-400`
                }`}
              >
                {tab === "properties" && <><FileText className="w-3 h-3 inline mr-1" />基本属性</>}
                {tab === "relations" && <><Link2 className="w-3 h-3 inline mr-1" />关联对象 ({detailRelations.length})</>}
                {tab === "timeline" && <><Clock className="w-3 h-3 inline mr-1" />时间线 ({detailTimeline.length})</>}
              </button>
            ))}
          </div>

          {/* Tab content */}
          <div className="flex-1 overflow-y-auto p-4">
            {activeDetailTab === "properties" && (
              <PropertiesTab detail={detail} schema={schema} />
            )}

            {activeDetailTab === "relations" && (
              <RelationsTab
                entityCode={entityCode}
                selectedId={selectedId}
                detailRelations={detailRelations}
                setRelFormData={setRelFormData}
                setShowRelationForm={setShowRelationForm}
                setSelectedId={setSelectedId}
                navigateToRelated={navigateToRelated}
              />
            )}

            {activeDetailTab === "timeline" && (
              <TimelineTab
                detailTimeline={detailTimeline}
                timelineLoading={timelineLoading}
                timelineTotal={timelineTotal}
                setTimelinePage={setTimelinePage}
              />
            )}
          </div>
        </>
      ) : (
        <div className={`flex-1 flex items-center justify-center ${styles.cardTextMuted} text-xs`}>
          <div className="text-center">
            <AlertCircle className={`w-8 h-8 mx-auto mb-2 ${styles.cardTextMuted}`} />
            详情暂不可用
          </div>
        </div>
      )}
    </div>
  );
}
