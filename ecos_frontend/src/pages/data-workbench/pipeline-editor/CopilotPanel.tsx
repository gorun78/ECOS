/**
 * CopilotPanel — AI 助手聊天面板
 * 聊天式对话框，支持 Markdown/代码块渲染
 * @license Apache-2.0
 */
import React, { useState, useRef, useEffect, useCallback } from 'react';
import {
  Send, Bot, User, Loader2, Code2, Copy, Check,
  Sparkles, X, Trash2, Maximize2,
} from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import { apiFetch } from '../../../api';

// ─── Types ────────────────────────────────────────────

interface ChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: Date;
}

interface CopilotPanelProps {
  className?: string;
  onClose?: () => void;
}

// ─── Quick actions ────────────────────────────────────

const QUICK_ACTIONS = [
  { label: '帮我写一个过滤活跃用户的 Pipeline', prompt: '帮我写一个过滤活跃用户的 Pipeline，输出 YAML DSL 定义' },
  { label: '这个表里有哪些异常值？', prompt: '这个表里有哪些异常值？请分析并列出可能的异常检测方法' },
  { label: '推荐数据清洗步骤', prompt: '为我的数据集推荐一套完整的数据清洗 Pipeline 步骤' },
  { label: 'UDF: 计算 RFM 模型', prompt: '帮我写一个 Python UDF 函数，用于计算用户 RFM 模型' },
];

// ─── Component ────────────────────────────────────────

