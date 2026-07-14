/**
 * ActionsTab — 应用操作 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { ChevronRight, Zap } from 'lucide-react';
import type { ActionsTabProps } from './types';

export default function ActionsTab({
  objectType,
  relatedActions,
  onNavigateToAction,
}: ActionsTabProps) {
  return (
    <div className="space-y-4">
      <div className="text-xs text-slate-500">
        在此查看能够被应用、修改或实例化 {objectType.displayName} 对象的有界业务操作。
      </div>
      {relatedActions.length === 0 ? (
        <div className="text-center py-8 border border-dashed border-gray-200 rounded-lg text-slate-400 text-xs">
          目前尚无操作类型注册针对此对象的操作修改。
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-4">
          {relatedActions.map(action => (
            <div
              key={action.id}
              onClick={() => onNavigateToAction(action.id)}
              className="p-4 border border-slate-200 rounded-xl hover:border-blue-400 hover:shadow-xs transition-all cursor-pointer bg-white group flex items-start justify-between"
            >
              <div className="space-y-2">
                <div className="flex items-center gap-2">
                  <span className="p-1.5 rounded-full bg-amber-50 text-amber-600 group-hover:bg-amber-100 transition-colors">
                    <Zap size={13} className="fill-amber-500" />
                  </span>
                  <div className="font-semibold text-xs text-slate-800 group-hover:text-blue-600">
                    {action.displayName}
                  </div>
                </div>
                <p className="text-[10px] text-slate-500">{action.description}</p>
                <div className="flex items-center gap-2 font-mono text-[9px] text-slate-400 bg-slate-50 p-1.5 rounded border border-slate-100">
                  <div>
                    <strong>参数:</strong> {action.parameters.length} | <strong>副作用:</strong> {action.rules.length} 条
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-1 text-[10px] text-blue-500 group-hover:translate-x-0.5 transition-transform">
                <span>跳转配置</span>
                <ChevronRight size={12} />
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
