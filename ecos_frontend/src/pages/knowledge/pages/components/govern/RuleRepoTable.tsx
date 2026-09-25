/**
 * PMO-D Batch 2 — F7 GovernPage 下区左 RuleRepoTable（PRD §3.2 F7 下区左）。
 *
 * 原 KnowledgeRuleRepositoryTab 规则仓库表格迁移：
 *  - fetchRules（GET /api/v1/knowledge/compliance-rules）
 *  - CRUD 4 按钮：create / edit / disable / delete
 *  - 列表行：name / domain / status / version / updatedAt + 4 按钮
 *
 * 禁用主题 §4.1：0 硬编码色值
 * i18n §4.3：0 硬编码中文（key: knowledge.govern.rule_*）
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Archive, BookOpen, CheckCircle2, Edit3, Loader2, Pencil, Plus, RefreshCw,
  Search, ShieldCheck, Trash2, X,
} from 'lucide-react';
import { apiFetchData } from '../../../../../api';
import { useLanguage } from '../../../../../components/LanguageContext';
import { useTheme } from '../../../../../components/ThemeContext';

/** 与 typesAndConstants.RuleRepository 对齐（字段子集） */
interface RuleItem {
  id: string;
  name: string;
  domain: string;
  status: 'DRAFT' | 'IN_REVIEW' | 'ACTIVE' | 'DEPRECATED';
  version: number;
  updatedAt?: string;
}

interface NewRuleForm {
  name: string;
  domain: string;
  status: RuleItem['status'];
}

const DEFAULT_FORM: NewRuleForm = { name: '', domain: '', status: 'DRAFT' };

/** 状态 → theme chip 类 */
function statusChip(status: RuleItem['status']): { bg: 'success' | 'warning' | 'danger' | 'info'; labelKey: string } {
  switch (status) {
    case 'ACTIVE':       return { bg: 'success', labelKey: 'knowledge.govern.rule_status_active' };
    case 'IN_REVIEW':    return { bg: 'warning', labelKey: 'knowledge.govern.rule_status_review' };
    case 'DRAFT':        return { bg: 'info',    labelKey: 'knowledge.govern.rule_status_draft' };
    case 'DEPRECATED':   return { bg: 'danger',  labelKey: 'knowledge.govern.rule_status_deprecated' };
    default:             return { bg: 'info',    labelKey: 'knowledge.govern.rule_status_draft' };
  }
}

