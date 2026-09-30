/* Extracted from ConnectionsTab.tsx */
import React from 'react';
import LucideIcon from '../../LucideIcon';
import type { DataConnection } from '../../types';
import { useTheme } from "../../../../components/ThemeContext";

interface ConnectionParamsCardProps {
  conn: DataConnection;
  t: (key: string) => string;
}

/** ① 连接参数（横向扁平卡，紧凑） */
export default function ConnectionParamsCard({ conn, t }: ConnectionParamsCardProps) {
  const { styles } = useTheme();

  return (
          <div className={`${styles.appBg} border ${styles.cardBorder} rounded-xl p-4`}>
            <div className="flex flex-wrap items-end gap-6">
              <div className="flex items-center gap-2 mr-6">
                <LucideIcon name="Settings" size={12} className={`${styles.cardTextMuted}`} />
                <span className={`text-[10px] font-bold uppercase tracking-wider ${styles.cardTextMuted}`}>{t("dw.connConfigParams")}</span>
              </div>
              {conn.config.host && (
                <div className="text-xs">
                  <span className={`text-[10px] ${styles.cardTextMuted} uppercase block font-mono`}>{t("dw.txt.16e578")}</span>
                  <span className={`font-mono font-medium ${styles.cardText}`}>{conn.config.host}</span>
                </div>
              )}
              {conn.config.port && (
                <div className="text-xs">
                  <span className={`text-[10px] ${styles.cardTextMuted} uppercase block font-mono`}>{t("dw.txt.4016cf")}</span>
                  <span className={`font-mono font-medium ${styles.cardText}`}>{conn.config.port}</span>
                </div>
              )}
              {conn.config.username && (
                <div className="text-xs">
                  <span className={`text-[10px] ${styles.cardTextMuted} uppercase block font-mono`}>{t("dw.txt.1169ed")}</span>
                  <span className={`font-mono font-medium ${styles.cardText}`}>{conn.config.username}</span>
                </div>
              )}
              {conn.config.bucket && (
                <div className="text-xs">
                  <span className={`text-[10px] ${styles.cardTextMuted} uppercase block font-mono`}>{t("dw.txt.eb9003")}</span>
                  <span className={`font-mono font-medium ${styles.cardText} truncate block`}>{conn.config.bucket}</span>
                </div>
              )}
              {conn.config.endpointUrl && (
                <div className="text-xs">
                  <span className={`text-[10px] ${styles.cardTextMuted} uppercase block font-mono`}>{t("dw.txt.3cd968")}</span>
                  <span className={`font-mono font-medium ${styles.cardText} truncate block`}>{conn.config.endpointUrl}</span>
                </div>
              )}
              <div className="text-xs">
                <span className={`text-[10px] ${styles.cardTextMuted} uppercase block font-mono`}>{t("dw.txt.165c7b")}</span>
                <span className={`${styles.cardTextMuted} text-[11px] font-medium`}>{conn.config.lastTested || t("dw.neverTested")}</span>
              </div>
            </div>
          </div>
  );
}
