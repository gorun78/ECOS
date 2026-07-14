/* Extracted from DataWorkbenchLayout.tsx */
import React from 'react';
import CodeWorkbooksPrototype from '../CodeWorkbooksPrototype';
import { useTheme } from "../../../components/ThemeContext";


const CodeWorkbooksTab: React.FC = () => {
  const { styles } = useTheme();
  return (
<div className={`flex-1 flex flex-col min-h-0 ${styles.appBg} overflow-hidden`}>
  <div className="bg-slate-950 text-white px-5 py-3 border-b border-slate-800 flex justify-between items-center shrink-0 select-none">
    <div className="flex items-center gap-2">
      <span className="h-2 w-2 rounded-full bg-violet-400 animate-pulse" />
      <span className="text-xs font-black tracking-wide font-sans">
        ECOS 核心开发工具 · Code Workbooks (混编交互式数据探索沙箱)
      </span>
    </div>
    <div className={`${styles.cardBg}/10 text-white text-[9px] font-bold px-2 py-0.5 rounded border border-white/10 font-mono`}>
      PROTOTYPE WORKSTATION ACTIVE
    </div>
  </div>
  <div className={`flex-1 overflow-hidden min-h-0 ${styles.appBg}`}>
    <CodeWorkbooksPrototype />
  </div>
</div>
  );
};

export default CodeWorkbooksTab;
