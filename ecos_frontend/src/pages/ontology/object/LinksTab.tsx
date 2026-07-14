/**
 * LinksTab — 关联链接 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { ChevronRight, GitMerge } from 'lucide-react';
import type { LinksTabProps } from './types';

export default function LinksTab({
  objectType,
  relatedLinks,
  onNavigateToLink,
}: LinksTabProps) {
  return (
    <div className="space-y-4">
      <div className="text-xs text-slate-500">
        在此查看与 {objectType.displayName} 相关联的所有多维链接关系模式。
      </div>
      {relatedLinks.length === 0 ? (
        <div className="text-center py-8 border border-dashed border-gray-200 rounded-lg text-slate-400 text-xs">
          暂无任何链接关系定义关联到此对象。
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-4">
          {relatedLinks.map(link => {
            const isSource = link.sourceObjectType === objectType.id;
            return (
              <div
                key={link.id}
                onClick={() => onNavigateToLink(link.id)}
                className="p-4 border border-slate-200 rounded-xl hover:border-blue-400 hover:shadow-xs transition-all cursor-pointer bg-white group flex items-start justify-between"
              >
                <div className="space-y-2">
                  <div className="flex items-center gap-2">
                    <span className="p-1 rounded-md bg-slate-100 text-slate-600 group-hover:bg-blue-50 group-hover:text-blue-600 transition-colors">
                      <GitMerge size={14} />
                    </span>
                    <div className="font-semibold text-xs text-slate-800 group-hover:text-blue-600">
                      {link.displayName}
                    </div>
                    <span className="text-[10px] font-mono text-slate-400 bg-slate-50 px-1 rounded">
                      {link.cardinality}
                    </span>
                  </div>
                  <p className="text-[10px] text-slate-500">{link.description}</p>
                  <div className="flex items-center gap-1.5 text-[10px] text-slate-600 pt-1">
                    <span className={isSource ? 'font-semibold text-blue-600' : ''}>
                      {isSource ? '源' : '源(' + link.sourceObjectType + ')'}
                    </span>
                    <span>→</span>
                    <span className={!isSource ? 'font-semibold text-blue-600' : ''}>
                      {!isSource ? '目标' : '目标(' + link.targetObjectType + ')'}
                    </span>
                  </div>
                </div>
                <div className="flex items-center gap-1 text-[10px] text-blue-500 group-hover:translate-x-0.5 transition-transform">
                  <span>跳转配置</span>
                  <ChevronRight size={12} />
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
