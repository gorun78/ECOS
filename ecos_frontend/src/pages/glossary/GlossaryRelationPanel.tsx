/**
 * GlossaryRelationPanel — 术语词条关系维护（关系 Tab）。
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
import ConfirmDialog from "../../components/common/ConfirmDialog";
import ErrorPanel from "../../components/common/status/ErrorPanel";
import {
  listGlossaryRelations,
  createGlossaryRelation,
  deleteGlossaryRelation,
  readGlossaryError,
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
  /** 关系列表失败态（与「暂无关系」空态互斥，避免错误伪装空态） */
  const [loadError, setLoadError] = useState<{ reason: string; traceId?: string } | null>(null);
  /** 待删除关系边（非空即打开确认弹窗，替代 window.confirm） */
  const [deleteTarget, setDeleteTarget] = useState<GlossaryRelation | null>(null);

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
      setLoadError(null);
    } catch (e) {
      const { message, traceId } = readGlossaryError(e);
      // 失败态独立呈现（错误面板 + 重试），不把失败渲染成「暂无关系」
      setLoadError({ reason: `${t("glossary.toast.load_failed")}: ${message}`, traceId });
    } finally {
      setLoading(false);
    }
  }, [termId, t]);

  useEffect(() => {
    void loadRelations();
  }, [loadRelations]);

  /** Esc 关闭删除确认（modal 键盘出口） */
  useEffect(() => {
    if (!deleteTarget) {
      return;
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setDeleteTarget(null);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [deleteTarget]);

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
      const { message } = readGlossaryError(e);
      showToast("error", `${t("glossary.relation.create_failed")}: ${message}`);
    } finally {
      setSaving(false);
    }
  };

  /** 删除关系边：先开确认门（window.confirm 无主题/无障碍，报告 §4.3 行114） */
  const handleDelete = (rel: GlossaryRelation) => setDeleteTarget(rel);

  /** 确认后的实际删除（写成功重拉列表 — 铁律 §4.8-3） */
  const runDelete = async () => {
    if (!deleteTarget) {
      return;
    }
    const target = deleteTarget;
    setSaving(true);
    try {
      await deleteGlossaryRelation(target.id);
      showToast("success", t("glossary.relation.deleted"));
      setDeleteTarget(null);
      await loadRelations();
    } catch (e) {
      const { message } = readGlossaryError(e);
      showToast("error", `${t("glossary.relation.delete_failed")}: ${message}`);
    } finally {
      setSaving(false);
    }
  };

  /** 输入控件统一样式（聚焦态走主题 accentRing，禁裸色板 §七.1） */
  const inputClass = `w-full px-3 py-2 rounded-lg border text-xs outline-none
    focus:ring-2 focus:ring-inset focus-visible:ring-2 ${styles.accentRing}
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
          <button type="button"
            className={`px-4 py-2 rounded-lg text-xs font-semibold transition disabled:opacity-50
              flex items-center gap-1 cursor-pointer ${styles.accentBg} ${styles.accentHover} ${styles.accentSolidText}`}
            onClick={handleAdd}
            disabled={saving}
          >
            {saving ? <RotateCw size={14} className="animate-spin" aria-hidden="true" /> : <Plus size={14} aria-hidden="true" />}
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
            <RotateCw size={16} className="animate-spin" aria-hidden="true" />
            {t("glossary.loading")}
          </div>
        ) : loadError ? (
          <div className="p-4">
            <ErrorPanel
              reason={loadError.reason}
              traceId={loadError.traceId}
              onRetry={() => void loadRelations()}
            />
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
                <span className={`font-semibold truncate ${styles.cardText}`} title={rel.fromTermName}>
                  {rel.fromTermName}
                </span>
                <ArrowRight size={12} className={styles.muted} aria-hidden="true" />
                <span className={`px-1.5 py-0.5 rounded text-[10px] font-semibold shrink-0
                  ${styles.badgeBg} ${styles.badgeText}`}>
                  {relationTypeDict.getLabel(rel.relationType)}
                </span>
                <ArrowRight size={12} className={styles.muted} aria-hidden="true" />
                <span className={`font-semibold truncate ${styles.cardText}`} title={rel.toTermName}>
                  {rel.toTermName}
                </span>
                {rel.description && (
                  <span className={`truncate ${styles.muted}`} title={rel.description}>· {rel.description}</span>
                )}
                <span className={`ml-auto text-[10px] shrink-0 ${styles.muted}`}>w={rel.weight}</span>
                <button type="button"
                  className={`p-1 rounded cursor-pointer ${styles.sidebarHoverBg} ${styles.dangerText}`}
                  title={t("glossary.delete")}
                  aria-label={t("glossary.delete")}
                  onClick={() => handleDelete(rel)}
                  disabled={saving}
                >
                  <Trash2 size={13} aria-hidden="true" />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 删除关系确认门（应用内 modal：role=alertdialog + aria-modal 由 ConfirmDialog 提供，Esc 关闭） */}
      <ConfirmDialog
        open={!!deleteTarget}
        title={t("glossary.delete_confirm_title")}
        message={t("glossary.relation.delete_confirm")}
        confirmText={t("glossary.delete")}
        cancelText={t("glossary.cancel")}
        variant="danger"
        onConfirm={() => void runDelete()}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  );
}