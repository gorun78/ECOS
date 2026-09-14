/**
 * TestTab — 函数沙盒测试运行 Tab（真实执行版）
 *
 * T9 去 mock：移除硬编码 pilot/P01 假对象选项与假编译器定时器日志，
 * 改为直接对接后端 FunctionController 测试端点，以「SQL 表达式 + 目标实体名」
 * 驱动真实沙盒执行，并展示真实 FunctionResult（耗时/编译后 SQL/命中缓存/值）。
 *
 * 后端契约（T16-3 强类型）：
 *   POST /api/v1/ontology/functions/test
 *     入参 OntologyFunctionSaveDTO { expression, entityName, callerId }
 *     返回 FunctionResult { value, sqlType, executionTimeMs, compiledSql, fromCache }
 *
 * @license Apache-2.0
 */
import React, { useState } from 'react';
import { Play, Terminal, CheckCircle } from 'lucide-react';
import type { FunctionType, ObjectType } from '../../../types/ontology';
import type { FunctionTestResultVO } from '../../../services/ontologyApi';
import { testFunction } from '../../../services/ontologyApi';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { showToastGlobal } from '../../../components/common/Toast';

interface TestTabProps {
  func: FunctionType;
  objectTypes: ObjectType[];
  // ── 以下字段由父组件 FunctionTypeDetail 传入 ──
  // T9 起 TestTab 自管真实执行态（不再消费父级 mock 的 handleRunTest/testLogs/testResult），
  // 保留接口声明仅为不改动父组件而维持 JSX props 兼容。
  testInputs: Record<string, any>;
  setTestInputs: React.Dispatch<React.SetStateAction<Record<string, any>>>;
  isTesting: boolean;
  handleRunTest: () => void;
  testLogs: string[];
  testResult: any;
}

/** 日志行语义（映射到主题感知语义色，避免硬编码 Tailwind 颜色） */
type LogTone = 'ok' | 'info' | 'warn' | 'plain';

/** 沙盒默认目标实体（真实 PG 数据表，保证默认表达式可直接执行） */
const DEFAULT_ENTITY = 'ecos_ontology_data';

/** 生成默认 SQL 表达式：对目标表做最小可执行的计数统计 */
function buildDefaultExpression(entity: string): string {
  return `COUNT(id) FROM ${entity}`;
}

