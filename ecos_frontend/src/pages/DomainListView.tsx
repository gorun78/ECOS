/**
 * DomainListView — Step 2 域列表页
 *
 * 对应设计文档第7.2章 Step 2 布局：
 * - 左侧: 域列表（搜索 + 筛选）
 * - 中间: 选中域的详情卡片（实体列表预览 + 统计）
 * - 右侧: 快速操作（进入设计器 / 导出 Schema）
 *
 * 数据来源: useWorkbenchStore.domains, useWorkbenchStore.fetchKGAndDomains()
 * 点击域 → 导航到 /ontology_workbench/domains/:code
 *
 * @license Apache-2.0
 */

import React, { useEffect, useState, useMemo, useCallback } from 'react';
import {
  Building2, Search, Box, GitBranch, ChevronRight,
  Loader2, AlertCircle, Layers, ExternalLink,
  Database, Filter, RefreshCw, ArrowUpDown,
  Eye, Download, Tag,
} from 'lucide-react';
import { useWorkbenchStore } from '../stores/useWorkbenchStore';
import type { Domain } from '../types/workbench';

// ── 域卡片颜色 & 图标映射 ────────────────────────────────────

const DOMAIN_ICONS: Record<string, string> = {
  '销售域': '📦', '供应链': '🏭', '财务域': '💰',
  'HR域': '👥', '人力资源': '👥', '采购域': '📋',
  '项目域': '📐', '资产域': '🏢', '工程域': '🔧',
  '分析域': '📊', '研发域': '⚙️', '默认域': '📁',
};

const DOMAIN_COLORS: Record<string, string> = {
  '销售域': 'border-indigo-400/30 bg-indigo-500/10',
  '供应链': 'border-emerald-400/30 bg-emerald-500/10',
  '财务域': 'border-amber-400/30 bg-amber-500/10',
  'HR域': 'border-purple-400/30 bg-purple-500/10',
  '人力资源': 'border-purple-400/30 bg-purple-500/10',
  '采购域': 'border-cyan-400/30 bg-cyan-500/10',
  '项目域': 'border-blue-400/30 bg-blue-500/10',
  '资产域': 'border-pink-400/30 bg-pink-500/10',
  '工程域': 'border-orange-400/30 bg-orange-500/10',
  '分析域': 'border-teal-400/30 bg-teal-500/10',
  '研发域': 'border-red-400/30 bg-red-500/10',
  '默认域': 'border-slate-500/30 bg-slate-500/10',
};

type SortKey = 'name' | 'entityCount' | 'relationshipCount';
type SortDir = 'asc' | 'desc';

// ── 主组件 ──────────────────────────────────────────────────

