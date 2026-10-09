/**
 * ObjectExplorerRelationsTab — 详情「关联对象」页签（自 ObjectExplorer.tsx JSX 原样迁出，H6-T4）
 * @license Apache-2.0
 */

import type { Dispatch, SetStateAction } from "react";
import { Plus, GitBranch, ArrowRight, ChevronRight } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { type Relation } from "./helpers";
import type { RelFormData } from "./objectExplorerTypes";

interface RelationsTabProps {
  entityCode: string;
  selectedId: string | null;
  detailRelations: Relation[];
  setRelFormData: Dispatch<SetStateAction<RelFormData>>;
  setShowRelationForm: Dispatch<SetStateAction<boolean>>;
  setSelectedId: Dispatch<SetStateAction<string | null>>;
  navigateToRelated: (targetEntityCode: string, targetObjectId: string) => void;
}

export function RelationsTab({
  entityCode,
  selectedId,
  detailRelations,
  setRelFormData,
  setShowRelationForm,
  setSelectedId,
  navigateToRelated,
}: RelationsTabProps) {
  const { styles } = useTheme();

  return (
    <div className="space-y-2">
      {/* Gap 2: Add relationship button */}
      <button type="button"
        onClick={() => {
          setRelFormData({ targetObjectId: "", targetEntityCode: entityCode, relationshipCode: "", relationshipType: "OneToMany" });
          setShowRelationForm(true);
        }}
        className="w-full text-[10px] bg-blue-50 hover:bg-blue-100 text-blue-500 border border-blue-200 rounded-lg px-3 py-2 font-semibold transition flex items-center justify-center gap-1.5"
      >
        <Plus className="w-3 h-3" />添加关系
      </button>
      {detailRelations.length === 0 ? (
        <div className={`text-center py-12 text-xs ${styles.cardTextMuted}`}>
          <GitBranch className={`w-6 h-6 mx-auto mb-2 ${styles.cardTextMuted}`} />
          无关联对象
        </div>
      ) : (
        detailRelations.map(rel => {
          const isSource = rel.sourceObjectId === selectedId;
          return (
            <div
              key={rel.id}
              onClick={() => {
                const targetCode = rel.targetEntityCode || entityCode;
                const targetId = isSource ? rel.targetObjectId : rel.sourceObjectId;
                if (targetCode === entityCode) {
                  setSelectedId(targetId);
                } else {
                  navigateToRelated(targetCode, targetId);
                }
              }}
              className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 flex items-center gap-3 hover:border-blue-500 hover:bg-blue-50/30 transition cursor-pointer`}
            >
              <div className={`p-1.5 rounded shrink-0 ${isSource ? "bg-green-50" : "bg-blue-50"}`}>
                {isSource ? (
                  <ArrowRight className="w-3.5 h-3.5 text-green-500" />
                ) : (
                  <ArrowRight className="w-3.5 h-3.5 text-blue-500 rotate-180" />
                )}
              </div>
              <div className="flex-1 min-w-0">
                <div className={`text-[11px] font-semibold ${styles.cardText}`}>{rel.relationCode}</div>
                <div className={`text-[10px] ${styles.cardTextMuted} font-mono mt-0.5`}>
                  {isSource ? "→" : "←"} {rel.targetEntityCode || "?"} · {rel.targetObjectId?.slice(0, 8)}
                </div>
              </div>
              {rel.targetData && (
                <div className={`text-[10px] ${styles.cardTextMuted} ${styles.appBg} rounded px-2 py-0.5 max-w-[120px] truncate`}>
                  {rel.targetData.name || rel.targetData.code || rel.targetObjectId?.slice(0, 8)}
                </div>
              )}
              <ChevronRight className={`w-3 h-3 ${styles.cardTextMuted} shrink-0`} />
            </div>
          );
        })
      )}
    </div>
  );
}
