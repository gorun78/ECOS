/**
 * ExtractionReviewPanel — Knowledge extraction review panel
 * Left column: raw text with entity highlighting (DOMPurify whitelist sanctioned)
 * Right column: extraction result list (entities/relations/rule checkbox confirmation) + manual correction + confirm registration
 *
 * @license Apache-2.0
 */

import React, { useState, useMemo, useCallback } from 'react';
import {
  Check, X, Edit3, RotateCw, ChevronDown, Network, ShieldAlert,
  Layers, Send, Ban, CheckSquare, Square, FileText,
} from 'lucide-react';
import DOMPurify from 'dompurify';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { apiFetch } from '../../../api';

// ── XSS Whitelist: Only allow <mark> highlight tags ────────────────────────

const SANITIZE_CONFIG = {
  ALLOWED_TAGS: ['mark', 'span', 'code', 'strong'],
  ALLOWED_ATTR: ['class', 'style'],
};

function sanitizeHighlight(html: string): string {
  return DOMPurify.sanitize(html, SANITIZE_CONFIG);
}

// ── Types ──────────────────────────────────────────────────────

interface ReviewEntity {
  id: string; name: string; type: string; selected: boolean;
  properties?: Record<string, unknown>;
}
interface ReviewRelation {
  id: string; source: string; target: string; type: string; selected: boolean;
}
interface ReviewRule {
  id: string; name: string; description: string; condition?: string;
  action?: string; selected: boolean;
}
interface ReviewData {
  entities: ReviewEntity[]; relations: ReviewRelation[]; rules: ReviewRule[];
}

interface Props {
  extractionId: string;
  sourceText: string;
  reviewData: ReviewData;
  onBack: () => void;
}

// ── Component ──────────────────────────────────────────────────

