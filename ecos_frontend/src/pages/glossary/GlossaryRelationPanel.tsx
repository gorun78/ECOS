/**
 * GlossaryRelationPanel — Wiki 词条关系维护（关系 Tab）。
 *
 * 职责：新增词条关系边（起点 / 边类型 / 终点 / 权重 / 说明）与关系列表增删；
 * 增删成功后强制重拉列表（架构铁律 §4.8-3 写后刷新）。
 * 数据/接口：services/glossary 的 listGlossaryRelations / createGlossaryRelation / deleteGlossaryRelation。
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useCallback, useEffect, useState } from "react";
import { ArrowRight, Plus, RotateCw, Trash2, Link2 } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { useDict } from "../../hooks/useDict";
import {
  listGlossaryRelations,
  createGlossaryRelation,
  deleteGlossaryRelation,
  type GlossaryRelation,
  type GlossaryTerm,
} from "../../services/glossary";

interface GlossaryRelationPanelProps {
  /** 当前选中词条 id */
  termId: number;
  /** 全部词条（用于起点/终点下拉候选） */
  terms: GlossaryTerm[];
  /** 轻提示 */
  showToast: (type: "success" | "error", msg: string) => void;
}

export default function GlossaryRelationPanel({
  termId,
  terms,
  showToast,
}: GlossaryRelationPanelProps) {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const relationTypeDict = useDict("glossary_relation_type", locale);

  const [relations, setRelations] = useState<GlossaryRelation[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  // ── 新增表单 ──
  const [fromTermId, setFromTermId] = useState<number>(termId);
  const [toTermId, setToTermId] = useState<number | "">("");
  const [relationType, setRelationType] = useState("RELATED");
  const [weight, setWeight] = useState("100");
  const [description, setDescription] = useState("");

  // 切换词条时重置表单并在重拉列表后把起点对齐当前词条
  useEffect(() => {
    setFromTermId(termId);
    setToTermId("");
    setRelationType("RELATED");
    setWeight("100");
    setDescription("");
  }, [termId]);

  /** 拉取当前词条的全部关系（termId = 任一端命中） */
  const loadRelations = useCallback(async () => {
    setLoading(true);
    try {
      const data = await listGlossaryRelations({ termId });
      setRelations(data);
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e);
      showToast("error", `${t("glossary.toast.load_failed")}: ${message}`);
    } finally {
      setLoading(false);
    }
  }, [termId, showToast, t]);

  useEffect(() => {
    void loadRelations();
  }, [loadRelations]);

  /** 新增关系边 */
  const handleAdd = async () => {
    if (toTermId === "" || fromTermId === 0) {
      showToast("error", t("glossary.toast.relation_required"));
      return;
    }
    if (fromTermId === toTermId) {
      showToast("error", t("glossary.toast.relation_self"));
      return;
    }
    const parsedWeight = Number(weight);
    setSaving(true);
    try {
      await createGlossaryRelation({
        fromTermId,
        toTermId,
        relationType,
        weight: Number.isFinite(parsedWeight) ? parsedWeight : 100,
        description: description.trim() || undefined,
      });
      showToast("success", t("glossary.relation.created"));
      setToTermId("");
      setDescription("");
      await loadRelations();
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e);
      showToast("error", `${t("glossary.relation.create_failed")}: ${message}`);
    } finally {
      setSaving(false);
    }
  };

  /** 删除关系边（二次确认） */
  const handleDelete = async (id: number) => {
    if (!window.confirm(t("glossary.relation.delete_confirm"))) {
      return;
    }
    setSaving(true);
    try {
      await deleteGlossaryRelation(id);
      showToast("success", t("glossary.relation.deleted"));
      await loadRelations();
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e);
      showToast("error", `${t("glossary.relation.delete_failed")}: ${message}`);
    } finally {
      setSaving(false);
    }
  };

  /** 输入控件统一样式 */
  const inputClass = `w-full px-3 py-2 rounded-lg border text-xs outline-none focus:border-indigo-400
    disabled:opacity-50 ${styles.inputBorder} ${styles.inputBg} ${styles.inputText}`;
  const labelClass = `text-[11px] font-semibold mb-1 ${styles.cardTextMuted}`;

  return (
    <div className="space-y-4">
      {/* ═══ 新增关系 ═══ */}
      <div className={`rounded-lg border p-4 ${styles.cardBorder} ${styles.cardBg}`}>
        <h3 className={`text-sm font-semibold mb-3 flex items-center gap-1.5 ${styles.cardText}`}>
          <Link2 size={14} />
          {t("glossary.relation.new")}
        </h3>

        <div className="grid grid-cols-1 sm:grid-cols-[1fr_1fr_1fr] gap-3">
          {/* 起点 */}
          <div>
            <div className={labelClass}>{t("glossary.relation.from")}</div>
            <select
              className={inputClass}
              value={fromTermId}
              onChange={(e) => setFromTermId(Number(e.target.value))}
              disabled={saving}
            >
              {terms.length === 0 && <option value={fromTermId}>{termId}</option>}
              {terms.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </div>

          {/* 边类型 */}
          <div>
            <div className={labelClass}>{t("glossary.relation.type")}</div>
            <select
              className={inputClass}
              value={relationType}
              onChange={(e) => setRelationType(e.target.value)}
              disabled={saving}
            >
              <option value="">{t("glossary.relation.type_placeholder")}</option>
              {relationTypeDict.getOptions().map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </div>

          {/* 终点 */}
          <div>
            <div className={labelClass}>{t("glossary.relation.to")}</div>
            <select
              className={inputClass}
              value={toTermId}
              onChange={(e) => setToTermId(e.target.value ? Number(e.target.value) : "")}
              disabled={saving}
            >
              <option value="">{t("glossary.relation.to_placeholder")}</option>
              {terms
                .filter((item) => item.id !== fromTermId)
                .map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
            </select>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-[120px_1fr] gap-3 mt-3">
          {/* 权重 */}
          <div>
            <div className={labelClass}>{t("glossary.relation.weight")}</div>
            <input
              type="number"
              className={inputClass}
              value={weight}
              onChange={(e) => setWeight(e.target.value)}
              disabled={saving}
            />
          </div>
          {/* 说明 */}
          <div>
            <div className={labelClass}>{t("glossary.relation.description")}</div>
            <input
              className={inputClass}
              placeholder={t("glossary.relation.description_placeholder")}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              disabled={saving}
            />
          </div>
        </div>

        <div className="mt-3">
          <button
            className="px-4 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-xs
              font-semibold transition disabled:opacity-50 flex items-center gap-1"
            onClick={handleAdd}
            disabled={saving}
          >
            {saving ? <RotateCw size={14} className="animate-spin" /> : <Plus size={14} />}
            {t("glossary.relation.add")}
          </button>
        </div>
      </div>

      {/* ═══ 关系列表 ═══ */}
      <div className={`rounded-lg border ${styles.cardBorder}`}>
        <div className={`flex items-center justify-between px-4 py-2.5 border-b ${styles.cardBorder}`}>
          <span className={`text-sm font-semibold ${styles.cardText}`}>
            {t("glossary.relation.list_title")}
            <span className={`text-xs font-normal ml-1 ${styles.muted}`}>({relations.length})</span>
          </span>
        </div>

        {loading ? (
          <div className={`flex items-center justify-center py-10 text-xs gap-2 ${styles.muted}`}>
            <RotateCw size={16} className="animate-spin" />
            {t("glossary.loading")}
          </div>
        ) : relations.length === 0 ? (
          <div className={`py-10 text-center text-xs ${styles.muted}`}>
            {t("glossary.relation.empty")}
          </div>
        ) : (
          <div>
            {relations.map((rel) => (
              <div
                key={rel.id}
                className={`flex items-center gap-2 px-4 py-2.5 border-b text-xs ${styles.cardBorder}`}
              >
                <span className={`font-semibold ${styles.cardText}`}>{rel.fromTermName}</span>
                <ArrowRight size={12} className={styles.muted} />
                <span className={`px-1.5 py-0.5 rounded text-[10px] font-semibold
                  ${styles.badgeBg} ${styles.badgeText}`}>
                  {relationTypeDict.getLabel(rel.relationType)}
                </span>
                <ArrowRight size={12} className={styles.muted} />
                <span className={`font-semibold ${styles.cardText}`}>{rel.toTermName}</span>
                {rel.description && (
                  <span className={`truncate ${styles.muted}`}>· {rel.description}</span>
                )}
                <span className={`ml-auto text-[10px] ${styles.muted}`}>w={rel.weight}</span>
                <button
                  className="p-1 rounded hover:bg-red-500/10 text-red-400"
                  title={t("glossary.delete")}
                  onClick={() => handleDelete(rel.id)}
                  disabled={saving}
                >
                  <Trash2 size={13} />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}