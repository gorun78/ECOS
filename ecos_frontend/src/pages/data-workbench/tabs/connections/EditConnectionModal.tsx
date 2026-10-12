/* Extracted from ConnectionsTab.tsx */
import React, { useState } from 'react';
import type { DataConnection } from '../../types';
import { useTheme } from "../../../../components/ThemeContext";
import { useLanguage } from "../../../../components/LanguageContext";

// ── Edit Connection Modal ─────────────────────────────────
export default function EditConnectionModal({ conn, onSave, onCancel }: {
  conn: DataConnection;
  onSave: (conn: DataConnection) => void;
  onCancel: () => void;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [name, setName] = useState(conn.name);
  const [host, setHost] = useState(conn.config.host || '');
  const [port, setPort] = useState(conn.config.port || 5432);
  const [username, setUsername] = useState(conn.config.username || '');
  const [password, setPassword] = useState('');

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center" style={{ background: 'rgba(0,0,0,0.5)' }}>
      <div className={`w-96 rounded-xl border ${styles.cardBorder} ${styles.cardBg} shadow-2xl p-6 space-y-4`}>
        <h3 className={`text-sm font-bold ${styles.cardText}`}>{t('dw.conn.editTitle')}</h3>
        <div className="space-y-3">
          <div>
            <label className={`text-[10px] ${styles.cardTextMuted} uppercase block mb-1`}>{t('dw.conn.name')}</label>
            <input value={name} onChange={e => setName(e.target.value)}
              className={`w-full p-2 border ${styles.inputBorder} rounded text-xs ${styles.inputBg} ${styles.inputText}`} />
          </div>
          <div>
            <label className={`text-[10px] ${styles.cardTextMuted} uppercase block mb-1`}>{t('dw.conn.host')}</label>
            <input value={host} onChange={e => setHost(e.target.value)}
              className={`w-full p-2 border ${styles.inputBorder} rounded text-xs font-mono ${styles.inputBg} ${styles.inputText}`} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={`text-[10px] ${styles.cardTextMuted} uppercase block mb-1`}>{t('dw.conn.port')}</label>
              <input type="number" value={port} onChange={e => setPort(Number(e.target.value))}
                className={`w-full p-2 border ${styles.inputBorder} rounded text-xs font-mono ${styles.inputBg} ${styles.inputText}`} />
            </div>
            <div>
              <label className={`text-[10px] ${styles.cardTextMuted} uppercase block mb-1`}>{t('dw.conn.username')}</label>
              <input value={username} onChange={e => setUsername(e.target.value)}
                className={`w-full p-2 border ${styles.inputBorder} rounded text-xs font-mono ${styles.inputBg} ${styles.inputText}`} />
            </div>
          </div>
          <div>
            <label className={`text-[10px] ${styles.cardTextMuted} uppercase block mb-1`}>{t('dw.conn.editPassword')}</label>
            <input type="password" value={password} onChange={e => setPassword(e.target.value)} placeholder="******"
              className={`w-full p-2 border ${styles.inputBorder} rounded text-xs font-mono ${styles.inputBg} ${styles.inputText}`} />
          </div>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onCancel}
            className={`px-4 py-1.5 text-xs rounded border ${styles.cardBorder} ${styles.cardTextMuted} cursor-pointer hover:${styles.appBg}`}>
            {t('dw.conn.cancel')}
          </button>
          <button type="button" onClick={() => onSave({ ...conn, name, config: { ...conn.config, host, port, username, password: password || conn.config.password } })}
            className={`px-4 py-1.5 text-xs rounded ${styles.accentBg} ${styles.accentHover} ${styles.cardText} font-semibold cursor-pointer`}>
            {t('dw.conn.save')}
          </button>
        </div>
      </div>
    </div>
  );
}
