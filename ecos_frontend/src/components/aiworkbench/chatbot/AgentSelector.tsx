/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * AgentSelector — Enhanced Chatbot Selector (PMO-15 T7-4)
 * Supports search/filter, status indication, keyboard navigation
 */

import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { AIPAgent } from '../../../types/aiworkbench';
import * as Icons from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

interface AgentSelectorProps {
  agents: AIPAgent[];
  selectedAgentId: string;
  onSelectAgent: (id: string) => void;
  onStartCreate: () => void;
  styles: Record<string, string>;
}

export default function AgentSelector({
  agents,
  selectedAgentId,
  onSelectAgent,
  onStartCreate,
  styles,
}: AgentSelectorProps) {
  const { t } = useLanguage();
  const [searchQuery, setSearchQuery] = useState('');
  const [highlightedIndex, setHighlightedIndex] = useState(-1);
  const [showStatusFilter, setShowStatusFilter] = useState<'all' | 'active' | 'development'>('all');
  const searchRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

  // ── Filter agents based on search + status ─────────────────────
  const filteredAgents = useMemo(() => {
    return agents.filter(a => {
      const matchesSearch = !searchQuery ||
        a.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        a.role.toLowerCase().includes(searchQuery.toLowerCase()) ||
        a.modelId.toLowerCase().includes(searchQuery.toLowerCase());
      const matchesStatus = showStatusFilter === 'all' || a.status === showStatusFilter;
      return matchesSearch && matchesStatus;
    });
  }, [agents, searchQuery, showStatusFilter]);

  // ── Keyboard navigation ─────────────────────────────────────────
  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (filteredAgents.length === 0) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setHighlightedIndex(prev => Math.min(prev + 1, filteredAgents.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlightedIndex(prev => Math.max(prev - 1, 0));
    } else if (e.key === 'Enter' && highlightedIndex >= 0) {
      e.preventDefault();
      onSelectAgent(filteredAgents[highlightedIndex].id);
    } else if (e.key === 'Escape') {
      setSearchQuery('');
      setHighlightedIndex(-1);
      searchRef.current?.blur();
    }
  }, [filteredAgents, highlightedIndex, onSelectAgent]);

  // ── Scroll highlighted item into view ───────────────────────────
  useEffect(() => {
    if (highlightedIndex >= 0 && listRef.current) {
      const items = listRef.current.querySelectorAll('[data-agent-item]');
      items[highlightedIndex]?.scrollIntoView({ block: 'nearest' });
    }
  }, [highlightedIndex]);

  // ── Reset highlight when search results change ──────────────────
  useEffect(() => {
    setHighlightedIndex(-1);
  }, [searchQuery, showStatusFilter]);

  // ── Focus search on "/" ─────────────────────────────────────────
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === '/' && document.activeElement?.tagName !== 'INPUT') {
        e.preventDefault();
        searchRef.current?.focus();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, []);

  // ── Count agents by status ─────────────────────────────────────
  const statusCounts = useMemo(() => {
    const active = agents.filter(a => a.status === 'active').length;
    const dev = agents.filter(a => a.status === 'development').length;
    return { active, dev };
  }, [agents]);

  return (
    <div
      className={`w-full md:w-60 ${styles.cardBg} border-r ${styles.cardBorder} flex flex-col h-full shrink-0`}
      onKeyDown={handleKeyDown}
    >
      {/* Header */}
      <div className={`p-3 border-b ${styles.cardBorder} ${styles.appBg} flex items-center justify-between`}>
        <div className="flex items-center gap-1.5">
          <Icon name="Bot" size={14} className={styles.accentText} />
          <span className={`font-bold ${styles.cardText} text-[11px]`}>
            {t('aiworkbench.chatbot.agentSelectorTitle')}
          </span>
          <span className={`text-[9px] font-mono ${styles.cardTextMuted} px-1 rounded ${styles.inputBg}`}>
            {agents.length}
          </span>
        </div>
        <button
          onClick={onStartCreate}
          className={`p-1 ${styles.accentText} ${styles.accentHover} hover:text-white ${styles.accentBorder} hover:border-transparent border rounded-md transition-all cursor-pointer flex items-center justify-center`}
          title={t('aiworkbench.chatbot.agentSelectorNewChatbot')}
        >
          <Icon name="Plus" size={11} />
        </button>
      </div>

      {/* Search + Status Filter (T7-4) */}
      <div className={`p-2 border-b ${styles.cardBorder} space-y-1.5`}>
        {/* Search input */}
        <div className="relative">
          <Icon
            name="Search"
            size={10}
            className={`absolute left-2 top-1/2 -translate-y-1/2 ${styles.cardTextMuted} pointer-events-none`}
          />
          <input
            ref={searchRef}
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder={t('aiworkbench.chatbot.agentSelectorSearchPlaceholder')}
            className={`w-full pl-6 pr-2 py-1 text-[9px] ${styles.inputBg} border ${styles.inputBorder} rounded-md outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500`}
          />
          {searchQuery && (
            <button
              onClick={() => setSearchQuery('')}
              className={`absolute right-1.5 top-1/2 -translate-y-1/2 ${styles.cardTextMuted} cursor-pointer`}
            >
              <Icon name="X" size={9} />
            </button>
          )}
        </div>

        {/* Status filter pills */}
        <div className="flex gap-1 text-[8px] font-bold">
          <button
            onClick={() => setShowStatusFilter('all')}
            className={`flex-1 py-0.5 rounded-sm text-center transition-colors ${
              showStatusFilter === 'all'
                ? `${styles.accentBg} text-white`
                : `${styles.inputBg} ${styles.cardTextMuted} hover:${styles.accentBg}/20`
            }`}
          >
            {t('aiworkbench.chatbot.agentSelectorFilterAll')} ({agents.length})
          </button>
          <button
            onClick={() => setShowStatusFilter('active')}
            className={`flex-1 py-0.5 rounded-sm text-center transition-colors flex items-center justify-center gap-1 ${
              showStatusFilter === 'active'
                ? 'bg-emerald-600 text-white'
                : `${styles.inputBg} ${styles.cardTextMuted} hover:bg-emerald-600/20`
            }`}
          >
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
            {statusCounts.active}
          </button>
          <button
            onClick={() => setShowStatusFilter('development')}
            className={`flex-1 py-0.5 rounded-sm text-center transition-colors flex items-center justify-center gap-1 ${
              showStatusFilter === 'development'
                ? 'bg-amber-600 text-white'
                : `${styles.inputBg} ${styles.cardTextMuted} hover:bg-amber-600/20`
            }`}
          >
            <span className="w-1.5 h-1.5 rounded-full bg-amber-400" />
            {statusCounts.dev}
          </button>
        </div>
      </div>

      {/* Chatbots list */}
      <div ref={listRef} className="flex-1 overflow-y-auto p-2 space-y-1">
        {filteredAgents.length === 0 ? (
          <div className={`p-4 text-center text-[10px] ${styles.cardTextMuted}`}>
            <Icon name="SearchX" size={14} className="mx-auto mb-1 opacity-40" />
            <p>{t('aiworkbench.chatbot.agentSelectorNoMatch')}</p>
            {searchQuery && (
              <button
                onClick={() => setSearchQuery('')}
                className={`mt-1 ${styles.accentText} font-bold`}
              >
                {t('aiworkbench.chatbot.agentSelectorClearSearch')}
              </button>
            )}
          </div>
        ) : (
          filteredAgents.map((a, idx) => {
            const isSelected = selectedAgentId === a.id;
            const isHighlighted = idx === highlightedIndex;
            const isActive = a.status === 'active';

            return (
              <div
                key={a.id}
                data-agent-item
                onClick={() => onSelectAgent(a.id)}
                className={`p-2.5 rounded-lg cursor-pointer transition-all flex flex-col gap-1 border ${
                  isSelected
                    ? `${styles.accentBg} ${styles.cardBorder} text-white shadow-xs`
                    : isHighlighted
                    ? `${styles.inputBg} ${styles.cardBorder} ring-1 ring-blue-500/50`
                    : `${styles.cardBg} hover:${styles.inputBg} ${styles.cardBorder} ${styles.cardTextMuted}`
                }`}
              >
                {/* Name + Status indicator */}
                <div className="flex items-center justify-between min-w-0">
                  <div className="flex items-center gap-1.5 font-bold min-w-0">
                    <span className={`p-1 rounded shrink-0 ${
                      isSelected
                        ? 'bg-blue-600 text-white'
                        : isActive
                        ? `${styles.inputBg} ${styles.cardTextMuted}`
                        : `${styles.inputBg} text-amber-500`
                    }`}>
                      <Icon name={a.avatar} size={11} />
                    </span>
                    <span className="truncate text-[11px]">{a.name}</span>
                  </div>
                  {/* Status dot (T7-4) */}
                  <span
                    className={`w-2 h-2 rounded-full shrink-0 ${
                      isActive ? 'bg-emerald-500 animate-pulse' : 'bg-amber-500'
                    }`}
                    title={isActive ? t('aiworkbench.chatbot.agentSelectorStatusActive') : t('aiworkbench.chatbot.agentSelectorStatusDev')}
                  />
                </div>

                {/* Role */}
                <p className={`text-[10px] line-clamp-1 leading-normal ${
                  isSelected ? styles.cardText : styles.cardTextMuted
                }`}>
                  {a.role}
                </p>

                {/* Footer: model + tools count + status badge (T7-4) */}
                <div className={`flex items-center justify-between text-[8px] pt-1 mt-1 border-t ${styles.cardBorder}/10 font-mono`}>
                  <span className={styles.cardTextMuted}>
                    {a.modelId.replace('-1.5-pro', '')}
                  </span>
                  <div className="flex items-center gap-1">
                    {/* Tools count */}
                    <span className={`${styles.cardTextMuted}`} title={t('aiworkbench.chatbot.agentSelectorToolsCount').replace('{actions}', String(a.assignedTools?.actionIds?.length || 0)).replace('{functions}', String(a.assignedTools?.functionIds?.length || 0))}>
                      <Icon name="Wrench" size={8} />
                      {(a.assignedTools?.actionIds?.length || 0) + (a.assignedTools?.functionIds?.length || 0)}
                    </span>
                    {/* Guardrail count */}
                    <span className={`${styles.cardTextMuted}`} title={t('aiworkbench.chatbot.agentSelectorGuardrailsCount').replace('{count}', String(a.guardrailIds?.length || 0))}>
                      <Icon name="Shield" size={8} />
                      {a.guardrailIds?.length || 0}
                    </span>
                    {/* Status badge */}
                    <span className={`px-1 rounded-sm font-extrabold ${
                      isSelected
                        ? 'bg-blue-500/20 text-blue-300'
                        : isActive
                        ? 'bg-emerald-500/10 text-emerald-500'
                        : 'bg-amber-500/10 text-amber-500'
                    }`}>
                      {isActive ? 'ACTIVE' : 'DEV'}
                    </span>
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Workspace Footer Info */}
      <div className={`p-3 border-t ${styles.cardBorder} ${styles.inputBg} text-[9px] ${styles.cardTextMuted} leading-normal font-sans`}>
        <div className={`flex items-center gap-1.5 font-bold ${styles.cardTextMuted} mb-1`}>
          <Icon name="ShieldCheck" size={11} className="text-emerald-500" />
          <span>Sovereign Chatbot Agent</span>
        </div>
        <p>{t('aiworkbench.chatbot.agentSelectorFooterDesc')}</p>
        <p className={`mt-1 text-[8px] ${styles.cardTextMuted}`}>
          {t('aiworkbench.chatbot.agentSelectorShortcuts')}
        </p>
      </div>
    </div>
  );
}
