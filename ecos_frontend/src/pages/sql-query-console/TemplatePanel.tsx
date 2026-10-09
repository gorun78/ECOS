/**
 * SQL Query Console — Template Manager Panel
 * @license Apache-2.0
 */
import React, { useState, useEffect } from 'react';
import * as Icons from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';
import { apiFetchData } from '../../api';
import type { QueryTemplate } from './types';

interface TemplatePanelProps {
  show: boolean;
  datasourceId: string | null;
  onLoad: (template: QueryTemplate) => void;
  onClose: () => void;
}

type IconComponent = React.ComponentType<{ size?: number; className?: string }>;

/** 模板列表响应：裸数组或 { data } 信封（apiFetchData 已解包一层） */
type TemplatesResponse = QueryTemplate[] | { data?: QueryTemplate[] } | null;

const Icon = ({ name, size = 14 }: { name: string; size?: number }) => {
  const iconMap = Icons as unknown as Record<string, IconComponent>;
  const Comp = iconMap[name] || iconMap.HelpCircle;
  return <Comp size={size} />;
};

export default function TemplatePanel({ show, datasourceId, onLoad, onClose }: TemplatePanelProps) {
  const { styles } = useTheme();
  const [templates, setTemplates] = useState<QueryTemplate[]>([]);
  const [loading, setLoading] = useState(false);

  const loadTemplates = () => {
    if (!datasourceId) return;
    setLoading(true);
    apiFetchData<TemplatesResponse>(`/api/v1/engine/data/query/templates?datasourceId=${datasourceId}&page=1&pageSize=50`)
      .then((d) => {
        const list = Array.isArray(d) ? d : d?.data;
        setTemplates(Array.isArray(list) ? list : []);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  };

  useEffect(() => { if (show) loadTemplates(); }, [show, datasourceId]);

  const handleDelete = async (id: string) => {
    try {
      await apiFetchData(`/api/v1/engine/data/query/templates/${id}`, { method: 'DELETE' });
      loadTemplates();
    } catch { /* ignore */ }
  };

  if (!show) return null;

  return (
    <div className={`w-56 border-l ${styles.cardBorder} ${styles.cardBg} flex flex-col shrink-0 overflow-hidden`}>
      <div className={`px-2.5 py-2 border-b ${styles.cardBorder} flex items-center justify-between`}>
        <span className={`text-[10px] font-bold uppercase ${styles.cardTextMuted} flex items-center gap-1.5`}>
          <Icon name="Bookmark" size={13} />模板
        </span>
        <button type="button" onClick={onClose} className={`${styles.cardTextMuted} hover:opacity-70 rounded`}>
          <Icon name="X" size={13} />
        </button>
      </div>
      <div className="flex-1 overflow-y-auto">
        {loading ? (
            <div className={`flex items-center justify-center py-8 ${styles.cardTextMuted} text-[11px]`}>
              <Icon name="Loader2" /> 加载中...
            </div>
          ) : templates.length === 0 ? (
            <div className={`text-center py-8 ${styles.cardTextMuted} text-[10px]`}>暂无模板</div>
          ) : (
          templates.map(t => (
            <div key={t.id} className={`px-2.5 py-2 border-b ${styles.cardBorder} hover:bg-white/5 group`}>
              <div className="flex items-center justify-between">
                <button type="button" onClick={() => onLoad(t)} className={`text-[11px] font-medium ${styles.cardText} text-left truncate hover:text-blue-400`}>
                  {t.name}
                </button>
                <button type="button" onClick={() => handleDelete(t.id)} className={`${styles.cardTextMuted} hover:text-rose-400 opacity-0 group-hover:opacity-100`}>
                  <Icon name="Trash2" size={11} />
                </button>
              </div>
              <div className={`text-[9px] ${styles.cardTextMuted} mt-0.5 truncate`}>
                {t.sqlContent.substring(0, 60)}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
