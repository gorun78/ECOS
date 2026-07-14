/**
 * UdfBuilderPanel — UDF 构建面板
 * 左侧 SQL/代码输入 → 右侧 UDF 代码预览 (Monaco)
 * @license Apache-2.0
 */
import React, { useState, useCallback } from 'react';
import Editor from '@monaco-editor/react';
import {
  Code2, Play, Save, RefreshCw, Database,
  ChevronRight, Loader2, CheckCircle, XCircle,
} from 'lucide-react';
import { apiFetch, apiFetchData } from '../../../api';

// ─── Props ────────────────────────────────────────────

interface UdfBuilderPanelProps {
  className?: string;
}

// ─── Types ────────────────────────────────────────────

type UdfLanguage = 'python' | 'sql' | 'java';

// ─── Component ────────────────────────────────────────

const UdfBuilderPanel: React.FC<UdfBuilderPanelProps> = ({ className = '' }) => {
  const [language, setLanguage] = useState<UdfLanguage>('python');
  const [inputCode, setInputCode] = useState('');
  const [outputCode, setOutputCode] = useState('');
  const [udfName, setUdfName] = useState('');
  const [registering, setRegistering] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ success: boolean; output?: string; error?: string } | null>(null);
  const [toast, setToast] = useState<{ type: 'success' | 'error' | 'info'; msg: string } | null>(null);

  const showToast = (type: 'success' | 'error' | 'info', msg: string) => {
    setToast({ type, msg });
    setTimeout(() => setToast(null), 3000);
  };

  // Convert SQL to UDF
  const handleConvert = useCallback(async () => {
    if (!inputCode.trim()) return;
    try {
      const resp = await apiFetchData<{ data: { code: string } }>(
        '/api/v1/engine/data/udf/convert',
        {
          method: 'POST',
          body: JSON.stringify({
            sourceCode: inputCode,
            language,
            targetLanguage: 'python',
          }),
        }
      );
      const generated = (resp as any)?.data?.code || (resp as any)?.code;
      if (generated) {
        setOutputCode(generated);
        showToast('success', 'UDF 代码已生成');
      } else {
        // Fallback: generate a basic UDF skeleton
        const skeleton = generateUdfSkeleton(inputCode, language);
        setOutputCode(skeleton);
        showToast('info', '已生成 UDF 骨架代码');
      }
    } catch {
      // Fallback: generate skeleton locally
      const skeleton = generateUdfSkeleton(inputCode, language);
      setOutputCode(skeleton);
      showToast('info', '已生成 UDF 骨架代码');
    }
  }, [inputCode, language]);

  // Register UDF
  const handleRegister = useCallback(async () => {
    if (!udfName.trim() || !outputCode.trim()) {
      showToast('error', '请输入 UDF 名称和代码');
      return;
    }
    setRegistering(true);
    try {
      await apiFetch('/api/v1/engine/data/udf/register', {
        method: 'POST',
        body: JSON.stringify({
          name: udfName.trim(),
          language,
          sourceCode: outputCode,
          category: 'transform',
          description: `Auto-generated UDF: ${udfName}`,
        }),
      });
      showToast('success', `UDF "${udfName}" 已注册`);
    } catch (e: any) {
      showToast('error', `注册失败: ${e?.message || '未知错误'}`);
    } finally {
      setRegistering(false);
    }
  }, [udfName, outputCode, language, showToast]);

  // Test UDF
  const handleTest = useCallback(async () => {
    if (!udfName.trim()) {
      showToast('error', '请先注册 UDF');
      return;
    }
    setTesting(true);
    setTestResult(null);
    try {
      const resp = await apiFetchData<{ data: { success: boolean; output?: string; error?: string } }>(
        `/api/v1/engine/data/udf/test`,
        {
          method: 'POST',
          body: JSON.stringify({
            udfName: udfName.trim(),
            testData: { rows: [{ column1: 'test_value' }] },
          }),
        }
      );
      const result = (resp as any)?.data || resp;
      setTestResult(result);
      showToast(result?.success ? 'success' : 'error', result?.success ? '测试通过' : `测试失败: ${result?.error || ''}`);
    } catch (e: any) {
      setTestResult({ success: false, error: e?.message || '未知错误' });
      showToast('error', `测试失败: ${e?.message || ''}`);
    } finally {
      setTesting(false);
    }
  }, [udfName, showToast]);

  return (
    <div className={`flex flex-col h-full bg-white ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-2 border-b border-slate-200 bg-slate-50 shrink-0">
        <div className="flex items-center gap-2">
          <Code2 size={15} className="text-purple-600" />
          <span className="text-xs font-bold text-slate-700 uppercase tracking-wider">
            UDF 构建器
          </span>
        </div>
        <div className="flex items-center gap-1">
          {/* Language selector */}
          <select
            value={language}
            onChange={(e) => setLanguage(e.target.value as UdfLanguage)}
            className="px-2 py-1 text-[10px] border border-slate-200 rounded bg-white text-slate-600 focus:border-purple-400 outline-none"
          >
            <option value="python">Python</option>
            <option value="sql">SQL</option>
            <option value="java">Java</option>
          </select>
        </div>
      </div>

      {/* Main content: Left (Input) + Right (Preview) */}
      <div className="flex-1 flex min-h-0">
        {/* Left: Input */}
        <div className="flex-1 flex flex-col border-r border-slate-200 min-w-0">
          <div className="flex items-center justify-between px-3 py-1.5 bg-slate-50 border-b border-slate-100 shrink-0">
            <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider">
              输入 ({language === 'sql' ? 'SQL' : 'Code'})
            </span>
            <button
              onClick={handleConvert}
              disabled={!inputCode.trim()}
              className="flex items-center gap-1 px-2 py-0.5 text-[10px] bg-purple-600 hover:bg-purple-700 text-white rounded transition-colors disabled:opacity-50"
            >
              <RefreshCw size={10} />
              转换
            </button>
          </div>
          <div className="flex-1 min-h-0">
            <Editor
              height="100%"
              language={language === 'sql' ? 'sql' : language === 'java' ? 'java' : 'python'}
              theme="vs-dark"
              value={inputCode}
              onChange={(v) => setInputCode(v || '')}
              options={{
                minimap: { enabled: false },
                fontSize: 12,
                lineNumbers: 'on',
                wordWrap: 'on',
                scrollBeyondLastLine: false,
              }}
            />
          </div>
        </div>

        {/* Right: UDF Preview */}
        <div className="flex-1 flex flex-col min-w-0">
          <div className="flex items-center justify-between px-3 py-1.5 bg-slate-50 border-b border-slate-100 shrink-0">
            <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider">
              UDF 代码预览
            </span>
            <div className="flex items-center gap-1">
              <input
                type="text"
                value={udfName}
                onChange={(e) => setUdfName(e.target.value)}
                placeholder="UDF 名称"
                className="w-32 px-1.5 py-0.5 text-[10px] border border-slate-200 rounded outline-none focus:border-purple-400"
              />
              <button
                onClick={handleRegister}
                disabled={registering || !udfName.trim() || !outputCode.trim()}
                className="flex items-center gap-1 px-2 py-0.5 text-[10px] bg-emerald-600 hover:bg-emerald-700 text-white rounded transition-colors disabled:opacity-50"
              >
                {registering ? <Loader2 size={10} className="animate-spin" /> : <Save size={10} />}
                注册
              </button>
              <button
                onClick={handleTest}
                disabled={testing}
                className="flex items-center gap-1 px-2 py-0.5 text-[10px] bg-blue-600 hover:bg-blue-700 text-white rounded transition-colors disabled:opacity-50"
              >
                {testing ? <Loader2 size={10} className="animate-spin" /> : <Play size={10} />}
                测试
              </button>
            </div>
          </div>
          <div className="flex-1 min-h-0">
            <Editor
              height="100%"
              language="python"
              theme="vs-dark"
              value={outputCode}
              onChange={(v) => setOutputCode(v || '')}
              options={{
                minimap: { enabled: false },
                fontSize: 12,
                lineNumbers: 'on',
                wordWrap: 'on',
                scrollBeyondLastLine: false,
                readOnly: false,
              }}
            />
          </div>
        </div>
      </div>

      {/* Test result */}
      {testResult && (
        <div className={`px-3 py-2 border-t shrink-0 ${
          testResult.success ? 'bg-emerald-50 border-emerald-200' : 'bg-red-50 border-red-200'
        }`}>
          <div className="flex items-center gap-1.5">
            {testResult.success ? (
              <CheckCircle size={13} className="text-emerald-500" />
            ) : (
              <XCircle size={13} className="text-red-500" />
            )}
            <span className={`text-xs font-medium ${testResult.success ? 'text-emerald-700' : 'text-red-700'}`}>
              {testResult.success ? '测试通过' : '测试失败'}
            </span>
          </div>
          {testResult.output && (
            <pre className="text-[10px] text-slate-600 mt-1 font-mono">{testResult.output}</pre>
          )}
          {testResult.error && (
            <pre className="text-[10px] text-red-600 mt-1 font-mono">{testResult.error}</pre>
          )}
        </div>
      )}

      {/* Toast */}
      {toast && (
        <div className="fixed bottom-4 right-4 z-50">
          <div className={`flex items-center gap-2 px-3 py-2 rounded-lg shadow-lg text-xs font-medium ${
            toast.type === 'success' ? 'bg-emerald-600 text-white' :
            toast.type === 'error' ? 'bg-red-600 text-white' : 'bg-blue-600 text-white'
          }`}>
            {toast.msg}
          </div>
        </div>
      )}
    </div>
  );
};

// ─── UDF Skeleton Generator ──────────────────────────

function generateUdfSkeleton(inputCode: string, language: UdfLanguage): string {
  if (language === 'sql') {
    return `"""
Auto-generated Python UDF from SQL query.
Source query:
${inputCode.split('\n').map(l => '# ' + l).join('\n')}

ECOS Pipeline UDF v2.0
"""
import pandas as pd
from typing import Dict, Any

def transform(df: pd.DataFrame, params: Dict[str, Any]) -> pd.DataFrame:
    \"\"\"
    Transform function for ECOS Pipeline.
    
    Args:
        df: Input DataFrame
        params: Configuration parameters
    
    Returns:
        Transformed DataFrame
    \"\"\"
    # TODO: Implement your transformation logic here
    # Example:
    # df['new_column'] = df['existing_column'].apply(lambda x: x.upper())
    
    return df
`;
  } else if (language === 'java') {
    return `import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

/**
 * Auto-generated Java UDF for ECOS Pipeline v2.0
 */
public class CustomTransform {
    
    /**
     * Transform method
     */
    public static Dataset<Row> transform(Dataset<Row> df, SparkSession spark) {
        // TODO: Implement your transformation logic here
        
        return df;
    }
}
`;
  }

  // Python default
  return `"""
Auto-generated Python UDF for ECOS Pipeline v2.0
"""
import pandas as pd
from typing import Dict, Any

def transform(df: pd.DataFrame, params: Dict[str, Any]) -> pd.DataFrame:
    \"\"\"
    Transform function for ECOS Pipeline.
    
    Args:
        df: Input DataFrame
        params: Configuration parameters
    
    Returns:
        Transformed DataFrame
    \"\"\"
    # TODO: Implement your transformation logic here
    # df['new_column'] = df['old_column'].apply(lambda x: ...)
    
    return df
`;
}

export default UdfBuilderPanel;
