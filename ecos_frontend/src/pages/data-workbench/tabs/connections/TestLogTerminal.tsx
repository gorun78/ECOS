/* Extracted from ConnectionsTab.tsx */
import React from 'react';
import { useTheme } from "../../../../components/ThemeContext";

interface TestLogTerminalProps {
  testingLogs: string[];
  t: (key: string) => string;
}

/** Diagnostic Log Terminal — 连接器调试面板 */
export default function TestLogTerminal({ testingLogs, t }: TestLogTerminalProps) {
  const { styles } = useTheme();

  return (
            <div className={`${styles.sidebarBg} p-4 rounded-xl text-xs font-mono ${styles.sidebarText} space-y-1.5 border ${styles.sidebarBorder} select-text leading-relaxed`}>
              <div className={`text-[10px] ${styles.cardTextMuted} tracking-wider uppercase font-semibold mb-2 border-b ${styles.sidebarBorder} pb-1 flex justify-between items-center select-none`}>
                <span>{t("dw.txt.26079a")}</span>
                <span className={`${styles.accentText}`}>JDBC API Log v1.4</span>
              </div>
              {testingLogs.map((log, i) => (
                <div key={i} className={
                  log.includes('ERROR') || log.includes('❌') ? `${styles.dangerText}` :
                  log.includes('SUCCESS') || log.includes('✅') ? `${styles.successText}` :
                  log.includes('🔑') ? `${styles.accentText}` : `${styles.cardTextMuted}`
                }>
                  {log}
                </div>
              ))}
            </div>
  );
}