export default function TestTab({ func }: TestTabProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  const [expression, setExpression] = useState<string>(() => buildDefaultExpression(DEFAULT_ENTITY));
  const [entityName, setEntityName] = useState<string>(func.associatedObjectType || DEFAULT_ENTITY);
  const [isTesting, setIsTesting] = useState<boolean>(false);
  const [logs, setLogs] = useState<Array<{ text: string; tone: LogTone }>>([]);
  const [result, setResult] = useState<FunctionTestResultVO | null>(null);

  // 语义色 → 主题 token（success/info/danger/muted），随当前主题切换
  const toneClass: Record<LogTone, string> = {
    ok: styles.successText,
    info: styles.infoText,
    warn: styles.dangerText,
    plain: styles.muted,
  };

  const addLog = (text: string, tone: LogTone) =>
    setLogs(prev => [...prev, { text, tone }]);

  /** 真实执行：调后端沙盒 test 端点（替代 mock 定时器模拟） */
  const runTest = async () => {
    setIsTesting(true);
    setLogs([]);
    setResult(null);
    addLog(t('ow.funcTest.compileStart'), 'info');
    try {
      const res = await testFunction({ expression, entityName, callerId: func.id });
      if (res.compiledSql) {
        addLog(`${t('ow.funcTest.compiledSql')}: ${res.compiledSql}`, 'info');
      }
      addLog(
        `${t('ow.funcTest.executed')} ${res.executionTimeMs}ms${res.fromCache ? ` · ${t('ow.funcTest.cacheHit')}` : ''}`,
        'ok'
      );
      setResult(res);
      if (res.value === null || res.value === undefined) {
        addLog(t('ow.funcTest.emptyResult'), 'warn');
      } else {
        addLog(t('ow.funcTest.captured'), 'ok');
      }
    } catch (e: any) {
      addLog(`${t('ow.funcTest.failed')}: ${String(e?.message || e)}`, 'warn');
      showToastGlobal('error', t('ow.funcTest.toastFailed'));
    } finally {
      setIsTesting(false);
    }
  };

  const isEmptyResult =
    result !== null && (result.value === null || result.value === undefined);

  return (
    <div className="flex-1 flex overflow-hidden">
      {/* 左：SQL 表达式沙盒入参 */}
      <div className={`w-2/5 border-r ${styles.cardBorder} p-5 flex flex-col justify-between overflow-y-auto select-none`}>
        <div className="space-y-4 text-xs">
          <div>
            <h4 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.section.sandboxInput')}</h4>
            <p className={`text-[10px] ${styles.muted} mt-0.5`}>{t('ow.funcTest.inputDesc')}</p>
          </div>

          <div className={`space-y-1 ${styles.cardBg} p-3 rounded-lg border ${styles.cardBorder}`}>
            <label className={`block text-[10px] font-semibold ${styles.cardTextMuted}`}>{t('ow.funcTest.expressionLabel')}</label>
            <textarea
              value={expression}
              onChange={e => setExpression(e.target.value)}
              rows={4}
              readOnly={isTesting}
              placeholder={t('ow.funcTest.expressionPlaceholder')}
              className={`w-full mt-1 px-2.5 py-1.5 text-[11px] border ${styles.cardBorder} rounded ${styles.inputBg} ${styles.inputText} font-mono focus:outline-hidden resize-none leading-relaxed`}
            />
          </div>

          <div className={`space-y-1 ${styles.cardBg} p-3 rounded-lg border ${styles.cardBorder}`}>
            <label className={`block text-[10px] font-semibold ${styles.cardTextMuted}`}>{t('ow.funcTest.entityLabel')}</label>
            <input
              type="text"
              value={entityName}
              onChange={e => setEntityName(e.target.value)}
              readOnly={isTesting}
              placeholder={t('ow.funcTest.entityPlaceholder')}
              className={`w-full mt-1 px-2.5 py-1 text-[11px] border ${styles.cardBorder} rounded ${styles.inputBg} ${styles.inputText} font-mono focus:outline-hidden`}
            />
          </div>
        </div>

        <button
          onClick={runTest}
          disabled={isTesting || !expression.trim()}
          className={`w-full py-2 rounded-lg flex items-center justify-center gap-2 font-medium transition-all shadow-xs mt-4 text-xs ${styles.accentBg} text-white ${styles.accentHover} disabled:opacity-50`}
        >
          {isTesting ? (
            <>
              <span className="h-3 w-3 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
              <span>{t('ow.btn.testing')}</span>
            </>
          ) : (
            <>
              <Play size={14} className="fill-white" />
              <span>{t('ow.btn.runTest')}</span>
            </>
          )}
        </button>
      </div>

      {/* 右：执行控制台（展示真实返回） */}
      <div className={`flex-1 p-5 flex flex-col text-xs font-mono overflow-y-auto ${styles.cardBg} border-l ${styles.cardBorder}`}>
        <h4 className={`text-[10px] tracking-wider uppercase font-semibold mb-3 border-b ${styles.cardBorder} pb-2 flex justify-between items-center ${styles.muted}`}>
          <span>{t('ow.section.consoleOutput')}</span>
          {result && (
            <span className={`px-1.5 py-0.5 rounded flex items-center gap-1 ${styles.successBg} ${styles.successText}`}>
              <CheckCircle size={11} /> {result.sqlType ?? 'OK'}
            </span>
          )}
        </h4>

        {logs.length === 0 ? (
          <div className={`flex-1 flex flex-col justify-center items-center italic ${styles.muted}`}>
            <Terminal size={24} className="mb-2" />
            <div>{t('ow.empty.consoleIdle1')}</div>
            <div>{t('ow.funcTest.idleHint')}</div>
          </div>
        ) : (
          <div className="flex-1 space-y-1.5 select-text">
            {logs.map((l, i) => (
              <div key={i} className={toneClass[l.tone]}>{l.text}</div>
            ))}
            {result && (
              <div
                className={`mt-4 p-4 rounded border ${styles.cardBorder} ${
                  isEmptyResult ? styles.muted : `${styles.successBg} ${styles.successText}`
                }`}
              >
                <div className={`text-[10px] mb-1 font-sans uppercase tracking-wider font-semibold ${styles.muted}`}>
                  {t('ow.funcTest.returnValue')} · {result.sqlType ?? '-'} · {result.executionTimeMs}ms
                </div>
                <pre className={`text-xs leading-relaxed font-mono ${isEmptyResult ? styles.muted : styles.successText}`}>
                  {isEmptyResult
                    ? t('ow.funcTest.emptyResult')
                    : typeof result.value === 'object'
                      ? JSON.stringify(result.value, null, 4)
                      : String(result.value)}
                </pre>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