export default function DomainListView() {
  const store = useWorkbenchStore();
  const { domains, kgLoading, error } = store;

  // 本地 UI 状态
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedDomainCode, setSelectedDomainCode] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [sortKey, setSortKey] = useState<SortKey>('entityCount');
  const [sortDir, setSortDir] = useState<SortDir>('desc');

  // 初始加载
  useEffect(() => {
    if (domains.length === 0 && !kgLoading) {
      store.fetchKGAndDomains();
    }
  }, []);

  // 自动选中第一个域
  useEffect(() => {
    if (!selectedDomainCode && domains.length > 0) {
      setSelectedDomainCode(domains[0].code);
    }
  }, [domains, selectedDomainCode]);

  // ── 过滤 + 排序 ──
  const filteredDomains = useMemo(() => {
    let result = [...domains];

    // 搜索
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      result = result.filter(
        (d) =>
          d.name.toLowerCase().includes(q) ||
          d.code.toLowerCase().includes(q) ||
          (d.description && d.description.toLowerCase().includes(q))
      );
    }

    // 状态筛选
    if (statusFilter !== 'all') {
      result = result.filter((d) => d.status === statusFilter);
    }

    // 排序
    result.sort((a, b) => {
      let cmp = 0;
      switch (sortKey) {
        case 'name':
          cmp = a.name.localeCompare(b.name, 'zh');
          break;
        case 'entityCount':
          cmp = a.entityCount - b.entityCount;
          break;
        case 'relationshipCount':
          cmp = a.relationshipCount - b.relationshipCount;
          break;
      }
      return sortDir === 'asc' ? cmp : -cmp;
    });

    return result;
  }, [domains, searchQuery, statusFilter, sortKey, sortDir]);

  // 当前选中的域
  const selectedDomain = useMemo(
    () => domains.find((d) => d.code === selectedDomainCode),
    [domains, selectedDomainCode]
  );

  // ── 操作 ──
  const handleEnterDesign = useCallback((domainCode: string) => {
    window.location.hash = `#/ontology_workbench/domains/${encodeURIComponent(domainCode)}`;
  }, []);

  const handleToggleSort = useCallback(
    (key: SortKey) => {
      if (sortKey === key) {
        setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
      } else {
        setSortKey(key);
        setSortDir('desc');
      }
    },
    [sortKey]
  );

  // 获取域图标
  const getDomainIcon = (name: string) => DOMAIN_ICONS[name] || '📁';

  const getDomainColor = (name: string) =>
    DOMAIN_COLORS[name] || 'border-slate-500/30 bg-slate-500/10';

  // ── 加载态 ──
  if (kgLoading && domains.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center bg-[#0f1117]">
        <div className="text-center">
          <Loader2 size={32} className="animate-spin text-indigo-400 mx-auto mb-3" />
          <p className="text-sm text-slate-400">正在加载域数据...</p>
        </div>
      </div>
    );
  }

  // ── 错误态 ──
  if (error && domains.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center bg-[#0f1117]">
        <div className="text-center p-8">
          <AlertCircle size={40} className="text-red-400 mx-auto mb-3" />
          <p className="text-sm text-red-400 mb-2">加载失败</p>
          <p className="text-xs text-slate-500 mb-4">{error}</p>
          <button
            onClick={() => store.fetchKGAndDomains()}
            className="px-4 py-1.5 rounded-lg text-xs bg-slate-700 text-slate-300 hover:bg-slate-600 transition"
          >
            重新加载
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 flex h-full overflow-hidden bg-[#0f1117] font-sans">
      {/* ═══════ 左侧：域列表 (280px) ═══════ */}
      <div className="w-[280px] min-w-[240px] border-r border-[#1E293B] bg-[#141924] flex flex-col shrink-0">
        {/* 标题 */}
        <div className="px-4 py-3.5 border-b border-[#1E293B]">
          <div className="flex items-center gap-2 mb-3">
            <Building2 size={16} className="text-indigo-400" />
            <h2 className="text-sm font-semibold text-white">业务域</h2>
            <span className="text-[10px] text-slate-500 ml-auto">
              {filteredDomains.length}/{domains.length}
            </span>
          </div>

          {/* 搜索 */}
          <div className="relative">
            <Search size={12} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-500" />
            <input
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="搜索域..."
              className="w-full bg-[#0b0e14] border border-[#1E293B] rounded-lg pl-7 pr-3 py-1.5
                text-xs text-white placeholder:text-slate-600
                focus:outline-none focus:border-indigo-500/40 transition"
            />
          </div>

          {/* 状态筛选 */}
          <div className="flex gap-1.5 mt-2">
            {[
              { key: 'all', label: '全部' },
              { key: 'active', label: '活跃' },
              { key: 'draft', label: '草稿' },
              { key: 'inactive', label: '停用' },
            ].map((f) => (
              <button
                key={f.key}
                onClick={() => setStatusFilter(f.key)}
                className={`flex-1 py-1 rounded text-[10px] font-medium transition ${
                  statusFilter === f.key
                    ? 'bg-indigo-600/20 text-indigo-300 border border-indigo-500/30'
                    : 'text-slate-500 hover:text-slate-400 border border-transparent'
                }`}
              >
                {f.label}
              </button>
            ))}
          </div>
        </div>

        {/* 域列表 */}
        <div className="flex-1 overflow-y-auto">
          {filteredDomains.length === 0 ? (
            <div className="flex items-center justify-center h-full text-slate-500">
              <div className="text-center p-4">
                <Box size={28} className="mx-auto mb-2 opacity-30" />
                <p className="text-xs">暂无匹配的域</p>
              </div>
            </div>
          ) : (
            filteredDomains.map((domain) => {
              const isSelected = domain.code === selectedDomainCode;
              const colorClass = getDomainColor(domain.name);
              const icon = getDomainIcon(domain.name);

              return (
                <button
                  key={domain.code}
                  onClick={() => setSelectedDomainCode(domain.code)}
                  className={`w-full text-left px-4 py-3 border-b border-[#1E293B]/50 transition
                    flex items-start gap-3 ${
                      isSelected
                        ? 'bg-indigo-500/10 border-l-2 border-l-indigo-500'
                        : 'hover:bg-white/[0.03] border-l-2 border-l-transparent'
                    }`}
                >
                  <span className="text-lg mt-0.5 shrink-0">{icon}</span>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-1.5">
                      <span className="text-xs font-semibold text-white truncate">
                        {domain.name}
                      </span>
                      {domain.status === 'draft' && (
                        <span className="text-[9px] px-1 py-0.5 rounded bg-amber-500/10 text-amber-400 shrink-0">
                          草稿
                        </span>
                      )}
                    </div>
                    <p className="text-[10px] text-slate-500 font-mono truncate">
                      {domain.code}
                    </p>
                    <div className="flex items-center gap-3 mt-1.5 text-[10px] text-slate-500">
                      <span className="flex items-center gap-1">
                        <Box size={10} /> {domain.entityCount}
                      </span>
                      <span className="flex items-center gap-1">
                        <GitBranch size={10} /> {domain.relationshipCount}
                      </span>
                    </div>
                  </div>
                  {isSelected && (
                    <ChevronRight size={14} className="text-indigo-400 mt-1.5 shrink-0" />
                  )}
                </button>
              );
            })
          )}
        </div>

        {/* 底部操作 */}
        <div className="px-4 py-2 border-t border-[#1E293B]">
          <button
            onClick={() => store.fetchKGAndDomains()}
            className="w-full flex items-center justify-center gap-1.5 py-1.5 rounded-lg
              text-[10px] text-slate-500 hover:text-slate-400 hover:bg-white/[0.03] transition"
          >
            <RefreshCw size={10} className={kgLoading ? 'animate-spin' : ''} />
            刷新数据
          </button>
        </div>
      </div>

      {/* ═══════ 中间：域详情卡片 ═══════ */}
      <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
        {/* 页面标题 */}
        <div className="flex items-center justify-between px-5 py-3.5 border-b border-[#1E293B] bg-[#141924] shrink-0">
          <div className="flex items-center gap-2.5">
            <Layers size={18} className="text-indigo-400" />
            <div>
              <h1 className="text-sm font-bold text-white">域管理</h1>
              <p className="text-[10px] text-slate-500">选择业务域查看详情或进入设计器</p>
            </div>
          </div>

          {/* 排序 */}
          <div className="flex items-center gap-1 text-[10px]">
            <span className="text-slate-500 mr-1">排序:</span>
            {([
              { key: 'name' as SortKey, label: '名称' },
              { key: 'entityCount' as SortKey, label: '实体数' },
              { key: 'relationshipCount' as SortKey, label: '关系数' },
            ]).map((s) => (
              <button
                key={s.key}
                onClick={() => handleToggleSort(s.key)}
                className={`px-2 py-1 rounded transition flex items-center gap-0.5 ${
                  sortKey === s.key
                    ? 'text-indigo-300 bg-indigo-500/10'
                    : 'text-slate-500 hover:text-slate-400'
                }`}
              >
                {s.label}
                {sortKey === s.key && (
                  <ArrowUpDown size={9} className={sortDir === 'asc' ? 'rotate-180' : ''} />
                )}
              </button>
            ))}
          </div>
        </div>

        {/* 域详情 / 空状态 */}
        {!selectedDomain ? (
          <div className="flex-1 flex items-center justify-center text-slate-500">
            <div className="text-center">
              <Database size={40} className="mx-auto mb-3 opacity-20" />
              <p className="text-sm">选择左侧业务域查看详情</p>
              <p className="text-[10px] mt-1 opacity-60">
                {domains.length === 0 ? '暂无域数据' : `共 ${domains.length} 个域`}
              </p>
            </div>
          </div>
        ) : (
          <div className="p-5 space-y-5">
            {/* 域概览卡片 */}
            <div className={`rounded-xl border p-5 ${getDomainColor(selectedDomain.name)}`}>
              <div className="flex items-start justify-between mb-4">
                <div className="flex items-center gap-3">
                  <span className="text-2xl">{getDomainIcon(selectedDomain.name)}</span>
                  <div>
                    <h2 className="text-lg font-bold text-white">{selectedDomain.name}</h2>
                    <p className="text-xs font-mono text-slate-400">{selectedDomain.code}</p>
                  </div>
                </div>
                {/* 状态标签 */}
                <span
                  className={`text-[10px] px-2.5 py-1 rounded-full font-medium ${
                    selectedDomain.status === 'active'
                      ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                      : selectedDomain.status === 'draft'
                      ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20'
                      : 'bg-slate-500/10 text-slate-400 border border-slate-500/20'
                  }`}
                >
                  {selectedDomain.status === 'active' ? '活跃' : selectedDomain.status === 'draft' ? '草稿' : '停用'}
                </span>
              </div>

              {/* 统计数字 */}
              <div className="grid grid-cols-3 gap-3 mb-4">
                <div className="bg-[#0b0e14]/50 rounded-lg p-3 text-center border border-white/5">
                  <Box size={14} className="text-indigo-400 mx-auto mb-1" />
                  <div className="text-lg font-bold text-white">{selectedDomain.entityCount}</div>
                  <div className="text-[10px] text-slate-500">实体</div>
                </div>
                <div className="bg-[#0b0e14]/50 rounded-lg p-3 text-center border border-white/5">
                  <GitBranch size={14} className="text-emerald-400 mx-auto mb-1" />
                  <div className="text-lg font-bold text-white">{selectedDomain.relationshipCount}</div>
                  <div className="text-[10px] text-slate-500">关系</div>
                </div>
                <div className="bg-[#0b0e14]/50 rounded-lg p-3 text-center border border-white/5">
                  <Tag size={14} className="text-amber-400 mx-auto mb-1" />
                  <div className="text-lg font-bold text-white">
                    {(selectedDomain.entities || []).length}
                  </div>
                  <div className="text-[10px] text-slate-500">实体引用</div>
                </div>
              </div>

              {/* 描述 */}
              {selectedDomain.description && (
                <p className="text-xs text-slate-400 mb-4 leading-relaxed">
                  {selectedDomain.description}
                </p>
              )}

              {/* 实体列表预览 */}
              {selectedDomain.entities && selectedDomain.entities.length > 0 && (
                <div className="mb-4">
                  <h4 className="text-xs font-semibold text-slate-300 mb-2 flex items-center gap-1.5">
                    <Database size={11} className="text-slate-500" />
                    实体列表
                  </h4>
                  <div className="flex flex-wrap gap-1.5">
                    {selectedDomain.entities.slice(0, 15).map((entityRef) => (
                      <span
                        key={entityRef}
                        className="text-[10px] px-2 py-1 rounded-md bg-[#0b0e14] border border-[#2a3040]
                          text-slate-400 font-mono"
                      >
                        {entityRef}
                      </span>
                    ))}
                    {selectedDomain.entities.length > 15 && (
                      <span className="text-[10px] px-2 py-1 rounded-md bg-[#0b0e14] border border-[#2a3040] text-slate-500">
                        +{selectedDomain.entities.length - 15} 更多
                      </span>
                    )}
                  </div>
                </div>
              )}
            </div>

            {/* 域列表网格（快速切换） */}
            {domains.length > 1 && (
              <div>
                <h4 className="text-xs font-semibold text-slate-300 mb-3 flex items-center gap-1.5">
                  <Building2 size={11} className="text-slate-500" />
                  其他业务域
                </h4>
                <div className="grid grid-cols-2 md:grid-cols-3 gap-2.5">
                  {domains
                    .filter((d) => d.code !== selectedDomainCode)
                    .sort((a, b) => b.entityCount - a.entityCount)
                    .map((domain) => (
                      <button
                        key={domain.code}
                        onClick={() => setSelectedDomainCode(domain.code)}
                        className={`text-left rounded-lg border p-3 transition hover:border-opacity-60 ${
                          getDomainColor(domain.name)
                        }`}
                      >
                        <div className="flex items-center gap-2 mb-1">
                          <span className="text-sm">{getDomainIcon(domain.name)}</span>
                          <span className="text-xs font-semibold text-white truncate">
                            {domain.name}
                          </span>
                        </div>
                        <div className="flex items-center gap-3 text-[10px] text-slate-500">
                          <span>{domain.entityCount} 实体</span>
                          <span>{domain.relationshipCount} 关系</span>
                        </div>
                      </button>
                    ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* ═══════ 右侧：快速操作面板 (280px) ═══════ */}
      <div className="w-[280px] min-w-[220px] border-l border-[#1E293B] bg-[#141924] flex flex-col shrink-0 overflow-y-auto">
        <div className="px-4 py-3.5 border-b border-[#1E293B]">
          <h3 className="text-xs font-semibold text-slate-300 flex items-center gap-1.5">
            <Eye size={12} className="text-indigo-400" />
            快速操作
          </h3>
        </div>

        <div className="flex-1 p-4 space-y-3">
          {/* 进入设计器 */}
          <button
            disabled={!selectedDomain}
            onClick={() => selectedDomain && handleEnterDesign(selectedDomain.code)}
            className="w-full flex items-center gap-3 px-4 py-3 rounded-xl
              bg-indigo-600/20 border border-indigo-500/30
              hover:bg-indigo-600/30 hover:border-indigo-500/50
              disabled:opacity-30 disabled:cursor-not-allowed
              transition group"
          >
            <div className="p-2 rounded-lg bg-indigo-500/20 shrink-0">
              <ExternalLink size={16} className="text-indigo-400" />
            </div>
            <div className="text-left">
              <div className="text-xs font-semibold text-indigo-300">进入设计器</div>
              <div className="text-[10px] text-indigo-400/60">
                可视化本体设计 · 实体关系建模
              </div>
            </div>
            <ChevronRight size={14} className="text-indigo-400 ml-auto shrink-0 group-hover:translate-x-0.5 transition-transform" />
          </button>

          {/* 导出 Schema */}
          <button
            disabled={!selectedDomain}
            className="w-full flex items-center gap-3 px-4 py-3 rounded-xl
              bg-slate-500/10 border border-slate-500/20
              hover:bg-slate-500/20 hover:border-slate-500/30
              disabled:opacity-30 disabled:cursor-not-allowed
              transition group"
          >
            <div className="p-2 rounded-lg bg-slate-500/10 shrink-0">
              <Download size={16} className="text-slate-400" />
            </div>
            <div className="text-left">
              <div className="text-xs font-semibold text-slate-300">导出 Schema</div>
              <div className="text-[10px] text-slate-500">JSON / YAML 格式</div>
            </div>
          </button>

          {/* 域统计 */}
          {selectedDomain && (
            <div className="mt-4 pt-4 border-t border-[#1E293B]">
              <h4 className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider mb-3">
                域统计
              </h4>
              <div className="space-y-2">
                <div className="flex items-center justify-between text-[11px]">
                  <span className="text-slate-400">实体数</span>
                  <span className="text-white font-mono">{selectedDomain.entityCount}</span>
                </div>
                <div className="flex items-center justify-between text-[11px]">
                  <span className="text-slate-400">关系数</span>
                  <span className="text-white font-mono">{selectedDomain.relationshipCount}</span>
                </div>
                <div className="flex items-center justify-between text-[11px]">
                  <span className="text-slate-400">状态</span>
                  <span
                    className={`font-medium ${
                      selectedDomain.status === 'active'
                        ? 'text-emerald-400'
                        : selectedDomain.status === 'draft'
                        ? 'text-amber-400'
                        : 'text-slate-500'
                    }`}
                  >
                    {selectedDomain.status === 'active' ? '活跃' : selectedDomain.status === 'draft' ? '草稿' : '停用'}
                  </span>
                </div>
                <div className="flex items-center justify-between text-[11px]">
                  <span className="text-slate-400">编码</span>
                  <span className="text-slate-500 font-mono text-[10px]">{selectedDomain.code}</span>
                </div>
              </div>
            </div>
          )}

          {/* 帮助提示 */}
          <div className="mt-auto pt-4 border-t border-[#1E293B]">
            <div className="px-3 py-2.5 rounded-lg bg-[#0b0e14] border border-[#1E293B]">
              <p className="text-[10px] text-slate-500 leading-relaxed">
                选择一个业务域进入本体设计器，进行实体建模、关系定义、属性编辑等操作。
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
