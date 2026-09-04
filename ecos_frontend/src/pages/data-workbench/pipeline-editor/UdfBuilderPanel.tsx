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
import { useTheme } from '../../../components/ThemeContext';

// ─── Props ────────────────────────────────────────────

interface UdfBuilderPanelProps {
  className?: string;
}

// ─── Types ────────────────────────────────────────────

type UdfLanguage = 'python' | 'sql' | 'java';

// ─── Component ────────────────────────────────────────

const UdfBuilderPanel: React.FC<UdfBuilderPanelProps> = ({ className = '' }) => {
  const { styles } = useTheme();
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
    <div className={`flex flex-col h-full ${styles.cardBg} ${className}`}>
      {/* Header */}
      <div className={`flex items-center justify-between px-3 py-2 border-b ${styles.cardBorder} ${styles.cardBg} shrink-0`}>
        <div className="flex items-center gap-2">
          <Code2 size={15} className={`${styles.infoText}`} />
          <span className={`text-xs font-bold ${styles.cardTextMuted} uppercase tracking-wider`}>
            UDF 构建器
          </span>
        </div>
        <div className="flex items-center gap-1">
          {/* Language selector */}
          <select
            value={language}
            onChange={(e) => setLanguage(e.target.value as UdfLanguage)}
            className={`px-2 py-1 text-[10px] border ${styles.cardBorder} rounded ${styles.cardBg} ${styles.cardTextMuted} focus:${styles.infoBorder} outline-none`}
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
        <div className={`flex-1 flex flex-col border-r ${styles.cardBorder} min-w-0`}>
          <div className={`flex items-center justify-between px-3 py-1.5 ${styles.cardBg} border-b ${styles.cardBorder} shrink-0`}>
            <span className={`text-[10px] font-semibold ${styles.muted} uppercase tracking-wider`}>
              输入 ({language === 'sql' ? 'SQL' : 'Code'})
            </span>
            <button
              onClick={handleConvert}
              disabled={!inputCode.trim()}
              className={`flex items-center gap-1 px-2 py-0.5 text-[10px] ${styles.accentBg} hover:${styles.accentBg} ${styles.cardText} rounded transition-colors disabled:opacity-50`}
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
          <div className={`flex items-center justify-between px-3 py-1.5 ${styles.cardBg} border-b ${styles.cardBorder} shrink-0`}>
            <span className={`text-[10px] font-semibold ${styles.muted} uppercase tracking-wider`}>
              UDF 代码预览
            </span>
            <div className="flex items-center gap-1">
              <input
                type="text"
                value={udfName}
                onChange={(e) => setUdfName(e.target.value)}
                placeholder="UDF 名称"
                className={`w-32 px-1.5 py-0.5 text-[10px] border ${styles.cardBorder} rounded outline-none focus:${styles.infoBorder}`}
              />
              <button
                onClick={handleRegister}
                disabled={registering || !udfName.trim() || !outputCode.trim()}
                className={`flex items-center gap-1 px-2 py-0.5 text-[10px] ${styles.successBg} hover:${styles.successBg} ${styles.cardText} rounded transition-colors disabled:opacity-50`}
              >
                {registering ? <Loader2 size={10} className="animate-spin" /> : <Save size={10} />}
                注册
              </button>
              <button
                onClick={handleTest}
                disabled={testing}
                className={`flex items-center gap-1 px-2 py-0.5 text-[10px] ${styles.accentBg} hover:${styles.accentBg} ${styles.cardText} rounded transition-colors disabled:opacity-50`}
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
          testResult.success ? `${styles.successBg} ${styles.successBorder}` : `${styles.dangerBg} ${styles.dangerBorder}`
        }`}>
          <div className="flex items-center gap-1.5">
            {testResult.success ? (
              <CheckCircle size={13} className={`${styles.successText}`} />
            ) : (
              <XCircle size={13} className={`${styles.dangerText}`} />
            )}
            <span className={`text-xs font-medium ${testResult.success ? styles.successText : styles.dangerText}`}>
              {testResult.success ? '测试通过' : '测试失败'}
            </span>
          </div>
          {testResult.output && (
            <pre className={`text-[10px] ${styles.cardTextMuted} mt-1 font-mono`}>{testResult.output}</pre>
          )}
          {testResult.error && (
            <pre className={`text-[10px] ${styles.dangerText} mt-1 font-mono`}>{testResult.error}</pre>
          )}
        </div>
      )}

      {/* Toast */}
      {toast && (
        <div className="fixed bottom-4 right-4 z-50">
          <div className={`flex items-center gap-2 px-3 py-2 rounded-lg shadow-lg text-xs font-medium ${
            toast.type === 'success' ? `${styles.successBg} ${styles.cardText}` :
            toast.type === 'error' ? `${styles.dangerBg} ${styles.cardText}` : `${styles.accentBg} ${styles.cardText}`
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
};

export default UdfBuilderPanel;
