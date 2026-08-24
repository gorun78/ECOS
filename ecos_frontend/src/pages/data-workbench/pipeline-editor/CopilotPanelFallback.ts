/**
 * CopilotPanelFallback — 兜底回复生成器
 * 从 CopilotPanel 拆分而来，逻辑不变（纯函数，返回 Markdown 字符串）
 * @license Apache-2.0
 */

export function generateFallbackResponse(prompt: string): string {
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
