/**
 * OfflineNotice — 离线/超时提示条（详设-08 §2.3「离线/超时」行：BFF 504 / fetch abort）。
 *
 * 契约：明确「后端不可达」文案 + 「已保留本地草稿」说明（与 useDraftOnFailure 配套消费）；
 * 重试回调由调用方发起。不含任何伪成功/伪状态。
 * @license Apache-2.0
 */

import React from "react";
import { RefreshCw, WifiOff } from "lucide-react";
import { useTheme } from "../../ThemeContext";
import { useLanguage } from "../../LanguageContext";
import Button from "../Button";

export interface OfflineNoticeProps {
  /** 是否展示（调用方依据请求失败/离线探测传入） */
  visible: boolean;
  /** 服务恢复后可继续提交（草稿在本地保留）—— 与 useDraftOnFailure.preserving 联动 */
  draftKept?: boolean;
  /** 重试回调 */
  onRetry?: () => void;
}

export default function OfflineNotice({ visible, draftKept = true, onRetry }: OfflineNoticeProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  if (!visible) return null;

  return (
    <div
      role="status"
      className={`flex flex-col sm:flex-row sm:items-center gap-2 rounded-xl border px-4 py-3 text-xs ${styles.dangerBg} ${styles.dangerBorder}`}
    >
      <WifiOff className={`w-4 h-4 shrink-0 ${styles.dangerText}`} aria-hidden="true" />
      <div className="flex-1 space-y-0.5">
        <p className={`font-semibold ${styles.dangerText}`}>{t("common.status.offline_title")}</p>
        <p className={styles.cardTextMuted}>{t("common.status.offline_desc")}</p>
        {draftKept && <p className={styles.cardText}>{t("common.status.draft_kept")}</p>}
      </div>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          <RefreshCw className="w-3.5 h-3.5 mr-1" aria-hidden="true" />
          {t("common.status.retry")}
        </Button>
      )}
    </div>
  );
}
