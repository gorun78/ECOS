/**
 * SaveSearchModal — 保存搜索结果弹窗
 * @license Apache-2.0
 */
import React from 'react';
import { Bookmark, X } from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';

interface Props {
  newSearchName: string;
  setNewSearchName: (v: string) => void;
  setShowSaveModal: (v: boolean) => void;
  handleSaveSearch: () => void;
}

export default function SaveSearchModal({ newSearchName, setNewSearchName, setShowSaveModal, handleSaveSearch }: Props) {
  const { styles } = useTheme();
  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-3xs flex items-center justify-center z-50">
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl shadow-2xl p-5 w-96 space-y-4`}>
        <div className={`flex justify-between items-center pb-2 border-b ${styles.divider}`}>
          <h3 className={`text-xs font-semibold ${styles.cardText} flex items-center gap-1.5`}><Bookmark size={13} className="text-blue-600" />保存当前过滤器为对象列表</h3>
          <button onClick={() => setShowSaveModal(false)} className={`${styles.cardTextMuted} hover:opacity-70`}><X size={14} /></button>
        </div>
        <div><label className={`text-[10px] ${styles.cardTextMuted} block mb-1`}>输入对象列表名称</label><input type="text" placeholder="例如：旧金山基地待检修飞机" value={newSearchName} onChange={e => setNewSearchName(e.target.value)} className={`w-full h-8 text-[11px] ${styles.inputBg} ${styles.inputText} border ${styles.cardBorder} rounded px-2.5 focus:border-blue-500 focus:outline-hidden`} /></div>
        <div className="flex justify-end gap-2 pt-1 text-[11px]"><button onClick={() => setShowSaveModal(false)} className={`h-8 px-3 rounded ${styles.appBg} hover:opacity-70 ${styles.cardText} font-semibold`}>取消</button><button onClick={handleSaveSearch} disabled={!newSearchName.trim()} className="h-8 px-4 rounded bg-blue-600 hover:bg-blue-500 text-white font-semibold disabled:opacity-50 disabled:cursor-not-allowed">确定保存</button></div>
      </div>
    </div>
  );
}
