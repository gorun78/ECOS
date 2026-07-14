/**
 * SQL Query Console — Main Entry
 * 集成到数据工作台标签页中
 * @license Apache-2.0
 */
import React, { useState, useEffect, useCallback } from 'react';
import { useTheme } from '../components/ThemeContext';
import { apiFetchData } from '../api';
import QueryToolbar from './sql-query-console/QueryToolbar';
import SchemaTree from './sql-query-console/SchemaTree';
import ResultTable from './sql-query-console/ResultTable';
import TemplatePanel from './sql-query-console/TemplatePanel';
import HistoryPanel from './sql-query-console/HistoryPanel';
import type { DataSource, QueryResult, QueryTemplate } from './sql-query-console/types';

export default function SqlQueryConsole() {
  const { styles } = useTheme();
  const [dataSources, setDataSources] = useState<DataSource[]>([]);
  const [selectedDsId, setSelectedDsId] = useState<string | null>(null);
  const [sql, setSql] = useState('SELECT 1');
  const [result, setResult] = useState<QueryResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isExecuting, setIsExecuting] = useState(false);
  const [showTemplates, setShowTemplates] = useState(false);
  const [showHistory, setShowHistory] = useState(false);

  // 加载数据源列表
  useEffect(() => {
    apiFetchData<any>('/datanet/datasource')
      .then((d: any) => setDataSources(Array.isArray(d) ? d : (d?.data || [])))
      .catch(() => {});
  }, []);

  // 执行查询
  const executeQuery = useCallback(async () => {
    if (!selectedDsId || !sql.trim()) return;
    setIsExecuting(true);
    setError(null);
    try {
      const res = await apiFetchData<any>('/api/v1/engine/data/query/execute', {
        method: 'POST',
        body: JSON.stringify({
          datasourceId: selectedDsId,
          sql: sql.trim(),
          maxRows: 1000,
          timeoutSeconds: 30
        })
      });
      setResult({
        columns: res?.columns || [],
        rows: res?.rows || [],
        rowCount: res?.rowCount || 0,
        elapsedMs: res?.elapsedMs || 0
      });
    } catch (e: any) {
      setError(e?.message || '查询执行失败');
      setResult(null);
    } finally {
      setIsExecuting(false);
    }
  }, [selectedDsId, sql]);

  // Ctrl+Enter 快捷键
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') executeQuery();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [executeQuery]);

  // 加载模板到编辑器
  const handleLoadTemplate = (t: QueryTemplate) => {
    setSql(t.sqlContent);
    setSelectedDsId(t.datasourceId);
    setShowTemplates(false);
  };

  return (
    <div className={`flex h-full overflow-hidden ${styles.appBg}`}>
      {/* 左侧 Schema 树 */}
      <SchemaTree datasourceId={selectedDsId} />

      {/* 中间主区域 */}
      <div className="flex-1 flex flex-col overflow-hidden min-w-0">
        {/* 工具栏 */}
        <QueryToolbar
          dataSources={dataSources}
          selectedDsId={selectedDsId}
          onSelectDs={setSelectedDsId}
          onExecute={executeQuery}
          onSaveTemplate={() => setShowTemplates(true)}
          onToggleHistory={() => setShowHistory(!showHistory)}
          isExecuting={isExecuting}
          isHistoryOpen={showHistory}
        />

        {/* SQL 编辑器 */}
        <div className="h-[40%] min-h-[120px] border-b border-slate-800">
          <textarea
            value={sql}
            onChange={e => setSql(e.target.value)}
            className={`w-full h-full p-3 text-xs font-mono resize-none outline-none ${styles.inputBg} ${styles.inputText}`}
            placeholder="在此输入 SQL 查询...&#10;Ctrl+Enter 执行"
            spellCheck={false}
          />
        </div>

        {/* 结果区域 */}
        <ResultTable result={result} loading={isExecuting} error={error} />
      </div>

      {/* 右侧面板 */}
      <TemplatePanel
        show={showTemplates}
        datasourceId={selectedDsId}
        onLoad={handleLoadTemplate}
        onClose={() => setShowTemplates(false)}
      />
      <HistoryPanel
        show={showHistory}
        onLoadSql={setSql}
        onClose={() => setShowHistory(false)}
      />
    </div>
  );
}
