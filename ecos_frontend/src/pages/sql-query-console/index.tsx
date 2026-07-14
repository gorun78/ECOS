/**
 * SQL Query Console — 统一 SQL 查询控制台
 * 布局: 左(Schema树) | 中上(Monaco编辑器) | 中下(结果表格) | 右(模板面板)
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useTheme } from '../../components/ThemeContext';
import * as Icons from 'lucide-react';
import Editor, { type OnMount } from '@monaco-editor/react';
import type { editor } from 'monaco-editor';

import SchemaTree from './SchemaTree';
import QueryToolbar from './QueryToolbar';
import ResultTable from './ResultTable';
import TemplatePanel from './TemplatePanel';
import HistoryPanel from './HistoryPanel';

import {
  fetchDataSources,
  executeQuery as apiExecuteQuery,
  saveTemplate,
} from './api';

import type {
  DataSource,
  QueryExecuteResponse,
  QueryTemplate,
  SaveTemplateRequest,
  ColumnMeta,
  Pagination,
} from './types';

const Icon = ({ name, size = 16, className = '' }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

interface SQLQueryConsoleProps {
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

export default function SQLQueryConsole({ showToast }: SQLQueryConsoleProps) {
  const { styles } = useTheme();

  // ── 数据源 ──
  const [dataSources, setDataSources] = useState<DataSource[]>([]);
  const [selectedDsId, setSelectedDsId] = useState<string | null>(null);

  // ── SQL 编辑器 ──
  const [sql, setSql] = useState('SELECT 1');
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null);

  // ── 查询结果 ──
  const [columns, setColumns] = useState<ColumnMeta[]>([]);
  const [rows, setRows] = useState<Record<string, unknown>[]>([]);
  const [pagination, setPagination] = useState<Pagination>({ page: 1, pageSize: 50, total: 0 });
  const [executionTimeMs, setExecutionTimeMs] = useState(0);
  const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined);
  const [isExecuting, setIsExecuting] = useState(false);

  // ── 面板状态 ──
  const [isHistoryOpen, setIsHistoryOpen] = useState(false);
  const [showRightPanel, setShowRightPanel] = useState(true);
  const [showLeftPanel, setShowLeftPanel] = useState(true);

  // ── 保存模板对话框 ──
  const [showSaveDialog, setShowSaveDialog] = useState(false);
  const [saveName, setSaveName] = useState('');
  const [saveDesc, setSaveDesc] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  // ── 初始化加载数据源 ──
  useEffect(() => {
    fetchDataSources().then(dsList => {
      setDataSources(dsList);
      // 默认选中第一个数据源
      if (dsList.length > 0 && !selectedDsId) {
        setSelectedDsId(dsList[0].datasourceId);
      }
    });
  }, []);

  // ── 执行查询 ──
  const handleExecute = useCallback(async (page: number = 1) => {
    if (!selectedDsId) {
      if (showToast) showToast('error', '请先选择数据源');
      return;
    }
    if (!sql.trim()) {
      if (showToast) showToast('error', '请输入 SQL 语句');
      return;
    }

    setIsExecuting(true);
    setErrorMessage(undefined);

    try {
      const result: QueryExecuteResponse = await apiExecuteQuery({
        datasourceId: selectedDsId,
        sql: sql.trim(),
        page,
        pageSize: pagination.pageSize,
      });

      setColumns(result.columns || []);
      setRows(result.rows || []);
      setPagination({
        page: result.page || 1,
        pageSize: result.pageSize || 50,
        total: result.total || 0,
      });
      setExecutionTimeMs(result.executionTimeMs || 0);

      if (showToast) {
        showToast('success', `查询完成，返回 ${result.total || 0} 行 (${result.executionTimeMs}ms)`);
      }
    } catch (e: any) {
      const msg = e?.message || '查询执行失败';
      setErrorMessage(msg);
      setColumns([]);
      setRows([]);
      setPagination({ page: 1, pageSize: 50, total: 0 });
      if (showToast) showToast('error', msg);
    } finally {
      setIsExecuting(false);
    }
  }, [selectedDsId, sql, pagination.pageSize, showToast]);

  // ── 分页切换 ──
  const handlePageChange = useCallback((page: number) => {
    handleExecute(page);
  }, [handleExecute]);

  // ── 插入字段名 ──
  const handleInsertField = useCallback((fieldName: string) => {
    const parts = fieldName.split('.');
    const name = parts[parts.length - 1];

    const editor = editorRef.current;
    if (!editor) {
      setSql(prev => prev + name);
      return;
    }

    const selection = editor.getSelection();
    if (selection && !selection.isEmpty()) {
      editor.executeEdits('insert-field', [{
        range: selection,
        text: name,
      }]);
    } else {
      const position = editor.getPosition();
      if (position) {
        editor.executeEdits('insert-field', [{
          range: {
            startLineNumber: position.lineNumber,
            startColumn: position.column,
            endLineNumber: position.lineNumber,
            endColumn: position.column,
          },
          text: name,
        }]);
      }
    }
    editor.focus();
  }, []);

  // ── 加载模板 ──
  const handleLoadTemplate = useCallback((template: QueryTemplate) => {
    setSql(template.sql);
    if (template.datasourceId) {
      setSelectedDsId(template.datasourceId);
    }
    if (showToast) showToast('info', `已加载模板: ${template.name}`);
  }, [showToast]);

  // ── 加载历史 SQL ──
  const handleLoadHistory = useCallback((historySql: string, dsId: string) => {
    setSql(historySql);
    if (dsId) {
      setSelectedDsId(dsId);
    }
  }, []);

  // ── 导出 CSV ──
  const handleExportCsv = useCallback(() => {
    if (showToast) showToast('success', 'CSV 文件已导出');
  }, [showToast]);

  // ── 打开保存模板对话框 ──
  const handleOpenSaveDialog = useCallback(() => {
    if (!sql.trim()) {
      if (showToast) showToast('error', 'SQL 不能为空');
      return;
    }
    setSaveName('');
    setSaveDesc('');
    setShowSaveDialog(true);
  }, [sql, showToast]);

  // ── 保存模板 ──
  const handleSaveTemplateConfirm = useCallback(async () => {
    if (!saveName.trim()) {
      if (showToast) showToast('error', '请输入模板名称');
      return;
    }

    setIsSaving(true);
    try {
      const req: SaveTemplateRequest = {
        name: saveName.trim(),
        description: saveDesc.trim(),
        sql,
        datasourceId: selectedDsId || undefined,
      };
      await saveTemplate(req);
      if (showToast) showToast('success', '模板保存成功');
      setShowSaveDialog(false);
      // 刷新模板列表
      const refresh = (window as any).__templateRefresh;
      if (refresh) refresh();
    } catch {
      if (showToast) showToast('error', '保存模板失败');
    } finally {
      setIsSaving(false);
    }
  }, [saveName, saveDesc, sql, selectedDsId, showToast]);

  // ── Monaco Editor 挂载 ──
  const handleEditorMount: OnMount = useCallback((editor, monaco) => {
    editorRef.current = editor;

    // 注册 Ctrl+Enter 快捷键执行查询
    editor.addAction({
      id: 'execute-query',
      label: '执行 SQL 查询',
      keybindings: [monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter],
      run: () => {
        handleExecute();
      },
    });

    editor.focus();
  }, [handleExecute]);

  // ── Monaco Editor 配置 ──
  const editorOptions: editor.IStandaloneEditorConstructionOptions = {
    minimap: { enabled: false },
    fontSize: 13,
    lineNumbers: 'on',
    lineNumbersMinChars: 3,
    folding: true,
    automaticLayout: true,
    scrollBeyondLastLine: false,
    wordWrap: 'on',
    tabSize: 2,
    renderWhitespace: 'selection',
    bracketPairColorization: { enabled: true },
    padding: { top: 8 },
    suggest: {
      showWords: true,
    },
  };

  return (
    <div className={`h-full w-full flex flex-row overflow-hidden ${styles.appBg} ${styles.appText} font-sans relative`}>
      {/* ===== 左侧: Schema 树浏览器 ===== */}
      {showLeftPanel && (
        <div className="w-60 shrink-0 border-r border-slate-700">
          <SchemaTree
            dataSources={dataSources}
            selectedDsId={selectedDsId}
            onSelectDs={setSelectedDsId}
            onInsertField={handleInsertField}
          />
        </div>
      )}

      {/* 左侧折叠按钮 */}
      <button
        onClick={() => setShowLeftPanel(!showLeftPanel)}
        className={`absolute top-1/2 -translate-y-1/2 z-20 w-4 h-12 flex items-center justify-center bg-slate-800 border border-slate-700 rounded-r text-slate-400 hover:text-slate-200 cursor-pointer transition-colors ${
          showLeftPanel ? 'left-[240px]' : 'left-0'
        }`}
        title={showLeftPanel ? '收起面板' : '展开面板'}
      >
        <Icon name={showLeftPanel ? 'ChevronLeft' : 'ChevronRight'} size={10} />
      </button>

      {/* ===== 中间: 编辑器 + 结果 ===== */}
      <div className="flex-1 flex flex-col overflow-hidden min-w-0">
        {/* 顶部工具栏 */}
        <QueryToolbar
          dataSources={dataSources}
          selectedDsId={selectedDsId}
          onSelectDs={setSelectedDsId}
          onExecute={() => handleExecute()}
          onSaveTemplate={handleOpenSaveDialog}
          onToggleHistory={() => setIsHistoryOpen(!isHistoryOpen)}
          isExecuting={isExecuting}
          isHistoryOpen={isHistoryOpen}
        />

        {/* 中间区域: 编辑器 + 结果 */}
        <div className="flex-1 flex flex-col overflow-hidden">
          {/* SQL 编辑器 */}
          <div className="h-2/5 min-h-[120px] border-b border-slate-700">
            <Editor
              height="100%"
              defaultLanguage="sql"
              value={sql}
              onChange={(val) => setSql(val || '')}
              onMount={handleEditorMount}
              options={editorOptions}
              theme="vs-dark"
              loading={
                <div className="flex items-center justify-center h-full bg-slate-900">
                  <Icon name="Loader2" size={20} className="animate-spin text-slate-500" />
                </div>
              }
            />
          </div>

          {/* 查询结果 + 历史面板 */}
          <div className="flex-1 flex overflow-hidden">
            <div className="flex-1 overflow-hidden">
              <ResultTable
                columns={columns}
                rows={rows}
                pagination={pagination}
                executionTimeMs={executionTimeMs}
                errorMessage={errorMessage}
                isLoading={isExecuting}
                onPageChange={handlePageChange}
                onExportCsv={handleExportCsv}
              />
            </div>

            {/* 查询历史面板 */}
            {isHistoryOpen && (
              <div className="w-72 shrink-0">
                <HistoryPanel
                  isOpen={isHistoryOpen}
                  onClose={() => setIsHistoryOpen(false)}
                  onLoadHistory={handleLoadHistory}
                  showToast={showToast}
                />
              </div>
            )}
          </div>
        </div>
      </div>

      {/* 右侧折叠按钮 */}
      <button
        onClick={() => setShowRightPanel(!showRightPanel)}
        className={`absolute top-1/2 -translate-y-1/2 z-20 w-4 h-12 flex items-center justify-center bg-slate-800 border border-slate-700 rounded-l text-slate-400 hover:text-slate-200 cursor-pointer transition-colors ${
          showRightPanel ? 'right-[256px]' : 'right-0'
        }`}
        title={showRightPanel ? '收起面板' : '展开面板'}
      >
        <Icon name={showRightPanel ? 'ChevronRight' : 'ChevronLeft'} size={10} />
      </button>

      {/* ===== 右侧: 模板面板 ===== */}
      {showRightPanel && (
        <div className="w-64 shrink-0">
          <TemplatePanel
            currentSql={sql}
            selectedDsId={selectedDsId}
            onLoadTemplate={handleLoadTemplate}
            onOpenSaveDialog={handleOpenSaveDialog}
            showToast={showToast}
          />
        </div>
      )}

      {/* ===== 保存模板对话框 (fixed overlay) ===== */}
      {showSaveDialog && (
        <div className="absolute inset-0 z-50 flex items-center justify-center bg-black/60">
          <div className={`w-96 rounded-lg ${styles.cardBg} border ${styles.cardBorder} shadow-2xl p-5`}>
            <h3 className="text-sm font-bold text-slate-200 mb-4 flex items-center gap-2">
              <Icon name="Save" size={15} className="text-amber-400" />
              保存查询模板
            </h3>
            <div className="space-y-3">
              <div>
                <label className="text-[11px] text-slate-400 mb-1 block">模板名称 *</label>
                <input
                  type="text"
                  value={saveName}
                  onChange={e => setSaveName(e.target.value)}
                  placeholder="输入模板名称..."
                  className={`w-full px-2 py-1.5 text-xs rounded ${styles.inputBg} ${styles.inputText} border ${styles.inputBorder} outline-none focus:border-blue-500/50 transition-colors`}
                  autoFocus
                  onKeyDown={e => e.key === 'Enter' && handleSaveTemplateConfirm()}
                />
              </div>
              <div>
                <label className="text-[11px] text-slate-400 mb-1 block">描述（可选）</label>
                <input
                  type="text"
                  value={saveDesc}
                  onChange={e => setSaveDesc(e.target.value)}
                  placeholder="可选描述..."
                  className={`w-full px-2 py-1.5 text-xs rounded ${styles.inputBg} ${styles.inputText} border ${styles.inputBorder} outline-none focus:border-blue-500/50 transition-colors`}
                  onKeyDown={e => e.key === 'Enter' && handleSaveTemplateConfirm()}
                />
              </div>
              <div className={`px-2 py-2 rounded text-[10px] ${styles.inputBg} border ${styles.inputBorder} max-h-24 overflow-auto`}>
                <span className="text-slate-500">SQL 预览：</span>
                <pre className="text-slate-300 mt-0.5 whitespace-pre-wrap break-all font-mono">{sql || '(空)'}</pre>
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-5">
              <button
                onClick={() => setShowSaveDialog(false)}
                className="px-4 py-1.5 text-xs rounded bg-slate-700 text-slate-300 hover:bg-slate-600 transition-colors cursor-pointer"
              >
                取消
              </button>
              <button
                onClick={handleSaveTemplateConfirm}
                disabled={isSaving || !saveName.trim()}
                className={`flex items-center gap-1.5 px-4 py-1.5 text-xs rounded transition-colors cursor-pointer ${
                  isSaving || !saveName.trim()
                    ? 'bg-slate-600 text-slate-500 cursor-not-allowed'
                    : 'bg-blue-600 hover:bg-blue-500 text-white'
                }`}
              >
                {isSaving && <Icon name="Loader2" size={11} className="animate-spin" />}
                保存
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