const CopilotPanel: React.FC<CopilotPanelProps> = ({ className = '', onClose }) => {
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: 'welcome',
      role: 'assistant',
      content: '👋 你好！我是 **ECOS Copilot**，你的数据工程 AI 助手。\n\n我可以帮你：\n- 📝 编写 Pipeline DSL / YAML 定义\n- 🔍 分析数据异常值\n- 🧹 推荐数据清洗步骤\n- 🐍 生成 Python UDF 代码\n- ❓ 解答 PB 函数用法\n\n请随意提问！',
      timestamp: new Date(),
    },
  ]);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(false);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  // Auto-scroll to bottom
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Auto-focus input
  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  // Send message
  const handleSend = useCallback(async (text?: string) => {
    const msgText = (text || inputValue).trim();
    if (!msgText || loading) return;

    const userMsg: ChatMessage = {
      id: `user-${Date.now()}`,
      role: 'user',
      content: msgText,
      timestamp: new Date(),
    };
    setMessages((prev) => [...prev, userMsg]);
    setInputValue('');
    setLoading(true);

    try {
      const resp = await apiFetch<{ data: { reply: string } }>(
        '/api/v1/engine/data/copilot/chat',
        {
          method: 'POST',
          body: JSON.stringify({
            message: msgText,
            history: messages.slice(-5).map((m) => ({
              role: m.role,
              content: m.content,
            })),
          }),
        }
      );
      const reply = (resp as any)?.data?.reply || (resp as any)?.reply;
      if (reply) {
        setMessages((prev) => [
          ...prev,
          {
            id: `assistant-${Date.now()}`,
            role: 'assistant',
            content: reply,
            timestamp: new Date(),
          },
        ]);
      } else {
        // Fallback responses based on prompt patterns
        const fallback = generateFallbackResponse(msgText);
        setMessages((prev) => [
          ...prev,
          {
            id: `assistant-${Date.now()}`,
            role: 'assistant',
            content: fallback,
            timestamp: new Date(),
          },
        ]);
      }
    } catch {
      const fallback = generateFallbackResponse(msgText);
      setMessages((prev) => [
        ...prev,
        {
          id: `assistant-${Date.now()}`,
          role: 'assistant',
          content: fallback,
          timestamp: new Date(),
        },
      ]);
    } finally {
      setLoading(false);
    }
  }, [inputValue, loading, messages]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleCopy = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const handleClear = () => {
    setMessages([
      {
        id: 'welcome',
        role: 'assistant',
        content: '对话已清空。有什么我可以帮你的？',
        timestamp: new Date(),
      },
    ]);
  };

  // Render code block with copy button
  const renderCodeBlock = (code: string, language: string = '') => {
    return (
      <div className="relative group my-2">
        <div className="flex items-center justify-between px-3 py-1 bg-slate-700 rounded-t-lg text-[10px] text-slate-400">
          <span>{language || 'code'}</span>
          <button
            onClick={() => handleCopy(code, `code-${code.slice(0, 20)}`)}
            className="flex items-center gap-1 text-slate-500 hover:text-slate-200 transition-colors"
          >
            {copiedId === `code-${code.slice(0, 20)}` ? (
              <Check size={10} className="text-green-400" />
            ) : (
              <Copy size={10} />
            )}
          </button>
        </div>
        <pre className="bg-slate-800 text-slate-200 p-3 rounded-b-lg overflow-x-auto text-xs font-mono leading-relaxed">
          <code>{code}</code>
        </pre>
      </div>
    );
  };

  return (
    <div className={`flex flex-col h-full bg-white ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-2.5 border-b border-slate-200 bg-slate-50 shrink-0">
        <div className="flex items-center gap-2">
          <Sparkles size={15} className="text-purple-600" />
          <span className="text-xs font-bold text-slate-700 uppercase tracking-wider">
            Copilot
          </span>
          <span className="text-[9px] px-1 py-0.5 rounded bg-purple-100 text-purple-700 font-medium">
            AI
          </span>
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={handleClear}
            className="p-1 rounded hover:bg-slate-200 text-slate-400 hover:text-slate-600 transition-colors"
            title="清空对话"
          >
            <Trash2 size={13} />
          </button>
          {onClose && (
            <button
              onClick={onClose}
              className="p-1 rounded hover:bg-slate-200 text-slate-400 transition-colors"
            >
              <X size={13} />
            </button>
          )}
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto p-3 space-y-3">
        {messages.map((msg) => (
          <div
            key={msg.id}
            className={`flex gap-2 ${msg.role === 'user' ? 'flex-row-reverse' : ''}`}
          >
            {/* Avatar */}
            <div
              className={`w-7 h-7 rounded-full flex items-center justify-center shrink-0 ${
                msg.role === 'user'
                  ? 'bg-blue-600 text-white'
                  : 'bg-purple-600 text-white'
              }`}
            >
              {msg.role === 'user' ? <User size={14} /> : <Bot size={14} />}
            </div>

            {/* Bubble */}
            <div
              className={`max-w-[85%] rounded-xl px-3 py-2 ${
                msg.role === 'user'
                  ? 'bg-blue-600 text-white'
                  : 'bg-slate-100 text-slate-800'
              }`}
            >
              {msg.role === 'assistant' ? (
                <div className="text-xs prose prose-sm max-w-none dark:prose-invert">
                  <ReactMarkdown
                    components={{
                      code({ children, className: codeClass, ...rest }) {
                        const match = /language-(\w+)/.exec(codeClass || '');
                        const codeStr = String(children).replace(/\n$/, '');
                        if (match) {
                          return renderCodeBlock(codeStr, match[1]);
                        }
                        return (
                          <code className="bg-slate-200 text-slate-800 px-1 py-0.5 rounded text-[10px] font-mono" {...rest}>
                            {children}
                          </code>
                        );
                      },
                      pre({ children }) {
                        return <>{children}</>;
                      },
                    }}
                  >
                    {msg.content}
                  </ReactMarkdown>
                </div>
              ) : (
                <div className="text-xs whitespace-pre-wrap">{msg.content}</div>
              )}
            </div>
          </div>
        ))}

        {/* Loading indicator */}
        {loading && (
          <div className="flex gap-2">
            <div className="w-7 h-7 rounded-full bg-purple-600 text-white flex items-center justify-center shrink-0">
              <Bot size={14} />
            </div>
            <div className="bg-slate-100 rounded-xl px-4 py-2.5">
              <div className="flex gap-1">
                <span className="w-1.5 h-1.5 bg-slate-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                <span className="w-1.5 h-1.5 bg-slate-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                <span className="w-1.5 h-1.5 bg-slate-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
              </div>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Quick actions */}
      <div className="px-3 py-2 border-t border-slate-100 shrink-0 overflow-x-auto">
        <div className="flex gap-1.5">
          {QUICK_ACTIONS.map((action, i) => (
            <button
              key={i}
              onClick={() => handleSend(action.prompt)}
              className="text-[10px] px-2 py-1 rounded-full border border-slate-200 text-slate-500 hover:bg-purple-50 hover:border-purple-200 hover:text-purple-600 whitespace-nowrap transition-colors"
            >
              {action.label}
            </button>
          ))}
        </div>
      </div>

      {/* Input */}
      <div className="px-3 py-2 border-t border-slate-200 bg-slate-50 shrink-0">
        <div className="flex gap-2">
          <textarea
            ref={inputRef}
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="输入问题，如: 帮我写一个过滤活跃用户的 Pipeline..."
            rows={2}
            className="flex-1 px-3 py-1.5 text-xs border border-slate-200 rounded-lg resize-none focus:border-purple-400 focus:ring-1 focus:ring-purple-200 outline-none transition-colors"
            disabled={loading}
          />
          <button
            onClick={() => handleSend()}
            disabled={!inputValue.trim() || loading}
            className="shrink-0 flex items-center justify-center w-9 h-9 bg-purple-600 hover:bg-purple-700 text-white rounded-lg transition-colors disabled:opacity-50 self-end"
          >
            {loading ? (
              <Loader2 size={15} className="animate-spin" />
            ) : (
              <Send size={15} />
            )}
          </button>
        </div>
      </div>
    </div>
  );
};

// ─── Fallback response generator ─────────────────────

function generateFallbackResponse(prompt: string): string {
  const lower = prompt.toLowerCase();

  if (lower.includes('pipeline') || lower.includes('yaml') || lower.includes('dsl')) {
    return `以下是 Pipeline YAML DSL 示例：

\`\`\`yaml
apiVersion: ecos/v2
kind: Pipeline
metadata:
  id: "pl-auto-generated"
  name: "自动生成的 Pipeline"
  version: 1
spec:
  execution:
    engine: memory
    timeout: 600
    retryMax: 3
    batchSize: 5000
  nodes:
    - id: source_data
      type: source
      config:
        datasourceId: ds-default
        table: your_table
        columns: ["col1", "col2", "col3"]
    - id: filter_active
      type: transform
      config:
        expression: "filter(status == 'active')"
      dependsOn: [source_data]
    - id: sink_result
      type: sink
      config:
        datasourceId: ds-default
        table: result_table
        mode: overwrite
      dependsOn: [filter_active]
  edges:
    - from: source_data
      to: filter_active
    - from: filter_active
      to: sink_result
\`\`\`

这是一个基础的三步 Pipeline：
1. **Source**: 从数据源读取表数据
2. **Transform**: 使用 \`filter()\` 表达式过滤
3. **Sink**: 将结果写入目标表

你可以根据需要在画布上拖拽更多节点来扩展此 Pipeline。`;
  }

  if (lower.includes('异常值') || lower.includes('outlier') || lower.includes('anomal')) {
    return `## 异常值检测方法

针对你的数据集，建议采用以下方法：

### 1. **统计方法**
- **Z-Score**: 计算每个数值列的 Z-Score，|Z| > 3 为异常
- **IQR (四分位距)**: Q1 - 1.5×IQR < 正常值 < Q3 + 1.5×IQR

\`\`\`python
# Python UDF 示例
def detect_outliers(df: pd.DataFrame, col: str, method='zscore'):
    if method == 'zscore':
        mean, std = df[col].mean(), df[col].std()
        return df[(df[col] - mean).abs() > 3 * std]
    elif method == 'iqr':
        q1, q3 = df[col].quantile(0.25), df[col].quantile(0.75)
        iqr = q3 - q1
        return df[(df[col] < q1 - 1.5*iqr) | (df[col] > q3 + 1.5*iqr)]
\`\`\`

### 2. **可用的 PB 表达式**
- \`filter(abs(col - mean(col)) > 3 * stddev(col))\`
- 配合窗口函数 \`percent_rank()\` 找出极值

### 3. **建议 Pipeline 步骤**
1. Source → 读取数据
2. Transform → 用 PB 函数计算统计量
3. Filter → 标记/过滤异常行
4. Sink → 输出异常记录到单独表

需要我帮你生成具体的 Pipeline DSL 吗？`;
  }

  if (lower.includes('清洗') || lower.includes('clean')) {
    return `## 数据清洗 Pipeline 建议

\`\`\`yaml
# 标准数据清洗流程
nodes:
  - id: raw_source
    type: source
    config:
      table: raw_data
  - id: trim_whitespace
    type: transform
    config:
      expression: "trim(name), trim(email)"
    dependsOn: [raw_source]
  - id: handle_nulls
    type: transform
    config:
      expression: "coalesce(age, 0), coalesce(status, 'unknown')"
    dependsOn: [trim_whitespace]
  - id: cast_types
    type: transform
    config:
      expression: "to_int(age), to_date(created, 'yyyy-MM-dd')"
    dependsOn: [handle_nulls]
  - id: deduplicate
    type: aggregate
    config:
      groupBy: ["id"]
      aggregations:
        - function: MAX
          sourceField: "*"
    dependsOn: [cast_types]
\`\`\`

**推荐函数**: \`trim()\`, \`coalesce()\`, \`upper()\`, \`to_date()\`, \`to_int()\``;
  }

  if (lower.includes('udf') || lower.includes('rfm')) {
    return `## RFM 模型 Python UDF

\`\`\`python
"""
RFM 分析 UDF — ECOS Pipeline v2.0
"""
import pandas as pd
from datetime import datetime

def transform(df: pd.DataFrame, params: dict) -> pd.DataFrame:
    ref_date = datetime.now()
    
    # 计算 Recency (距上次购买天数)
    df['recency'] = (ref_date - pd.to_datetime(df['last_purchase'])).dt.days
    
    # 计算 Frequency (购买次数)
    freq = df.groupby('customer_id').size().reset_index(name='frequency')
    
    # 计算 Monetary (总消费金额)
    monetary = df.groupby('customer_id')['amount'].sum().reset_index(name='monetary')
    
    # 合并
    result = df[['customer_id']].drop_duplicates()
    result = result.merge(freq, on='customer_id')
    result = result.merge(monetary, on='customer_id')
    result['recency'] = df.groupby('customer_id')['recency'].min().values
    
    # RFM 评分 (1-5)
    for col in ['recency', 'frequency', 'monetary']:
        result[f'{col[:1]}_score'] = pd.qcut(
            result[col], q=5, labels=[1,2,3,4,5]
        ).astype(int)
    
    return result
\`\`\`

将此代码粘贴到 UDF 构建器中，点击"注册"即可在 Pipeline 中使用。`;
  }

  return `关于你的问题，我可以从以下方面帮你：

- 📝 **Pipeline DSL**: 我可以自动生成 YAML 定义
- 🔧 **PB 函数**: 系统支持 120+ 内置函数（字符串/数值/日期/条件/数组/窗口/类型转换等）
- 🐍 **Python UDF**: 可编写自定义 Python 转换函数
- 📊 **数据分析**: 异常检测、聚合分析等

请提供更具体的需求，比如：
- "帮我写一个按日期分组的聚合 Pipeline"
- "如何用 PB 函数计算同比增长？"
- "为我的订单表生成一个数据清洗 Pipeline"`;
}

export default CopilotPanel;