export default function RuleRepoTable() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [rules, setRules] = useState<RuleItem[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [search, setSearch] = useState<string>('');
  const [domain, setDomain] = useState<string>('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editForm, setEditForm] = useState<NewRuleForm>(DEFAULT_FORM);
  const [showCreate, setShowCreate] = useState<boolean>(false);
  const [createForm, setCreateForm] = useState<NewRuleForm>(DEFAULT_FORM);
  const [saving, setSaving] = useState<boolean>(false);
  const [errorBanner, setErrorBanner] = useState<string | null>(null);

  const loadRules = useCallback(async (keywords?: string, domainFilter?: string) => {
    setLoading(true);
    setErrorBanner(null);
    try {
      const data = await apiFetchData<RuleItem[]>(
        `/api/v1/knowledge/compliance-rules?${
          (keywords ? `keyword=${encodeURIComponent(keywords)}` : '') +
          (keywords && domainFilter ? '&' : '') +
          (domainFilter ? `domain=${encodeURIComponent(domainFilter)}` : '')
        }`.replace('?&', '?'),
      );
      const list = Array.isArray(data) ? data : [];
      setRules(list);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setErrorBanner(`${t('knowledge.govern.rule_load_error')}: ${msg}`);
      setRules([]);
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => { void loadRules(); }, [loadRules]);

  const downloadFiltered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return rules.filter(r => {
      const matchesSearch = !q || r.name.toLowerCase().includes(q) || (r.domain || '').toLowerCase().includes(q);
      const matchesDomain = !domain || r.domain === domain;
      return matchesSearch && matchesDomain;
    });
  }, [rules, search, domain]);

  /** 创建规则：POST /compliance-rules */
  const handleCreate = useCallback(async () => {
    if (!createForm.name.trim() || !createForm.domain.trim()) {
      setErrorBanner(t('knowledge.govern.rule_create_validation'));
      return;
    }
    setSaving(true);
    try {
      await apiFetchData<RuleItem>(
        '/api/v1/knowledge/compliance-rules',
        { method: 'POST', body: JSON.stringify({ name: createForm.name, domain: createForm.domain, status: createForm.status }) },
      );
      setShowCreate(false);
      setCreateForm(DEFAULT_FORM);
      await loadRules(search, domain);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setErrorBanner(`${t('knowledge.govern.rule_create_failed')}: ${msg}`);
    } finally {
      setSaving(false);
    }
  }, [createForm, search, domain, loadRules, t]);

  /** 编辑规则：PUT /compliance-rules/{id} */
  const handleSaveEdit = useCallback(async () => {
    if (!editingId) return;
    if (!editForm.name.trim() || !editForm.domain.trim()) {
      setErrorBanner(t('knowledge.govern.rule_edit_validation'));
      return;
    }
    setSaving(true);
    try {
      await apiFetchData<RuleItem>(`/api/v1/knowledge/compliance-rules/${editingId}`, {
        method: 'PUT',
        body: JSON.stringify({ name: editForm.name, domain: editForm.domain, status: editForm.status }),
      });
      setEditingId(null);
      setEditForm(DEFAULT_FORM);
      await loadRules(search, domain);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setErrorBanner(`${t('knowledge.govern.rule_edit_failed')}: ${msg}`);
    } finally {
      setSaving(false);
    }
  }, [editingId, editForm, search, domain, loadRules, t]);

  const startEdit = useCallback((r: RuleItem) => {
    setEditingId(r.id);
    setEditForm({ name: r.name, domain: r.domain, status: r.status });
    setShowCreate(false);
  }, []);

  /** 禁用：PUT status=DEPRECATED */
  const handleDisable = useCallback(async (r: RuleItem) => {
    setSaving(true);
    try {
      await apiFetchData<RuleItem>(`/api/v1/knowledge/compliance-rules/${r.id}`, {
        method: 'PUT',
        body: JSON.stringify({ name: r.name, domain: r.domain, status: 'DEPRECATED' }),
      });
      await loadRules(search, domain);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setErrorBanner(`${t('knowledge.govern.rule_disable_failed')}: ${msg}`);
    } finally {
      setSaving(false);
    }
  }, [search, domain, loadRules, t]);

  /** 删除：DELETE /compliance-rules/{id} */
  const handleDelete = useCallback(async (r: RuleItem) => {
    setSaving(true);
    try {
      await apiFetchData<void>(`/api/v1/knowledge/compliance-rules/${r.id}`, { method: 'DELETE' });
      await loadRules(search, domain);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setErrorBanner(`${t('knowledge.govern.rule_delete_failed')}: ${msg}`);
    } finally {
      setSaving(false);
    }
  }, [search, domain, loadRules, t]);

  const chipStyle = (bg: 'success' | 'warning' | 'danger' | 'info') => {
    if (bg === 'success') return { background: styles.successBg, color: styles.successText };
    if (bg === 'warning') return { background: styles.warningBg, color: styles.warningText };
    if (bg === 'danger') return { background: styles.dangerBg, color: styles.dangerText };
    return { background: styles.infoBg, color: styles.infoText };
  };

  return (
    <div
      className="rounded-md border flex flex-col"
      style={{ borderColor: styles.cardBorder, background: styles.cardBg }}
    >
      {/* 标题栏 */}
      <div className="flex items-center justify-between px-3 py-2 border-b" style={{ borderColor: styles.cardBorder }}>
        <div className="flex items-center gap-2 text-sm font-semibold">
          <ShieldCheck className="w-4 h-4" style={{ color: styles.accentText }} />
          <span>{t('knowledge.govern.rule_repo')}</span>
          <span className="text-[10px] font-mono" style={{ color: styles.muted }}>
            {t('knowledge.govern.rule_count', { count: rules.length })}
          </span>
        </div>
        <div className="flex items-center gap-1.5">
          <button
            type="button"
            onClick={() => void loadRules(search, domain)}
            className="p-1 rounded border cursor-pointer hover:opacity-70 transition disabled:opacity-50"
            style={{ borderColor: styles.cardBorder, color: styles.cardTextMuted }}
            title={t('knowledge.govern.rule_refresh')}
            disabled={loading}
          >
            {loading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <RefreshCw className="w-3.5 h-3.5" />}
          </button>
          <button
            type="button"
            onClick={() => { setShowCreate((v) => !v); setEditingId(null); setCreateForm(DEFAULT_FORM); }}
            disabled={saving}
            className="inline-flex items-center gap-1 px-2 py-1 rounded text-[10px] font-bold disabled:opacity-50 cursor-pointer"
            style={{ background: styles.accentBg, color: 'rgba(255,255,255,0.95)' }}
            title={t('knowledge.govern.rule_create')}
          >
            <Plus className="w-3 h-3" />
            {t('knowledge.govern.rule_create')}
          </button>
        </div>
      </div>

      {errorBanner && (
        <div className="px-3 py-1.5 text-[11px] border-b flex items-center gap-2"
             style={{ borderColor: styles.cardBorder, background: styles.dangerBg, color: styles.dangerText }}>
          <X className="w-3 h-3 flex-shrink-0" />
          <span className="truncate">{errorBanner}</span>
        </div>
      )}

      {/* 创建表单（showCreate 时展开） */}
      {showCreate && (
        <div className="px-3 py-2 border-b space-y-2" style={{ borderColor: styles.cardBorder, background: styles.inputBg }}>
          <div className="flex flex-col md:flex-row md:items-end gap-2">
            <div className="flex-1 space-y-1">
              <label className="text-[9px] font-bold uppercase" style={{ color: styles.cardTextMuted }}>
                {t('knowledge.govern.rule_field_name')}
              </label>
              <input
                type="text"
                value={createForm.name}
                onChange={(e) => setCreateForm((p) => ({ ...p, name: e.target.value }))}
                placeholder={t('knowledge.govern.rule_field_name_placeholder')}
                className="w-full px-2 py-1 rounded text-[11px] outline-none"
                style={{ background: styles.inputBg, color: styles.inputText, border: `1px solid ${styles.inputBorder}` }}
              />
            </div>
            <div className="flex-1 space-y-1">
              <label className="text-[9px] font-bold uppercase" style={{ color: styles.cardTextMuted }}>
                {t('knowledge.govern.rule_field_domain')}
              </label>
              <input
                type="text"
                value={createForm.domain}
                onChange={(e) => setCreateForm((p) => ({ ...p, domain: e.target.value }))}
                placeholder={t('knowledge.govern.rule_field_domain_placeholder')}
                className="w-full px-2 py-1 rounded text-[11px] outline-none"
                style={{ background: styles.inputBg, color: styles.inputText, border: `1px solid ${styles.inputBorder}` }}
              />
            </div>
            <div className="flex gap-1.5">
              <button
                type="button"
                onClick={() => void handleCreate()}
                disabled={saving}
                className="px-3 py-1 rounded text-[10px] font-bold disabled:opacity-50 cursor-pointer"
                style={{ background: styles.successBg, color: styles.successText }}
              >
                {t('knowledge.govern.rule_action_save')}
              </button>
              <button
                type="button"
                onClick={() => { setShowCreate(false); setCreateForm(DEFAULT_FORM); }}
                className="px-2 py-1 rounded text-[10px] font-bold cursor-pointer"
                style={{ background: styles.inputBg, color: styles.cardTextMuted, border: `1px solid ${styles.inputBorder}` }}
              >
                {t('knowledge.govern.rule_action_cancel')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 搜索 + domain 过滤 */}
      <div className="px-3 py-2 flex items-center gap-2 border-b" style={{ borderColor: styles.cardBorder }}>
        <div className="flex-1 relative">
          <Search className="w-3.5 h-3.5 absolute left-2 top-1/2 -translate-y-1/2" style={{ color: styles.muted }} />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder={t('knowledge.govern.rule_search_placeholder')}
            className="w-full pl-7 pr-2 py-1 rounded text-[11px] outline-none"
            style={{ background: styles.inputBg, color: styles.inputText, border: `1px solid ${styles.inputBorder}` }}
          />
        </div>
        <button
          type="button"
          onClick={() => { setDomain(''); }}
          className="px-2 py-1 rounded text-[10px] font-bold cursor-pointer"
          style={{ background: domain ? styles.accentBg : styles.inputBg, color: domain ? 'rgba(255,255,255,0.95)' : styles.cardTextMuted, border: `1px solid ${styles.inputBorder}` }}
          title="All domains"
        >
          {t('knowledge.govern.rule_domain_all')}
        </button>
      </div>

      {/* 表格头 */}
      <div className="grid grid-cols-[1fr_100px_110px_50px_140px] gap-2 px-3 py-1.5 border-b text-[9px] font-bold uppercase tracking-wider"
           style={{ borderColor: styles.cardBorder, color: styles.cardTextMuted }}>
        <span>{t('knowledge.govern.rule_col_name')}</span>
        <span>{t('knowledge.govern.rule_col_domain')}</span>
        <span>{t('knowledge.govern.rule_col_status')}</span>
        <span className="text-right">{t('knowledge.govern.rule_col_version')}</span>
        <span className="text-right">{t('knowledge.govern.rule_col_actions')}</span>
      </div>

      <div className="flex-1 overflow-y-auto max-h-56">
        {loading ? (
          <div className="p-4 text-center text-[11px] flex items-center justify-center" style={{ color: styles.muted }}>
            <Loader2 className="w-3.5 h-3.5 animate-spin mr-1.5" />
            {t('knowledge.govern.rule_loading')}
          </div>
        ) : downloadFiltered.length === 0 ? (
          <div className="p-4 text-center text-[11px]" style={{ color: styles.muted }}>
            <BookOpen className="w-5 h-5 mx-auto mb-1 opacity-40" />
            {t('knowledge.govern.rule_empty')}
          </div>
        ) : (
          downloadFiltered.slice(0, 50).map((r) => {
            const chip = statusChip(r.status);
            const isEditing = editingId === r.id;
            return (
              <div key={r.id}
                   className="grid grid-cols-[1fr_100px_110px_50px_140px] gap-2 px-3 py-2 border-b items-center"
                   style={{ borderColor: styles.cardBorder }}>
                {/* name + 行内编辑 */}
                <div className="flex items-center gap-2 min-w-0">
                  {isEditing ? (
                    <input
                      type="text"
                      value={editForm.name}
                      onChange={(e) => setEditForm((p) => ({ ...p, name: e.target.value }))}
                      className="w-full flex-1 px-1.5 py-0.5 rounded text-[10px] outline-none"
                      style={{ background: styles.inputBg, color: styles.inputText, border: `1px solid ${styles.inputBorder}` }}
                    />
                  ) : (
                    <span className="text-[11px] font-bold truncate" style={{ color: styles.cardText }}>{r.name}</span>
                  )}
                  {r.updatedAt && !isEditing && (
                    <span className="text-[9px] font-mono shrink-0" style={{ color: styles.muted }}>
                      {r.updatedAt.slice(0, 10)}
                    </span>
                  )}
                </div>

                {/* domain + 行内编辑 */}
                <div className="min-w-0">
                  {isEditing ? (
                    <input
                      type="text"
                      value={editForm.domain}
                      onChange={(e) => setEditForm((p) => ({ ...p, domain: e.target.value }))}
                      className="w-full px-1.5 py-0.5 rounded text-[10px] outline-none"
                      style={{ background: styles.inputBg, color: styles.inputText, border: `1px solid ${styles.inputBorder}` }}
                    />
                  ) : (
                    <span className="text-[10px] font-mono truncate block" style={{ color: styles.cardTextMuted }}>{r.domain}</span>
                  )}
                </div>

                {/* status */}
                <span
                  className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[9px] font-mono font-bold self-start"
                  style={chipStyle(chip.bg)}
                >
                  {r.status === 'DEPRECATED' ? <Archive className="w-2.5 h-2.5" /> : null}
                  {t(chip.labelKey)}
                </span>

                {/* version */}
                <span className="text-[10px] font-mono text-right" style={{ color: styles.cardTextMuted }}>
                  v{r.version || 1}
                </span>

                {/* actions: edit / disable / delete（PRD：CRUD 4 按钮） */}
                <div className="flex items-center justify-end gap-1">
                  {isEditing ? (
                    <>
                      <button
                        type="button"
                        onClick={() => void handleSaveEdit()}
                        disabled={saving}
                        className="p-1 rounded cursor-pointer disabled:opacity-50"
                        style={{ background: styles.successBg, color: styles.successText }}
                        title={t('knowledge.govern.rule_action_save')}
                      >
                        <CheckCircle2 className="w-3 h-3" />
                      </button>
                      <button
                        type="button"
                        onClick={() => { setEditingId(null); setEditForm(DEFAULT_FORM); }}
                        className="p-1 rounded cursor-pointer"
                        style={{ background: styles.inputBg, color: styles.cardTextMuted, border: `1px solid ${styles.inputBorder}` }}
                        title={t('knowledge.govern.rule_action_cancel')}
                      >
                        <X className="w-3 h-3" />
                      </button>
                    </>
                  ) : (
                    <>
                      <button
                        type="button"
                        onClick={() => startEdit(r)}
                        className="p-1 rounded cursor-pointer hover:opacity-70 transition"
                        style={{ color: styles.accentText }}
                        title={t('knowledge.govern.rule_action_edit')}
                      >
                        <Edit3 className="w-3.5 h-3.5" />
                      </button>
                      <button
                        type="button"
                        onClick={() => void handleDisable(r)}
                        disabled={saving || r.status === 'DEPRECATED'}
                        className="p-1 rounded cursor-pointer disabled:opacity-30 hover:opacity-70 transition"
                        style={{ color: styles.warningText }}
                        title={t('knowledge.govern.rule_action_disable')}
                      >
                        <Archive className="w-3.5 h-3.5" />
                      </button>
                      <button
                        type="button"
                        onClick={() => void handleDelete(r)}
                        disabled={saving}
                        className="p-1 rounded cursor-pointer disabled:opacity-30 hover:opacity-70 transition"
                        style={{ color: styles.dangerText }}
                        title={t('knowledge.govern.rule_action_delete')}
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </>
                  )}
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
