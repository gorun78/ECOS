import { useTheme } from '../../components/ThemeContext';
import { Layers, Plus, Search, ShieldAlert } from 'lucide-react';
import type { GuardrailPolicy } from './types';
import { POLICY_TYPE_META } from './constants';
import { Toggle, Spinner, Badge } from './UiPrimitives';

export default function PolicyListPanel({
  policies, totalCount, loading, search, onSearchChange, selectedId, onSelect, onCreate, onToggle,
}: {
  policies: GuardrailPolicy[];
  totalCount: number;
  loading: boolean;
  search: string;
  onSearchChange: (v: string) => void;
  selectedId: string | null;
  onSelect: (id: string) => void;
  onCreate: () => void;
  onToggle: (p: GuardrailPolicy) => void;
}) {
  const { styles } = useTheme();

  return (
    <div className={`lg:w-80 shrink-0 ${styles.inputBg} border ${styles.cardBorder} rounded-xl shadow-sm flex flex-col overflow-hidden`}>
      <div className={`p-3 border-b ${styles.cardBorder} ${styles.appBg} space-y-2.5`}>
        <div className="flex items-center justify-between">
          <span className={`font-extrabold ${styles.cardText} flex items-center gap-1.5`}>
            <Layers size={13} className={styles.cardTextMuted} />
            <span>护栏策略 ({totalCount})</span>
          </span>
          <button
            onClick={onCreate}
            className="px-2.5 py-1 bg-rose-600 hover:bg-rose-700 text-white font-bold rounded-md text-[10px] flex items-center gap-1 cursor-pointer transition-colors"
          >
            <Plus size={11} /> 新建
          </button>
        </div>
        <div className="relative">
          <Search size={11} className={`absolute left-2 top-1/2 -translate-y-1/2 ${styles.cardTextMuted}`} />
          <input
            value={search}
            onChange={e => onSearchChange(e.target.value)}
            placeholder="搜索策略..."
            className={`w-full pl-6 pr-2 py-1.5 rounded-md text-[11px] ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} focus:outline-none focus:border-blue-500`}
          />
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-2 space-y-1.5">
        {loading ? (
          <div className={`h-full flex items-center justify-center ${styles.cardTextMuted} gap-2`}>
            <Spinner className={`w-4 h-4 ${styles.cardTextMuted}`} />
            <span className="font-bold">加载中...</span>
          </div>
        ) : policies.length === 0 ? (
          <div className={`h-full flex flex-col items-center justify-center p-6 text-center ${styles.cardTextMuted} space-y-2`}>
            <ShieldAlert size={24} className={styles.cardTextMuted} />
            <p className="font-bold">暂无护栏策略</p>
            <p className="text-[10px]">点击「新建」创建第一条安全护栏策略。</p>
          </div>
        ) : (
          policies.map(p => {
            const meta = POLICY_TYPE_META[p.type] ?? POLICY_TYPE_META.custom;
            const Icon = meta.icon;
            const isSelected = p.id === selectedId;
            return (
              <div
                key={p.id}
                onClick={() => onSelect(p.id)}
                className={`p-2.5 rounded-lg border cursor-pointer transition-all ${isSelected ? 'border-blue-600 bg-blue-50/40 shadow-sm' : `${styles.cardBorder} ${styles.cardBg} ${styles.sidebarHoverBg}`}`}>
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-1.5 min-w-0">
                    <span className={`p-1 rounded ${p.isEnabled ? 'bg-blue-50 ' + meta.color : `${styles.appBg} ${styles.cardTextMuted}`}`}>
                      <Icon size={13} />
                    </span>
                    <span className={`font-bold ${styles.cardText} text-[11px] truncate`}>{p.name}</span>
                  </div>
                  <Toggle on={p.isEnabled} onClick={() => onToggle(p)} />
                </div>
                <div className="flex items-center gap-1.5 mt-1.5 flex-wrap">
                  <Badge tone="slate">{meta.label}</Badge>
                  <Badge tone={p.severity === 'block' ? 'rose' : 'amber'}>
                    {p.severity === 'block' ? '强制阻断' : '记录审计'}
                  </Badge>
                  <Badge tone={p.status === 'COMPILED' ? 'emerald' : 'amber'}>
                    {p.status === 'COMPILED' ? '已编译' : '草稿'}
                  </Badge>
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
