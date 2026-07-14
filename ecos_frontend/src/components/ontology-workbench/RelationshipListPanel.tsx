/**
 * RelationshipListPanel — 关联关系列表面板
 *
 * 用于 WorkbenchRightPanel 关联关系标签页。
 * 展示当前选中实体的关系列表:
 *   - 列表显示: 源实体 → 目标实体 | 关系类型 | [删除]
 *   - 数据来源: store.relationships，按当前 entityId 过滤
 *   - 空状态: "暂无关系"
 *
 * @license Apache-2.0
 */

import React, { useState, useCallback } from 'react';
import {
  GitBranch,
  Trash2,
  Loader2,
  ArrowRight,
  ArrowLeft,
} from 'lucide-react';
import { useWorkbenchStore } from '../../stores/useWorkbenchStore';
import type { Relationship } from '../../types/workbench';

// ── 关系类型颜色映射 ──────────────────────────────────────────

const RELATIONSHIP_TYPE_COLORS: Record<string, string> = {
  ONE_TO_ONE:   'bg-blue-500/15 text-blue-400 border-blue-500/20',
  ONE_TO_MANY:  'bg-amber-500/15 text-amber-400 border-amber-500/20',
  MANY_TO_ONE:  'bg-emerald-500/15 text-emerald-400 border-emerald-500/20',
  MANY_TO_MANY: 'bg-purple-500/15 text-purple-400 border-purple-500/20',
};

function getRelationshipTypeLabel(rt: string): string {
  const map: Record<string, string> = {
    ONE_TO_ONE:   '1:1',
    ONE_TO_MANY:  '1:N',
    MANY_TO_ONE:  'N:1',
    MANY_TO_MANY: 'N:N',
  };
  return map[rt] || rt;
}

// ════════════════════════════════════════════════════════════════
// 删除确认弹窗（内联）
// ════════════════════════════════════════════════════════════════

interface ConfirmDeleteProps {
  rel: Relationship;
  sourceName: string;
  targetName: string;
  onConfirm: () => void;
  onCancel: () => void;
  loading: boolean;
}

function ConfirmDelete({ rel, sourceName, targetName, onConfirm, onCancel, loading }: ConfirmDeleteProps) {
  return (
    <div className="px-3 py-2.5 rounded-lg bg-red-500/10 border border-red-500/20 mx-2 my-1">
      <p className="text-[10px] text-red-400 mb-2">
        确定要删除关系「{rel.name || rel.code}」吗？
      </p>
      <p className="text-[9px] text-slate-500 mb-2 font-mono">
        {sourceName} → {targetName}
      </p>
      <div className="flex items-center gap-2">
        <button
          onClick={onConfirm}
          disabled={loading}
          className="flex items-center gap-1 px-2.5 py-1 rounded text-[10px] font-medium
            bg-red-600/20 text-red-400 border border-red-500/30
            hover:bg-red-600/30 disabled:opacity-40 transition"
        >
          {loading && <Loader2 size={10} className="animate-spin" />}
          确认删除
        </button>
        <button
          onClick={onCancel}
          disabled={loading}
          className="px-2.5 py-1 rounded text-[10px] text-slate-400
            hover:text-slate-300 hover:bg-white/5 transition
            disabled:opacity-40"
        >
          取消
        </button>
      </div>
    </div>
  );
}

// ════════════════════════════════════════════════════════════════
// 主组件
// ════════════════════════════════════════════════════════════════

interface RelationshipListPanelProps {
  entityId: string;
}

