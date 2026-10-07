/**
 * Approval Node — UserCheck icon, approver/timeout
 * @license Apache-2.0
 */
import React, { memo } from 'react';
import { Handle, Position, type NodeProps, type Node } from '@xyflow/react';
import { UserCheck } from 'lucide-react';
import { useTheme } from '../../ThemeContext';
import { useLanguage } from '../../LanguageContext';
import type { LogicNodeData, LogicApprovalConfig } from '../../../types/aiworkbench';

function ApprovalNode({ data, selected }: NodeProps<Node<LogicNodeData>>) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const config = data.config as LogicApprovalConfig;
  const statusColor = {
    idle: 'bg-gray-400',
    running: 'bg-blue-500 animate-pulse',
    success: 'bg-emerald-500',
    error: 'bg-red-500',
  }[data.status];

  return (
    <div
      className={`group min-w-[200px] rounded-xl border-2 bg-white shadow-md transition-shadow ${
        selected ? 'border-blue-500 shadow-lg ring-2 ring-blue-200' : 'border-rose-200 hover:border-rose-300'
      }`}
    >
      <Handle type="target" position={Position.Top} className="!bg-rose-500 !w-3 !h-3" />
      <div className="flex items-center gap-2 px-3 py-2.5 border-b border-rose-100 bg-rose-50 rounded-t-[10px]">
        <span className="p-1 bg-rose-500 text-white rounded-md">
          <UserCheck size={14} />
        </span>
        <span className="text-xs font-bold text-rose-800 truncate flex-1">{data.label}</span>
        <span className={`w-2.5 h-2.5 rounded-full shrink-0 ${statusColor}`} />
      </div>
      <div className={`px-3 py-2 space-y-1 text-[10px] ${styles.cardTextMuted}`}>
        <div className="flex justify-between">
          <span className="font-semibold">{t('aiworkbench.logic.node.approver')}</span>
          <span className={`font-mono ${styles.cardText}`}>{config.approver}</span>
        </div>
        <div className="flex justify-between">
          <span className="font-semibold">{t('aiworkbench.logic.node.timeout')}</span>
          <span className="font-mono text-rose-600 font-bold">{config.timeout}s</span>
        </div>
        {data.duration != null && (
          <div className="flex justify-between">
            <span className="font-semibold">{t('aiworkbench.logic.node.duration')}</span>
            <span className={`font-mono ${styles.cardText}`}>{data.duration}ms</span>
          </div>
        )}
      </div>
      <Handle type="source" position={Position.Bottom} className="!bg-rose-500 !w-3 !h-3" />
    </div>
  );
}

export default memo(ApprovalNode);
