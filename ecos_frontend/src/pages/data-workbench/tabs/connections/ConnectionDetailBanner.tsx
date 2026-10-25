/* Extracted from ConnectionsTab.tsx */
import React from 'react';
import LucideIcon from '../../LucideIcon';
import { getSourceIcon, connTypeBadgeText, connTypeDescText } from '../../helpers';
import type { DataConnection } from '../../types';
import { useTheme } from "../../../../components/ThemeContext";

interface ConnectionDetailBannerProps {
  conn: DataConnection;
  onTestConnection: (connId: string) => void;
  testingConnId: string | null;
  t: (key: string) => string;
}

/** 详情页顶部横幅：数据源图标/名称/类型 + 连接测试按钮 */
export default function ConnectionDetailBanner({
  conn, onTestConnection, testingConnId, t,
}: ConnectionDetailBannerProps) {
  const { styles } = useTheme();

  return (
        <div className={`p-6 border-b ${styles.cardBorder} flex justify-between items-center ${styles.appBg}/50`}>
          <div className="flex items-center gap-3">
            <div className={`p-2.5 rounded-full border ${styles.accentBorder} ${styles.badgeBg} ${styles.badgeText} flex items-center justify-center`}>
              <LucideIcon name={getSourceIcon(conn.type)} size={20} />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className={`text-sm font-bold ${styles.cardText}`}>{conn.name}</span>
                {/* #142/B4-3e-64（D-B4-93）：徽章改走 i18n 单源文案；未解析显式出「未知类型 (原值 X)」。
                    原 `uppercase` 会把中文标签与后端原值一起强推成大写，故随本行去掉。 */}
                <span className={`text-[10px] ${styles.sidebarBg} ${styles.cardTextMuted} font-mono px-2 py-0.5 rounded-full`}>
                  {connTypeBadgeText(conn, t)}
                </span>
              </div>
              {/* #142/B4-3e-65（D-B4-94）：副标题说明文案改走 CONNECTION_TYPES.descKey 单源。
                  改前这里是 helpers.ts 自带的 5 枚私有 switch 表，14 枚已知类型一律显示「未知数据源」，
                  与上一行徽章（19 枚单源）在同一块横幅里互相矛盾。 */}
              <p className={`text-xs ${styles.cardTextMuted} mt-1`}>{connTypeDescText(conn, t)}</p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button type="button"
              onClick={() => onTestConnection(conn.id)}
              disabled={testingConnId !== null}
              className={`px-3 py-1.5 ${styles.accentBg} ${styles.accentHover} ${styles.cardText} text-xs font-semibold rounded transition-all cursor-pointer flex items-center gap-1.5`}
            >
              <LucideIcon name="Wifi" size={13} />
              <span>{t("dw.conn.testBtn")}</span>
            </button>
          </div>
        </div>
  );
}