export default function RelationshipListPanel({ entityId }: RelationshipListPanelProps) {
  const store = useWorkbenchStore();
  const { relationships, entities } = store;

  // 过滤当前实体的关系
  const entityRelationships: Relationship[] = relationships.filter(
    (r) => r.sourceEntityId === entityId || r.targetEntityId === entityId
  );

  const [confirmingDeleteId, setConfirmingDeleteId] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);

  // ── 获取对方实体名称 ─────────────────────────────────────────
  const getOtherEntityName = useCallback((rel: Relationship): string => {
    const otherId = rel.sourceEntityId === entityId ? rel.targetEntityId : rel.sourceEntityId;
    const otherEntity = entities.find((e) => e.id === otherId);
    return otherEntity?.name || otherEntity?.code || otherId?.slice(0, 12) || '未知';
  }, [entityId, entities]);

  // ── 获取方向说明 ────────────────────────────────────────────
  const getDirection = useCallback((rel: Relationship): { isSource: boolean; sourceName: string; targetName: string } => {
    const isSource = rel.sourceEntityId === entityId;
    const srcEntity = entities.find((e) => e.id === rel.sourceEntityId);
    const tgtEntity = entities.find((e) => e.id === rel.targetEntityId);
    return {
      isSource,
      sourceName: srcEntity?.name || srcEntity?.code || rel.sourceEntityId?.slice(0, 12) || '?',
      targetName: tgtEntity?.name || tgtEntity?.code || rel.targetEntityId?.slice(0, 12) || '?',
    };
  }, [entityId, entities]);

  // ── 删除关系 ─────────────────────────────────────────────────
  const handleDelete = useCallback(async (relId: string) => {
    setDeleting(true);
    try {
      await store.deleteRelationship(relId);
      setConfirmingDeleteId(null);
    } catch {
      // error handled by store
    } finally {
      setDeleting(false);
    }
  }, [store]);

  return (
    <div>
      {/* 标题 */}
      <h4 className="text-[11px] font-semibold text-slate-300 mb-3 flex items-center gap-1.5">
        <GitBranch size={11} className="text-slate-500" />
        关联关系
        <span className="text-[10px] font-normal text-slate-500 ml-1">
          ({entityRelationships.length})
        </span>
      </h4>

      {/* 空状态 */}
      {entityRelationships.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-10 text-slate-500">
          <GitBranch size={24} className="mb-2 opacity-20" />
          <p className="text-[11px]">暂无关系</p>
          <p className="text-[9px] mt-0.5 opacity-50">
            在画布中拖拽连线创建实体间关系
          </p>
        </div>
      ) : (
        <div className="space-y-1">
          {entityRelationships.map((rel) => {
            const { isSource, sourceName, targetName } = getDirection(rel);
            const isConfirming = confirmingDeleteId === rel.id;
            const typeColor = RELATIONSHIP_TYPE_COLORS[rel.relationshipType] || 'bg-slate-500/15 text-slate-400 border-slate-500/20';

            return (
              <React.Fragment key={rel.id}>
                <div
                  className={`group flex items-center gap-2 px-3 py-2 rounded-lg transition ${
                    isConfirming
                      ? 'bg-red-500/5 border border-red-500/20'
                      : 'hover:bg-white/[0.03] border border-transparent'
                  }`}
                >
                  {/* 方向指示 */}
                  <div className="flex items-center text-[10px] shrink-0">
                    <span className={`font-mono ${isSource ? 'text-emerald-400' : 'text-blue-400'}`}>
                      {isSource ? sourceName : targetName}
                    </span>
                    <span className="mx-1 text-slate-600">
                      {isSource ? <ArrowRight size={10} /> : <ArrowLeft size={10} />}
                    </span>
                    <span className={`font-mono ${isSource ? 'text-blue-400' : 'text-emerald-400'}`}>
                      {isSource ? targetName : sourceName}
                    </span>
                  </div>

                  {/* 关系类型标签 */}
                  {rel.relationshipType && (
                    <span className={`text-[9px] px-1.5 py-0.5 rounded border ${typeColor} shrink-0`}>
                      {getRelationshipTypeLabel(rel.relationshipType)}
                    </span>
                  )}

                  {/* 弹性占位 */}
                  <div className="flex-1" />

                  {/* 删除按钮 */}
                  <button
                    onClick={() => setConfirmingDeleteId(isConfirming ? null : rel.id)}
                    className="p-1 rounded opacity-0 group-hover:opacity-100
                      hover:bg-red-500/10 text-slate-600 hover:text-red-400
                      transition-opacity shrink-0"
                    title="删除关系"
                  >
                    <Trash2 size={11} />
                  </button>
                </div>

                {/* 确认删除面板 */}
                {isConfirming && (
                  <ConfirmDelete
                    rel={rel}
                    sourceName={sourceName}
                    targetName={targetName}
                    onConfirm={() => handleDelete(rel.id)}
                    onCancel={() => setConfirmingDeleteId(null)}
                    loading={deleting}
                  />
                )}
              </React.Fragment>
            );
          })}
        </div>
      )}
    </div>
  );
}
