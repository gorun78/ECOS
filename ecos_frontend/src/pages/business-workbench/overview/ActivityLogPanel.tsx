/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import LucideIcon from '../LucideIcon';
import { useTheme } from '../../../components/ThemeContext';
import { auditLogs } from './auditLogs';

export default function ActivityLogPanel() {
  const { styles } = useTheme();

  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 shadow-xs lg:col-span-2 space-y-3`}>
      <h3 className={`text-xs font-semibold ${styles.cardText} flex items-center gap-1.5`}>
        <LucideIcon name="History" size={14} className={styles.cardTextMuted} />
        生命周期与审计日志 (Foundry Activity Stream)
      </h3>
      <div className={`divide-y ${styles.divider} max-h-[260px] overflow-y-auto pr-1`}>
        {auditLogs.map(log => (
          <div key={log.id} className="py-2.5 first:pt-0 last:pb-0 text-[11px] flex items-start gap-3">
            <div className="mt-0.5">
              <span className={`w-2 h-2 rounded-full inline-block ${
                log.type === 'publish' ? 'bg-blue-500' :
                log.type === 'create' ? 'bg-emerald-500' : 'bg-[var(--card,#475569)]'
              }`} />
            </div>
            <div className="flex-1 space-y-0.5">
              <div className="flex justify-between items-center text-xs">
                <span className={`font-semibold ${styles.cardText}`}>{log.action}</span>
                <span className={`text-[10px] ${styles.cardTextMuted} font-mono`}>{log.time}</span>
              </div>
              <p className={`${styles.cardTextMuted} text-[11px] leading-relaxed`}>{log.detail}</p>
              <div className={`text-[10px] ${styles.cardTextMuted} font-mono`}>操作员: {log.user}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
