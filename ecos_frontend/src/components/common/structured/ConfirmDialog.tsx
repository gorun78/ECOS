/**
 * ConfirmDialog（公共收敛版）— 不可逆操作二次确认（详设-08 §2.2 末行「发布/提交类按钮强制走此组件」）。
 *
 * 契约：
 * - impactScope（影响范围）必填：必须写明本次操作影响的对象与后果；
 * - newVersion（新版本号）：发布/提交类操作必填（后端开发规范 §十一 版本×Git 归档），
 *   取值为引擎回执中的真实版本号，禁止前端伪造 hash；终止运行类操作（Kill Switch）
 *   不产生新版本，可缺省，此时版本行不渲染；
 * - 存量旧份（`src/components/common/ConfirmDialog.tsx`、`src/pages/user-management/ConfirmDialog.tsx`）
 *   本批不改动，待 R-49 页面挂载批次逐页收敛到本组件。
 *
 * 结构性颜色走 useTheme() token；遮罩用 overlayBg token；文案全部 t()。
 * @license Apache-2.0
 */

import React from "react";
import { AlertTriangle } from "lucide-react";
import { useTheme } from "../../ThemeContext";
import { useLanguage } from "../../LanguageContext";
import Button from "../Button";

export interface ConfirmDialogProps {
  open: boolean;
  /** 对话框标题（调用方 t() 传入） */
  title: string;
  /** 补充说明（可选） */
  message?: string;
  /** 影响范围：必填，业务语言描述被影响的对象与后果 */
  impactScope: string;
  /** 新版本号：发布/提交类必填，取引擎回执真实值 */
  newVersion?: string;
  /** 确认按钮文案，默认 common.confirm.ok */
  confirmLabel?: string;
  /** 取消按钮文案，默认 common.cancel */
  cancelLabel?: string;
  onConfirm: () => void;
  onCancel: () => void;
}

export default function ConfirmDialog({
  open,
  title,
  message,
  impactScope,
  newVersion,
  confirmLabel,
  cancelLabel,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center" role="dialog" aria-modal="true" aria-label={title}>
      <div className={`absolute inset-0 ${styles.overlayBg}`} onClick={onCancel} aria-hidden="true" />
      <div className={`relative z-50 w-full max-w-md mx-4 rounded-xl border shadow-2xl p-6 space-y-4 ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center gap-2">
          <AlertTriangle className={`w-5 h-5 ${styles.warningText}`} aria-hidden="true" />
          <h3 className={`text-sm font-semibold ${styles.cardText}`}>{title}</h3>
        </div>

        <p className={`text-xs font-bold ${styles.dangerText}`}>{t("common.confirm.irreversible")}</p>

        {message && <p className={`text-xs leading-normal ${styles.cardText}`}>{message}</p>}

        <dl className="space-y-2 text-xs">
          <div>
            <dt className={styles.cardTextMuted}>{t("common.confirm.impact_scope")}</dt>
            <dd className={styles.cardText}>{impactScope}</dd>
          </div>
          {newVersion && (
            <div>
              <dt className={styles.cardTextMuted}>{t("common.confirm.new_version")}</dt>
              <dd className={`font-mono ${styles.cardText}`}>{newVersion}</dd>
            </div>
          )}
        </dl>

        <div className="flex gap-2 justify-end pt-2">
          <Button variant="secondary" size="sm" onClick={onCancel}>
            {cancelLabel ?? t("common.cancel")}
          </Button>
          <Button variant="danger" size="sm" onClick={onConfirm}>
            {confirmLabel ?? t("common.confirm.ok")}
          </Button>
        </div>
      </div>
    </div>
  );
}
