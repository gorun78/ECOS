import React from 'react';
import { useTheme } from '../../components/ThemeContext';
import { Settings, Binary, Edit3, Trash2, CheckCircle2, X, Terminal } from 'lucide-react';
import type { GuardrailPolicy } from './types';
import { POLICY_TYPE_META } from './constants';
import { Badge } from './UiPrimitives';

// ─────────────────────────────────────────────────────────────
// Policy Detail (read-only inspector)
// ─────────────────────────────────────────────────────────────

export default function PolicyDetail({
  policy, onEdit, onDelete, onCompile,
}: {
  policy: GuardrailPolicy;
  onEdit: () => void;
  onDelete: () => void;
  onCompile: () => void;
}) {
  const meta = POLICY_TYPE_META[policy.type] ?? POLICY_TYPE_META.custom;
  const Icon = meta.icon;
  const { styles } = useTheme();

  const configRows: { label: string; value: React.ReactNode }[] = [];
  if (policy.table) configRows.push({ label: '目标表', value: <span className="font-mono">{policy.table}</span> });
  if (policy.column) configRows.push({ label: '目标列', value: <span className="font-mono">{policy.column}</span> });
  if (policy.maskType) configRows.push({ label: '脱敏策略', value: <Badge tone="blue">{policy.maskType}</Badge> });
  if (policy.condition) configRows.push({ label: 'SQL 谓词', value: <span className="font-mono text-amber-700">WHERE {policy.condition}</span> });

  return (
    <div className="flex-1 flex flex-col overflow-y-auto">
      <div className={`p-4 border-b ${styles.cardBorder} ${styles.appBg} flex items-center justify-between shrink-0`}>
        <span className={`font-extrabold ${styles.cardText} flex items-center gap-1.5`}>
          <Settings size={13} className={styles.cardTextMuted} />
          <span>策略详情</span>
        </span>
        <div className="flex gap-2">
          <button onClick={onCompile} className="px-3 py-1.5 bg-[var(--muted,#0F172A)] hover:opacity-80 text-white rounded-md text-[10px] font-bold flex items-center gap-1 cursor-pointer">
            <Binary size={11} /> 编译
          </button>
          <button onClick={onEdit} className={`px-3 py-1.5 border ${styles.cardBorder} ${styles.sidebarHoverBg} rounded-md text-[10px] font-bold ${styles.cardText} flex items-center gap-1 cursor-pointer`}>
            <Edit3 size={11} /> 编辑
          </button>
          <button onClick={onDelete} className={`px-3 py-1.5 border ${styles.dangerBorder} ${styles.dangerText} ${styles.dangerBg} rounded-md text-[10px] font-bold flex items-center gap-1 cursor-pointer`}>
            <Trash2 size={11} /> 删除
          </button>
        </div>
      </div>

      <div className="p-4 space-y-4 flex-1">
        {/* Title block */}
        <div className="flex items-start gap-3">
          <span className={`p-2.5 rounded-xl ${policy.isEnabled ? 'bg-blue-50 ' + meta.color : `${styles.appBg} ${styles.cardTextMuted}`}`}>
            <Icon size={20} />
          </span>
          <div className="space-y-1 flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <h3 className={`font-black ${styles.cardText} text-sm`}>{policy.name}</h3>
              <Badge tone="slate">{meta.label}</Badge>
              <Badge tone={policy.severity === 'block' ? 'rose' : 'amber'}>{policy.severity === 'block' ? '强制阻断' : '记录审计'}</Badge>
              <Badge tone={policy.status === 'COMPILED' ? 'emerald' : 'amber'}>{policy.status === 'COMPILED' ? '已编译' : '草稿'}</Badge>
            </div>
            <p className={`text-[11px] ${styles.cardTextMuted} leading-relaxed`}>{policy.description || '（暂无描述）'}</p>
          </div>
        </div>

        {/* Config grid */}
        {configRows.length > 0 && (
          <div className={`grid grid-cols-2 md:grid-cols-3 gap-3 ${styles.appBg} p-3 rounded-xl border ${styles.cardBorder}`}>
            {configRows.map((r, i) => (
              <div key={i}>
                <span className={`text-[9px] ${styles.cardTextMuted} font-bold uppercase tracking-wider block`}>{r.label}</span>
                <span className={`font-bold ${styles.cardText} text-[11px] block mt-0.5`}>{r.value}</span>
              </div>
            ))}
          </div>
        )}

        {/* Meta info */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <div className={`p-2.5 ${styles.inputBg} border ${styles.cardBorder} rounded-lg`}>
            <span className={`text-[9px] ${styles.cardTextMuted} font-bold uppercase block`}>策略 ID</span>
            <span className={`font-mono text-[10px] ${styles.cardText} font-bold break-all`}>{policy.id}</span>
          </div>
          <div className={`p-2.5 ${styles.inputBg} border ${styles.cardBorder} rounded-lg`}>
            <span className={`text-[9px] ${styles.cardTextMuted} font-bold uppercase block`}>启用状态</span>
            <span className={`font-bold text-[11px] flex items-center gap-1 ${policy.isEnabled ? 'text-emerald-600' : styles.cardTextMuted}`}>
              {policy.isEnabled ? <CheckCircle2 size={12} /> : <X size={12} />} {policy.isEnabled ? '已启用' : '已关闭'}
            </span>
          </div>
          <div className={`p-2.5 ${styles.inputBg} border ${styles.cardBorder} rounded-lg`}>
            <span className={`text-[9px] ${styles.cardTextMuted} font-bold uppercase block`}>编译时间</span>
            <span className={`font-mono text-[10px] ${styles.cardText} font-bold`}>{policy.compiledAt || '未编译'}</span>
          </div>
          <div className={`p-2.5 ${styles.inputBg} border ${styles.cardBorder} rounded-lg`}>
            <span className={`text-[9px] ${styles.cardTextMuted} font-bold uppercase block`}>更新时间</span>
            <span className={`font-mono text-[10px] ${styles.cardText} font-bold`}>{policy.updatedAt || policy.createdAt || '—'}</span>
          </div>
        </div>

        {/* Compile logs (if any) */}
        {policy.compileLogs && policy.compileLogs.length > 0 && (
          <div className="space-y-2">
            <h4 className={`font-extrabold ${styles.cardTextMuted} uppercase tracking-wider text-[10px] flex items-center gap-1`}>
              <Terminal size={11} /> 上次编译日志
            </h4>
            <div className="bg-[var(--muted,#020202)] rounded-lg p-3 max-h-40 overflow-y-auto space-y-1 text-[10px] font-mono">
              {policy.compileLogs.map((log, idx) => (
                <p key={idx} className={
                  log.includes('✅') ? 'text-emerald-400 font-bold' :
                  log.includes('⚠️') ? 'text-amber-400' :
                  log.includes('🚫') ? 'text-rose-400' :
                  'text-[var(--muted-foreground,#CBD5E1)]'
                }>{log}</p>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
