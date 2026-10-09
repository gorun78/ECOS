import { useState, useEffect } from 'react';
import { fetchCatalogTables, type CatalogTable } from '../../../api';
import { inputClasses } from './helpers';

// ── Table Select (from data catalog) ─────────────────────────
export default function TableSelect({ value, onChange, styles, placeholder }: {
  value: string; onChange: (v: string) => void; styles: any; placeholder?: string;
}) {
  const [tables, setTables] = useState<CatalogTable[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchText, setSearchText] = useState('');

  useEffect(() => {
    setLoading(true);
    fetchCatalogTables().then(t => { setTables(t); setLoading(false); }).catch(() => setLoading(false));
  }, []);

  const filtered = searchText
    ? tables.filter(t => t.resourceName.toLowerCase().includes(searchText.toLowerCase()))
    : tables;

  return (
    <div className="relative">
      <input
        type="text"
        value={searchText || value}
        onChange={e => { setSearchText(e.target.value); if (!e.target.value) onChange(''); }}
        onFocus={() => setSearchText('')}
        placeholder={placeholder || (loading ? '加载表列表...' : '输入表名或搜索...')}
        className={inputClasses(styles)}
      />
      {searchText && filtered.length > 0 && (
        <div className={`absolute z-50 mt-1 w-full max-h-48 overflow-auto rounded-lg ${styles.cardBg} border ${styles.cardBorder} shadow-lg`}>
          {filtered.slice(0, 20).map(t => (
            <button type="button"
              key={t.catalogId}
              onClick={() => { onChange(t.resourceName); setSearchText(''); }}
              className={`w-full text-left px-3 py-2 text-sm hover:${styles.sidebarHoverBg} ${styles.cardText} cursor-pointer`}
            >
              <span className="font-medium">{t.resourceName}</span>
              <span className={`ml-2 text-xs ${styles.muted}`}>{t.resourceType}</span>
              {t.orgName && <span className={`ml-2 text-xs ${styles.muted}`}>{t.orgName}</span>}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