export default function ExtractionReviewPanel({ extractionId, sourceText, reviewData, onBack }: Props) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  const [entities, setEntities] = useState<ReviewEntity[]>(reviewData.entities.map(e => ({...e, selected: true})));
  const [relations, setRelations] = useState<ReviewRelation[]>(reviewData.relations.map(r => ({...r, selected: true})));
  const [rules, setRules] = useState<ReviewRule[]>(reviewData.rules.map(r => ({...r, selected: true})));
  const [activeTab, setActiveTab] = useState<'entities'|'relations'|'rules'>('entities');
  const [editingId, setEditingId] = useState<string|null>(null);
  const [editName, setEditName] = useState('');
  const [editType, setEditType] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  // Highlight source text with entity names
  const highlightedSource = useMemo(() => {
    if (!sourceText) return '';
    const names = entities.filter(e => e.selected).map(e => e.name).filter(Boolean);
    if (!names.length) return sourceText;
    const escaped = names.map(n => n.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
    const pattern = new RegExp(`(${escaped.join('|')})`, 'g');
    const parts = sourceText.split(pattern);
    return parts.map((part, i) =>
      names.includes(part)
        ? `<mark class="bg-yellow-200 text-yellow-900 px-0.5 rounded">${part}</mark>`
        : part
    ).join('');
  }, [sourceText, entities]);

  const toggleEntity = useCallback((id: string) => {
    setEntities(prev => prev.map(e => e.id===id ? {...e, selected:!e.selected} : e));
  }, []);
  const toggleRelation = useCallback((id: string) => {
    setRelations(prev => prev.map(r => r.id===id ? {...r, selected:!r.selected} : r));
  }, []);
  const toggleRule = useCallback((id: string) => {
    setRules(prev => prev.map(r => r.id===id ? {...r, selected:!r.selected} : r));
  }, []);

  const startEdit = useCallback((id: string, name: string, type: string) => {
    setEditingId(id); setEditName(name); setEditType(type);
  }, []);
  const saveEdit = useCallback(() => {
    setEntities(prev => prev.map(e => e.id===editingId ? {...e, name:editName, type:editType} : e));
    setEditingId(null);
  }, [editingId, editName, editType]);

  const selectedEntities = entities.filter(e => e.selected);
  const selectedRelations = relations.filter(r => r.selected);
  const selectedRules = rules.filter(r => r.selected);
  const totalSelected = selectedEntities.length + selectedRelations.length + selectedRules.length;

  const handleApprove = useCallback(async () => {
    setSubmitting(true);
    try {
      await apiFetch(`/v1/kb/extraction/${extractionId}/approve`, {
        method: 'POST',
        body: JSON.stringify({
          entities: selectedEntities.map(e => ({id:e.id, name:e.name, type:e.type})),
          relations: selectedRelations.map(r => ({id:r.id, source:r.source, target:r.target, type:r.type})),
          rules: selectedRules.map(r => ({id:r.id, name:r.name, description:r.description})),
        }),
      });
      // Trigger entity linking after approval
      try { await apiFetch('/v1/kb/entity/link', { method: 'POST', body: JSON.stringify({extractionId}) }); } catch {}
      setDone(true);
    } catch(e: any) { console.warn('Approve failed:', e); }
    finally { setSubmitting(false); }
  }, [extractionId, selectedEntities, selectedRelations, selectedRules]);

  const handleReject = useCallback(async () => {
    setSubmitting(true);
    try { await apiFetch(`/v1/kb/extraction/${extractionId}/reject`, { method: 'POST' }); setDone(true); }
    catch(e: any) { console.warn('Reject failed:', e); }
    finally { setSubmitting(false); }
  }, [extractionId]);

  const tabItems: Array<{key:'entities'|'relations'|'rules';labelKey:string;Icon:typeof Network;count:number}> = [
    { key:'entities', labelKey:'extractReview.tabs.entities', Icon: Layers, count: entities.length },
    { key:'relations', labelKey:'extractReview.tabs.relations', Icon: Network, count: relations.length },
    { key:'rules', labelKey:'extractReview.tabs.rules', Icon: ShieldAlert, count: rules.length },
  ];

  if (done) {
    return (
      <div className={`flex flex-col items-center justify-center py-16 space-y-4 ${styles.cardBg} border ${styles.cardBorder} rounded-xl`}>
        <div className="p-4 bg-emerald-100 rounded-2xl"><Check size={32} className="text-emerald-600" /></div>
        <p className={`text-sm font-extrabold ${styles.cardText}`}>{t('extractReview.complete.title')}</p>
        <p className={`text-[11px] ${styles.cardTextMuted}`}>{t('extractReview.complete.subtitle')}</p>
        <button onClick={onBack} className={`px-4 py-2 ${styles.accentBg} text-white font-bold rounded-lg text-xs cursor-pointer`}>
          {t('extractReview.backToUpload')}
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Top bar: back + actions */}
      <div className="flex items-center justify-between">
        <button onClick={onBack} className={`text-[11px] font-bold ${styles.cardTextMuted} hover:underline cursor-pointer flex items-center gap-1`}>
          <ChevronDown size={12} className="rotate-90" /> {t('extractReview.backToUpload')}
        </button>
        <div className="flex items-center gap-2">
          <span className={`text-[10px] ${styles.cardTextMuted}`}>{t('extractReview.selected', { n: totalSelected })}</span>
          <button onClick={handleReject} disabled={submitting}
            className="px-4 py-1.5 bg-rose-500 hover:bg-rose-600 text-white font-bold rounded-lg text-[11px] cursor-pointer disabled:opacity-50 flex items-center gap-1">
            <Ban size={11} /> {t('extractReview.rejectAll')}
          </button>
          <button onClick={handleApprove} disabled={submitting || totalSelected===0}
            className="px-4 py-1.5 bg-emerald-500 hover:bg-emerald-600 text-white font-bold rounded-lg text-[11px] cursor-pointer disabled:opacity-50 flex items-center gap-1">
            {submitting ? <RotateCw size={11} className="animate-spin" /> : <Send size={11} />}
            {t('extractReview.approve')}
          </button>
        </div>
      </div>

      {/* Main split: left source + right review */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Left: Source text with highlighting */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 space-y-2 min-h-[400px]`}>
          <h3 className={`text-[11px] font-extrabold ${styles.cardText} flex items-center gap-1.5`}>
            <FileText size={12} /> {t('extractReview.sourceText')}
          </h3>
          <div
            className={`text-[11px] leading-relaxed font-sans whitespace-pre-wrap overflow-y-auto max-h-[500px] ${styles.cardText}`}
            dangerouslySetInnerHTML={{ __html: sanitizeHighlight(highlightedSource) }}
          />
        </div>

        {/* Right: Review results with tabs */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden`}>
          {/* Tabs */}
          <div className="flex border-b" style={{ borderColor: 'var(--app-border, #E2E8F0)' }}>
            {tabItems.map(tab => (
              <button key={tab.key} onClick={() => setActiveTab(tab.key)}
                className={`flex-1 px-3 py-2 text-[10px] font-bold flex items-center justify-center gap-1.5 transition-colors cursor-pointer ${
                  activeTab===tab.key ? `${styles.accentBg} text-white` : `${styles.cardTextMuted} hover:opacity-80`
                }`}>
                <tab.Icon size={11} /> {t(tab.labelKey)} ({tab.count})
              </button>
            ))}
          </div>

          {/* Tab content */}
          <div className="divide-y max-h-[460px] overflow-y-auto" style={{ ['--divide-color' as string]: 'var(--app-border, #E2E8F0)' }}>
            {activeTab==='entities' && entities.map(entity => (
              <div key={entity.id} className="px-3 py-2.5 flex items-start gap-2.5 hover:bg-opacity-20 data-[hover]:hover:bg-black /[0.02]">
                <button onClick={() => toggleEntity(entity.id)} className="mt-0.5 shrink-0 cursor-pointer">
                  {entity.selected ? <CheckSquare size={14} className="text-emerald-500" /> : <Square size={14} className="text-zinc-300" />}
                </button>
                <div className="flex-1 min-w-0">
                  {editingId===entity.id ? (
                    <div className="flex gap-1.5">
                      <input value={editName} onChange={e=>setEditName(e.target.value)}
                        className={`flex-1 px-2 py-1 ${styles.inputBg} ${styles.inputBorder} border rounded text-[10px] font-bold ${styles.inputText}`} />
                      <input value={editType} onChange={e=>setEditType(e.target.value)}
                        className={`w-20 px-2 py-1 ${styles.inputBg} ${styles.inputBorder} border rounded text-[10px] font-bold ${styles.inputText}`} />
                      <button onClick={saveEdit} className="px-2 py-1 bg-emerald-500 text-white rounded text-[10px]"><Check size={10}/></button>
                      <button onClick={()=>setEditingId(null)} className="px-2 py-1 bg-zinc-200 rounded text-[10px]"><X size={10}/></button>
                    </div>
                  ) : (
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className={`text-[11px] font-bold ${styles.cardText}`}>{entity.name}</span>
                      <span className="px-1.5 py-0.5 bg-emerald-50 text-emerald-600 text-[9px] font-bold rounded">{entity.type}</span>
                      {entity.properties && Object.entries(entity.properties).map(([k,v]) => (
                        <span key={k} className={`text-[8px] font-mono ${styles.cardTextMuted} ${styles.cardBg} px-1 py-0.5 rounded`}>{k}:{String(v)}</span>
                      ))}
                      <button onClick={()=>startEdit(entity.id,entity.name,entity.type)}
                        className="ml-auto text-[9px] text-amber-500 hover:underline cursor-pointer flex items-center gap-0.5">
                        <Edit3 size={9}/> {t('extractReview.edit')}
                      </button>
                    </div>
                  )}
                </div>
              </div>
            ))}
            {activeTab==='relations' && relations.map(rel => (
              <div key={rel.id} className="px-3 py-2.5 flex items-center gap-2.5 hover:bg-black/5">
                <button onClick={() => toggleRelation(rel.id)} className="shrink-0 cursor-pointer">
                  {rel.selected ? <CheckSquare size={14} className="text-emerald-500" /> : <Square size={14} className="text-zinc-300" />}
                </button>
                <span className={`text-[10px] font-bold ${styles.cardText}`}>{rel.source}</span>
                <span className="px-1.5 py-0.5 bg-blue-50 text-blue-600 text-[9px] font-bold rounded-full">{rel.type}</span>
                <span className={`text-[10px] font-bold ${styles.cardText}`}>{rel.target}</span>
              </div>
            ))}
            {activeTab==='rules' && rules.map(rule => (
              <div key={rule.id} className="px-3 py-2.5 flex items-start gap-2.5 hover:bg-black/5">
                <button onClick={() => toggleRule(rule.id)} className="mt-0.5 shrink-0 cursor-pointer">
                  {rule.selected ? <CheckSquare size={14} className="text-emerald-500" /> : <Square size={14} className="text-zinc-300" />}
                </button>
                <div className="flex-1 min-w-0">
                  <span className={`text-[11px] font-bold ${styles.cardText}`}>{rule.name}</span>
                  <p className={`text-[10px] ${styles.cardTextMuted} mt-0.5`}>{rule.description}</p>
                  {rule.condition && <span className={`text-[9px] font-mono ${styles.cardTextMuted} ${styles.cardBg} px-1 py-0.5 rounded`}>IF: {rule.condition}</span>}
                </div>
              </div>
            ))}
            {(activeTab==='entities'&&!entities.length || activeTab==='relations'&&!relations.length || activeTab==='rules'&&!rules.length) && (
              <div className={`px-3 py-8 text-center text-[10px] ${styles.muted}`}>{t('extractReview.noData')}</div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
