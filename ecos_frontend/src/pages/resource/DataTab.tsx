/**
 * DataTab — 资源中心「数据资产」页签（2026-10-14 菜单重构）。
 *
 * 内嵌 DataAssetsDashboard（真实均已收口：/api/v1/datanet/assets + levels +
 * categories，错误态自渲染，非 Mock）。toast/t 由组件既有 Props 契约直传，
 * 此处经 useToast() 取 showToast 单源（可选链路：ToastProvider 保底）。
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */
import React from "react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { useToast } from "../../components/common/Toast";
import DataAssetsDashboard from "../DataAssetsDashboard";

export default function DataTab() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const { showToast } = useToast();

  return (
    <div className="flex-1 min-h-0 flex flex-col p-4 gap-3">
      <p className={`text-xs ${styles.cardTextMuted}`}>{t("platform.resource.data.embedded")}</p>
      <div className="flex-1 min-h-0 flex flex-col">
        <DataAssetsDashboard showToast={showToast} t={t} />
      </div>
    </div>
  );
}
