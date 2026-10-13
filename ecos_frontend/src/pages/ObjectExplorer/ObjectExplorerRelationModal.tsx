/**
 * ObjectExplorerRelationModal — 添加关系弹窗（自 ObjectExplorer.tsx JSX 原样迁出，H6-T4）
 * 目标实体下拉仅用真实 entityList（FALLBACK_ENTITIES 演示兜底已移除）。
 * @license Apache-2.0
 */

import type { Dispatch, SetStateAction } from "react";
import { Link2, X, Loader2 } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import type { EntityListItem } from "../../services/ontologyApi";
import { FormField } from "./ObjectExplorerFormField";
import type { RelFormData } from "./objectExplorerTypes";

interface ObjectExplorerRelationModalProps {
  entityList: EntityListItem[];
  relFormData: RelFormData;
  setRelFormData: Dispatch<SetStateAction<RelFormData>>;
  setShowRelationForm: Dispatch<SetStateAction<boolean>>;
  relCreating: boolean;
  handleCreateRelation: () => Promise<void>;
}

export function ObjectExplorerRelationModal({
  entityList,
  relFormData,
  setRelFormData,
  setShowRelationForm,
  relCreating,
  handleCreateRelation,
}: ObjectExplorerRelationModalProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className={`fixed inset-0 z-50 flex items-center justify-center ${styles.overlayBg}`} onClick={() => setShowRelationForm(false)}>
      <div
        className={`${styles.cardBg} rounded-xl shadow-2xl w-full sm:w-[400px] animate-in zoom-in-95 mx-4 sm:mx-auto`}
        onClick={e => e.stopPropagation()}
      >
        <div className={`p-4 border-b ${styles.cardBorder} flex items-center justify-between`}>
          <h3 className={`text-sm font-bold ${styles.cardText} flex items-center gap-2`}>
            <Link2 className={`w-4 h-4 ${styles.infoText}`} />{t("dw.objectExplorer.relation.title")}
          </h3>
          <button type="button" onClick={() => setShowRelationForm(false)} className={`${styles.sidebarHoverBg} hover:opacity-70 rounded p-1 transition`}>
            <X className={`w-4 h-4 ${styles.cardTextMuted}`} />
          </button>
        </div>
        <div className="p-4 space-y-3">
          <FormField label={t("dw.objectExplorer.relation.targetId")} required value={relFormData.targetObjectId} onChange={v => setRelFormData(prev => ({ ...prev, targetObjectId: v }))} />
          <div>
            <label className={`block text-[10px] font-semibold ${styles.cardTextMuted} uppercase tracking-wider mb-1`}>{t("dw.objectExplorer.relation.targetEntity")}</label>
            <select
              value={relFormData.targetEntityCode}
              onChange={e => setRelFormData(prev => ({ ...prev, targetEntityCode: e.target.value }))}
              disabled={entityList.length === 0}
              className={`w-full ${styles.appBg} border ${styles.cardBorder} rounded-lg px-3 py-2 text-xs ${styles.cardText} outline-none focus:ring-1 focus:ring-current transition font-mono disabled:opacity-60 disabled:cursor-not-allowed`}
            >
              {entityList.length === 0 ? (
                <option value="">{t("dw.objectExplorer.entity.unavailable")}</option>
              ) : (
                entityList.map(e => (
                  <option key={e.code} value={e.code}>{e.code}</option>
                ))
              )}
            </select>
          </div>
          <FormField label={t("dw.objectExplorer.relation.code")} required placeholder={t("dw.objectExplorer.relation.codePlaceholder")} value={relFormData.relationshipCode} onChange={v => setRelFormData(prev => ({ ...prev, relationshipCode: v }))} />
          <div>
            <label className={`block text-[10px] font-semibold ${styles.cardTextMuted} uppercase tracking-wider mb-1`}>{t("dw.objectExplorer.relation.type")}</label>
            <select
              value={relFormData.relationshipType}
              onChange={e => setRelFormData(prev => ({ ...prev, relationshipType: e.target.value }))}
              className={`w-full ${styles.appBg} border ${styles.cardBorder} rounded-lg px-3 py-2 text-xs ${styles.cardText} outline-none focus:ring-1 focus:ring-current transition font-mono`}
            >
              <option value="OneToOne">OneToOne</option>
              <option value="OneToMany">OneToMany</option>
              <option value="ManyToMany">ManyToMany</option>
            </select>
          </div>
        </div>
        <div className={`p-4 border-t ${styles.cardBorder} flex gap-2 justify-end`}>
          <button type="button"
            onClick={() => setShowRelationForm(false)}
            className={`px-4 py-2 text-xs font-semibold ${styles.cardTextMuted} hover:opacity-70 ${styles.sidebarHoverBg} rounded-lg transition`}
          >
            {t("common.cancel")}
          </button>
          <button type="button"
            onClick={handleCreateRelation}
            disabled={relCreating || !relFormData.targetObjectId || !relFormData.relationshipCode}
            className={`px-4 py-2 text-xs font-semibold ${styles.accentBg} ${styles.accentText} hover:opacity-90 rounded-lg transition disabled:opacity-50 flex items-center gap-1.5`}
          >
            {relCreating ? <><Loader2 className="w-3 h-3 animate-spin" />{t("dw.objectExplorer.relation.creating")}</> : t("dw.objectExplorer.relation.create")}
          </button>
        </div>
      </div>
    </div>
  );
}
