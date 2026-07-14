/**

 * @license

 * SPDX-License-Identifier: Apache-2.0

 */



import React, { useState, useEffect } from 'react';

import { useLanguage } from '../components/LanguageContext';
import { useTheme } from '../components/ThemeContext';

import { DataConnection, DataSyncTask, DataPipeline, DataHealthCheck, Dataset, ObjectType } from './data-workbench/types';

import LucideIcon from './data-workbench/LucideIcon';

import PipelineBuilderPrototype from './data-workbench/PipelineBuilderPrototype';

import PipelineFlowEditor from './data-workbench/PipelineFlowEditor';

import LineageMapView from './data-workbench/LineageMapView';

import CodeRepositoriesPrototype from './data-workbench/CodeRepositoriesPrototype';

import CodeWorkbooksPrototype from './data-workbench/CodeWorkbooksPrototype';

import ContourPrototype from './data-workbench/ContourPrototype';

import ConnectionsTab from './data-workbench/tabs/ConnectionsTab';
import SyncsTab from './data-workbench/tabs/SyncsTab';
import PipelinesTab from './data-workbench/tabs/PipelinesTab';
import HealthTab from './data-workbench/tabs/HealthTab';
import DataLineageTab from './data-workbench/tabs/DataLineageTab';
import PipelineBuilderTab from './data-workbench/tabs/PipelineBuilderTab';
import CodeReposTab from './data-workbench/tabs/CodeReposTab';
import CodeWorkbooksTab from './data-workbench/tabs/CodeWorkbooksTab';
import ContourTab from './data-workbench/tabs/ContourTab';



import InteractiveStepGuide from './data-workbench/InteractiveStepGuide';

import SqlQueryConsole from './SqlQueryConsole';
import DataEngineConfigPanel from './data-workbench/DataEngineConfigPanel';


interface DataWorkbenchLayoutProps {

  objectTypes?: ObjectType[];

  datasets?: Dataset[];

  onAddDataset?: (dataset: Dataset) => void;

  showToast?: (type: 'success' | 'info' | 'error', message: string) => void;

  activeTab?: 'connections' | 'syncs' | 'pipelines' | 'health' | 'lineage' | 'pipeline-builder' | 'code-repositories' | 'code-workbooks' | 'contour' | 'guide' | 'sql-query' | 'engine-config';

  onActiveTabChange?: (tab: 'connections' | 'syncs' | 'pipelines' | 'health' | 'lineage' | 'pipeline-builder' | 'code-repositories' | 'code-workbooks' | 'contour' | 'guide' | 'sql-query' | 'engine-config') => void;

}



interface PBFunctionDef {

  name: string;

  category: string;

  signature: string;

  description: string;

  example: string;

  defaultArgs: string[];

}



const pbFunctionsMap: PBFunctionDef[] = [

  // Numeric

  { name: 'abs', category: 'numeric', signature: 'abs(column)', description: '计算数值列中所有数值的绝对值。', example: 'abs(fuel_diff) -> 15.5', defaultArgs: [] },

  { name: 'ceil', category: 'numeric', signature: 'ceil(column)', description: '对数值列向上取整。', example: 'ceil(4.1) -> 5', defaultArgs: [] },

  { name: 'floor', category: 'numeric', signature: 'floor(column)', description: '对数值列向下取整。', example: 'floor(4.9) -> 4', defaultArgs: [] },

  { name: 'round', category: 'numeric', signature: 'round(column, scale)', description: '将数值列四舍五入到指定的精度（小数位数）。', example: 'round(cost, 2) -> 12500.45', defaultArgs: ['2'] },

  { name: 'power', category: 'numeric', signature: 'power(column, exponent)', description: '对数值列计算幂次。', example: 'power(base, 2) -> 16.0', defaultArgs: ['2'] },

  { name: 'sqrt', category: 'numeric', signature: 'sqrt(column)', description: '计算数值列中所有值的平方根。', example: 'sqrt(area) -> 4.0', defaultArgs: [] },

  { name: 'mod', category: 'numeric', signature: 'mod(column, divisor)', description: '计算数值除以指定除数的余数。', example: 'mod(flight_index, 2) -> 1', defaultArgs: ['2'] },



  // String

  { name: 'concat', category: 'string', signature: 'concat(col1, col2, ...)', description: '将多个字符串字段或常量按顺序连接为一个字段。', example: "concat(dep_airport, '-', arr_airport) -> 'PEK-HKG'", defaultArgs: ["'-'", 'arr_airport'] },

  { name: 'substring', category: 'string', signature: 'substring(column, position, length)', description: '从指定起始位置截取固定长度的子字符串 (1-indexed)。', example: "substring(flight_num, 1, 2) -> 'CA'", defaultArgs: ['1', '2'] },

  { name: 'lower', category: 'string', signature: 'lower(column)', description: '将所有字符串字符转换为小写字母。', example: "lower('PEK') -> 'pek'", defaultArgs: [] },

  { name: 'upper', category: 'string', signature: 'upper(column)', description: '将所有字符串字符转换为大写字母。', example: "upper('ca1928') -> 'CA1928'", defaultArgs: [] },

  { name: 'trim', category: 'string', signature: 'trim(column)', description: '剔除字符串首尾两端的空白字符。', example: "trim('  CA123  ') -> 'CA123'", defaultArgs: [] },

  { name: 'replace', category: 'string', signature: 'replace(column, search, replacement)', description: '在原文本中检索指定子串，并全部替换为新字符串。', example: "replace(findings, 'AOG', 'Urgent') -> 'Urgent Cabin Check'", defaultArgs: ["'AOG'", "'Urgent'"] },

  { name: 'split', category: 'string', signature: 'split(column, regex)', description: '使用指定的正则表达式分隔字符串并输出字符串数组。', example: "split(crew_list, ',') -> ['John', 'Peter']", defaultArgs: ["','"] },

  { name: 'length', category: 'string', signature: 'length(column)', description: '计算并返回字符串的字符长度。', example: "length('CA1928') -> 6", defaultArgs: [] },

  { name: 'regex_extract', category: 'string', signature: 'regex_extract(column, regex, groupIndex)', description: '使用正则表达式匹配并提取指定捕获组的文本内容。', example: "regex_extract(flight_num, '([A-Z]+)', 1) -> 'CA'", defaultArgs: ["'([A-Z]+)'", '1'] },

  { name: 'regex_replace', category: 'string', signature: 'regex_replace(column, regex, replacement)', description: '使用正则表达式匹配并替换相符的文本子串。', example: "regex_replace(phone, '(\\\\d{3})\\\\d{4}', '$1****') -> '138****'", defaultArgs: ["'(\\\\d{3})\\\\d{4}'", "'$1****'"] },



  // Date/Time

  { name: 'date_add', category: 'date_time', signature: 'date_add(column, days)', description: '为指定日期增加指定天数。', example: "date_add('2026-07-02', 3) -> '2026-07-05'", defaultArgs: ['3'] },

  { name: 'date_sub', category: 'date_time', signature: 'date_sub(column, days)', description: '为指定日期减去指定天数。', example: "date_sub('2026-07-02', 1) -> '2026-07-01'", defaultArgs: ['1'] },

  { name: 'datediff', category: 'date_time', signature: 'datediff(endColumn, startColumn)', description: '计算两个日期之间的天数差异（结束日期 - 开始日期）。', example: "datediff('2026-07-05', '2026-07-02') -> 3", defaultArgs: ['scheduled_departure'] },

  { name: 'year', category: 'date_time', signature: 'year(column)', description: '提取日期时间的年份数字。', example: "year('2026-07-02') -> 2026", defaultArgs: [] },

  { name: 'month', category: 'date_time', signature: 'month(column)', description: '提取日期时间的月份数字 (1 - 12)。', example: "month('2026-07-02') -> 7", defaultArgs: [] },

  { name: 'day', category: 'date_time', signature: 'day(column)', description: '提取日期时间的天数。', example: "day('2026-07-02') -> 2", defaultArgs: [] },

  { name: 'current_date', category: 'date_time', signature: 'current_date()', description: '获取系统当前的物理运行日期。', example: 'current_date() -> 2026-07-02', defaultArgs: [] },

  { name: 'to_date', category: 'date_time', signature: 'to_date(column, format)', description: '按照指定的日期模式模板，解析文本列为标准 Date 列。', example: "to_date('2026/07/02', 'yyyy/MM/dd') -> '2026-07-02'", defaultArgs: ["'yyyy/MM/dd'"] },

  { name: 'date_format', category: 'date_time', signature: 'date_format(column, format)', description: '将日期格式化为特定的字符串展示版式。', example: "date_format(dep_time, 'yyyyMMdd-HH') -> '20260702-22'", defaultArgs: ["'yyyyMMdd-HH'"] },



  // Conditional

  { name: 'coalesce', category: 'conditional', signature: 'coalesce(col1, col2, ...)', description: '按顺序检测各字段，并返回第一个不为 NULL 的值。常用于空缺值默认填充。', example: "coalesce(maint_date, '1970-01-01') -> '1970-01-01' (当原值为空)", defaultArgs: ["'1970-01-01'"] },

  { name: 'case_when', category: 'conditional', signature: 'case_when(condition1, val1, condition2, val2, ...)', description: '多条件分支判断，匹配第一个为 True 的条件并返回值 (IF-THEN-ELSE 语句)。', example: "case_when(dep == 'PEK', '北京首都', '其他') -> '北京首都'", defaultArgs: ["dep_airport = 'PEK'", "'首都机场'", "'外地机场'"] },

  { name: 'if_null', category: 'conditional', signature: 'if_null(column, defaultValue)', description: '简化的空值替代器，如果第一参数为 null 则输出第二参数。', example: "if_null(hours, 0) -> 0", defaultArgs: ['0'] },

  { name: 'nullif', category: 'conditional', signature: 'nullif(col1, col2)', description: '如果两列值严格相等，则返回 NULL，否则返回第一参数。', example: "nullif('N/A', 'N/A') -> NULL", defaultArgs: ["'N/A'"] },



  // Array/Struct

  { name: 'array_contains', category: 'array_struct', signature: 'array_contains(column, value)', description: '检查数组列中是否包含指定元素。', example: "array_contains(crew_tags, 'Pilot') -> True", defaultArgs: ["'Pilot'"] },

  { name: 'array_join', category: 'array_struct', signature: 'array_join(column, delimiter)', description: '将数组列中各元素使用连接符拼接为字符串。', example: "array_join(passengers, ';') -> 'A;B;C'", defaultArgs: ["';'"] },

  { name: 'explode', category: 'array_struct', signature: 'explode(column)', description: '将含有数组的行裂变展开为多行，数组每个元素单独占据一行。', example: 'explode([1, 2]) -> 两行(1 和 2)', defaultArgs: [] },

  { name: 'element_at', category: 'array_struct', signature: 'element_at(column, index)', description: '安全返回数组指定位置的元素值。', example: 'element_at(roles, 1) -> 第一个元素', defaultArgs: ['1'] },

  { name: 'size', category: 'array_struct', signature: 'size(column)', description: '计算并返回数组列中元素的总个数。', example: 'size(passengers) -> 120', defaultArgs: [] },



  // Window Functions

  { name: 'row_number', category: 'window', signature: 'row_number() over (partition_by, order_by)', description: '在窗口分区内，按指定排序列赋予每一行递增唯一的行号。', example: 'row_number() over (pilot_id, dep_time)', defaultArgs: ['pilot_id', 'scheduled_departure'] },

  { name: 'rank', category: 'window', signature: 'rank() over (partition_by, order_by)', description: '窗口排名，允许并列，并列会跳跃后续序号（如 1, 2, 2, 4）。', example: 'rank() over (home_base, hours_flown)', defaultArgs: ['home_base', 'hours_flown'] },

  { name: 'dense_rank', category: 'window', signature: 'dense_rank() over (...)', description: '窗口密集排名，允许并列，后续序号不跳跃（如 1, 2, 2, 3）。', example: 'dense_rank() over (home_base, hours_flown)', defaultArgs: ['home_base', 'hours_flown'] },

  { name: 'window_lead', category: 'window', signature: 'window_lead(column, offset, default) over (...)', description: '读取窗口中当前行后面的第 offset 行字段值。可用于轨迹寻踪或关联后续。', example: 'window_lead(flight_id, 1, NULL) over (...)', defaultArgs: ['flight_id', '1'] },

  { name: 'window_lag', category: 'window', signature: 'window_lag(column, offset, default) over (...)', description: '读取窗口中当前行前面的第 offset 行字段值。可用于比较前序状态。', example: 'window_lag(flight_id, 1, NULL) over (...)', defaultArgs: ['flight_id', '1'] },

  { name: 'running_sum', category: 'window', signature: 'running_sum(column) over (...)', description: '计算窗口分区内的递增累加总和（如月度额递增）。', example: 'running_sum(cost_usd) over (tail_number, maint_date)', defaultArgs: ['cost_usd'] },



  // Casting

  { name: 'cast_to_string', category: 'casting', signature: 'cast(column as string)', description: '将当前类型强制解析并重置为文本字符（String）。', example: 'cast(102.5 as string) -> "102.5"', defaultArgs: [] },

  { name: 'cast_to_integer', category: 'casting', signature: 'cast(column as integer)', description: '将该列强制转换为整型。对不合规格式输出 Null。', example: 'cast("45" as integer) -> 45', defaultArgs: [] },

  { name: 'cast_to_double', category: 'casting', signature: 'cast(column as double)', description: '将该列强制转换为双精度浮点数。', example: 'cast("15.5" as double) -> 15.5', defaultArgs: [] },

  { name: 'cast_to_boolean', category: 'casting', signature: 'cast(column as boolean)', description: '强制类型转换为布尔型 (True / False)。', example: 'cast(1 as boolean) -> True', defaultArgs: [] },

  { name: 'cast_to_timestamp', category: 'casting', signature: 'cast(column as timestamp)', description: '将该列强制转换为高精度标准 ISO 时间戳。', example: 'cast("2026-07-02 22:00:00" as timestamp)', defaultArgs: [] },



  // Hash

  { name: 'md5', category: 'hash', signature: 'md5(column)', description: '计算指定字符列的 MD5 散列值。', example: 'md5("secret") -> 32位哈希摘要', defaultArgs: [] },

  { name: 'sha256', category: 'hash', signature: 'sha256(column)', description: '计算指定字符列的 SHA-256 安全散列值。', example: 'sha256("secret") -> 64位哈希摘要', defaultArgs: [] },

  { name: 'hash', category: 'hash', signature: 'hash(col1, col2, ...)', description: '对一列或多列值进行联合哈希运算，常用于计算行指纹变化。', example: 'hash(flight_num, scheduled_departure)', defaultArgs: ['scheduled_departure'] }

];



const originalSampleRows: Record<string, any> = {

  'node_flights_raw': {

    flight_id: 'FL-4501',

    flight_num: '  ca1928 ',

    dep_airport: 'pek',

    arr_airport: 'hkg',

    scheduled_departure: '2026-07-02 22:00:00',

    actual_departure: '2026-07-02 22:15:00',

    pilot_id: 'PL-9023'

  },

  'node_pilots_raw': {

    pilot_id: 'PL-9023',

    name: '张伟 (Capt. Zhang)',

    hours_flown: 12500,

    home_base: 'pek'

  },

  'node_active_flights': {

    flight_id: 'FL-4501',

    flight_num: '  ca1928 ',

    dep_airport: 'pek',

    arr_airport: 'hkg',

    scheduled_departure: '2026-07-02 22:00:00',

    actual_departure: '2026-07-02 22:15:00',

    pilot_id: 'PL-9023'

  },

  'node_join_flights_pilots': {

    flight_id: 'FL-4501',

    flight_num: 'CA1928',

    dep_airport: 'PEK',

    arr_airport: 'HKG',

    scheduled_departure: '2026-07-02 22:00:00',

    actual_departure: '2026-07-02 22:15:00',

    pilot_id: 'PL-9023',

    pilot_name: null,

    pilot_hours_flown: 12500

  }

};



function simulateTransforms(nodeId: string, transformsList: any[], original: any) {

  if (!original) return {};

  let row = { ...original };

  if (!transformsList) return row;

  

  transformsList.forEach(t => {

    const col = t.column;

    const type = t.type;

    const args = t.args || [];

    const val = row[col];

    

    if (type === 'trim' && typeof val === 'string') {

      row[col] = val.trim();

    } else if (type === 'upper' && typeof val === 'string') {

      row[col] = val.toUpperCase();

    } else if (type === 'lower' && typeof val === 'string') {

      row[col] = val.toLowerCase();

    } else if (type === 'coalesce') {

      if (val === null || val === undefined || val === '') {

        const fallback = args[0] ? args[0].replace(/'/g, "") : 'DEFAULT';

        row[col] = fallback;

      }

    } else if (type === 'regex_extract') {

      if (typeof val === 'string') {

        row[col + '_extracted'] = val.toUpperCase().trim().slice(0, 2);

      }

    } else if (type === 'concat') {

      row[col + '_concat'] = `${val}-${args[0] ? args[0].replace(/'/g, "") : ''}`;

    } else if (type === 'round' && typeof val === 'number') {

      row[col] = Math.round(val);

    } else if (type === 'md5') {

      row[col + '_md5'] = 'e80b5017098950fc58aad83c8c14978e';

    } else if (type === 'cast_to_string') {

      row[col] = String(val);

    } else if (type === 'cast_to_integer') {

      row[col] = parseInt(val) || 0;

    }

  });

  

  return row;

}



function generateDorisOrInMemoryCode(nodeId: string, nodeName: string, nodeType: string, config: any, engine: 'memory' | 'doris'): string {

  if (engine === 'doris') {

    if (nodeType === 'source_dataset') {

      return `-- Apache Doris OLAP Ingestion DDL\nCREATE TABLE IF NOT EXISTS ${config?.sourceTable || 'default_table'} (\n    flight_id VARCHAR(50) NOT NULL,\n    flight_num VARCHAR(20) NULL,\n    dep_airport VARCHAR(10) NULL,\n    arr_airport VARCHAR(10) NULL,\n    scheduled_departure DATETIME NULL,\n    pilot_id VARCHAR(50) NULL\n)\nUNIQUE KEY(flight_id)\nDISTRIBUTED BY HASH(flight_id) BUCKETS 10\nPROPERTIES("replication_allocation" = "tag.location.default: 1");\n`;

    }

    

    if (nodeType === 'transform') {

      return `-- Apache Doris Expression Projection Selection\nSELECT \n    flight_id,\n    UPPER(TRIM(flight_num)) AS flight_num,\n    UPPER(dep_airport) AS dep_airport,\n    COALESCE(pilot_name, '未知飞行员') AS pilot_name\nFROM ${config?.sourceTable || 'parent_node'}\nWHERE dep_airport IS NOT NULL;\n`;

    }

    

    if (nodeType === 'join') {

      return `-- Apache Doris Colocate Join / Runtime Broadcast Join\nSELECT \n    f.flight_id,\n    f.flight_num,\n    f.dep_airport,\n    f.arr_airport,\n    p.name AS pilot_name,\n    p.hours_flown AS pilot_hours_flown\nFROM flights_df f\nJOIN [BROADCAST] pilots_df p ON f.${config?.joinCondition || 'pilot_id'} = p.${config?.joinCondition || 'pilot_id'};\n`;

    }

    

    if (nodeType === 'aggregate') {

      return `-- Apache Doris Aggregate Key Table Model & Projection\nSELECT \n    ${(config?.groupByColumns || []).join(', ') || 'pilot_id'},\n    COUNT(flight_id) AS total_flights,\n    AVG(hours_flown) AS average_hours_flown\nFROM parent_node\nGROUP BY ${(config?.groupByColumns || []).join(', ') || 'pilot_id'};\n`;

    }

    

    if (nodeType === 'target_dataset') {

      return `-- Apache Doris Overwrite Ingestion Table Transaction\nINSERT INTO ${config?.targetDatasetName || 'default_target'}\nSELECT * FROM parent_node;\n`;

    }

    return `-- Custom Doris Transformation\nSELECT * FROM ${nodeName};\n`;

  } else {

    if (nodeType === 'source_dataset') {

      return `// JavaScript In-Memory Data Pipeline Engine\nconst ${nodeId} = await MemoryEngine.loadTable("${config?.sourceTable || 'default_table'}");\nconsole.log(\`Loaded in-memory rows: \${${nodeId}.length}\`);\n`;

    }

    

    if (nodeType === 'transform') {

      return `// JavaScript Fast Row Projection & Format Map\nconst ${nodeId} = parent_node.map(row => ({\n  ...row,\n  flight_num: (row.flight_num || '').trim().toUpperCase(),\n  dep_airport: (row.dep_airport || '').toUpperCase(),\n  pilot_name: row.pilot_name || '未知飞行员'\n}));\n`;

    }

    

    if (nodeType === 'join') {

      return `// JavaScript Hash Join Algorithm (Memory O(1) Lookup)\nconst pilotMap = new Map(pilots_df.map(p => [p.${config?.joinCondition || 'pilot_id'}, p]));\nconst ${nodeId} = flights_df.map(f => {\n  const pilot = pilotMap.get(f.${config?.joinCondition || 'pilot_id'});\n  return {\n    ...f,\n    pilot_name: pilot ? pilot.name : null,\n    pilot_hours_flown: pilot ? pilot.hours_flown : 0\n  };\n});\n`;

    }

    

    if (nodeType === 'aggregate') {

      return `// JavaScript Hash Aggregation (Memory Map Reduce)\nconst groups = {};\nparent_node.forEach(row => {\n  const key = row.${(config?.groupByColumns || [])[0] || 'pilot_id'};\n  if (!groups[key]) groups[key] = { count: 0, sum_hours: 0 };\n  groups[key].count += 1;\n  groups[key].sum_hours += row.hours_flown || 0;\n});\nconst ${nodeId} = Object.entries(groups).map(([key, g]) => ({\n  ${(config?.groupByColumns || [])[0] || 'pilot_id'}: key,\n  total_flights: g.count,\n  average_hours_flown: g.count > 0 ? (g.sum_hours / g.count) : 0\n}));\n`;

    }

    

    if (nodeType === 'target_dataset') {

      return `// JavaScript Commit To Memory Storage / RAM Lake\nawait MemoryEngine.saveTable("${config?.targetDatasetName || 'default_target'}", parent_node);\nconsole.log("Memory transaction successfully committed and cached.");\n`;

    }

    return `// Custom In-Memory transformation\nconst ${nodeId} = [...parent_node];\n`;

  }

}



// --- TOP ARCHITECTURE BLUEPRINT DESIGN MATRIX ---

export interface MatrixPhaseDetail {

  role: 'PRIMARY' | 'SECONDARY' | 'NONE';

  roleLabel: string;

  desc: string;

  code?: string;

  antiPattern?: string;

}



export interface MatrixItem {

  toolId: string;

  toolName: string;

  toolIcon: string;

  phases: Record<number, MatrixPhaseDetail>;

}



const matrixData: MatrixItem[] = [

  {

    toolId: 'pipeline-builder',

    toolName: 'Pipeline Builder (图形算子)',

    toolIcon: 'Workflow',

    phases: {

      1: {

        role: 'SECONDARY',

        roleLabel: '辅助协作',

        desc: '支持在图形画布中直接导入由 Data Connection 托管的物理原始表。支持可视化触发一次性全量同步。',

        antiPattern: '避免在 Pipeline Builder 中手动编写大量底层的原始 JDBC 读取通道，应由 Magritte/Data Ingest 同步任务作为标准源导入。'

      },

      2: {

        role: 'PRIMARY',

        roleLabel: '绝对主导',

        desc: '低代码/免代码数据清洗的首选核心阵地。支持一键拖拽完成 30 余组流批一体标准清洗算子（Trim, Cast, Regex, Coalesce, DateFormat）并执行 Colocate Joins 融合成宽表。',

        code: `// [Pipeline Builder 算子图元物理下推配置]

{

  "type": "expression_projection",

  "nodeId": "node_active_flights",

  "transforms": [

    { "column": "flight_num", "type": "trim_and_upper" },

    { "column": "dep_airport", "type": "uppercase" },

    { "column": "pilot_name", "type": "coalesce", "args": ["'未知飞行员'"] }

  ]

}`,

        antiPattern: '严禁在 Pipeline Builder 节点中堆叠极其复杂的统计分析、多段复杂数学建模（如气象动力预测）。该场景应由 Code Repositories 高代码或 Python 建模节点承接。'

      },

      3: {

        role: 'PRIMARY',

        roleLabel: '深度主导',

        desc: '提供百分之百可视化的全链路数据血缘（Lineage DAG）呈现。每一个图形节点都自带开发分支（Branching）属性，可直接在当前画布拉起独立分支进行冷配置与运行测试，完全不破坏线上生产血缘依赖。',

        code: `# Pipeline Builder 拓扑图元分支自验

- 镜像生产线: [main] -> [dev/add-weather-enrichment]

- 差异运行分析: 评估 [ds_flights_clean] schema 新增字段对下游 SLA 影响

- 校验结论: 静态 DAG 无循环依赖，通过！`,

        antiPattern: '避免在未拉起开发分支的前提下直接在 main 分支修改拓扑。ECOS 强制要求任何修改必须提交 PR 经合并后由系统自动跑 CI。'

      },

      4: {

        role: 'PRIMARY',

        roleLabel: '绝对主导',

        desc: '提供平台原生、全可视化的数据质量健康判定（Data Health Checks）。支持一键在任意数据节点绑定：行数底线、特定字段空值率界限、SLA 物理更新延迟阈值，并自动触发熔断与警报。',

        code: `healthChecks:

  - dataset: "/aviation/silver/ds_flights_clean"

    rules:

      - rule: ROW_COUNT_NOT_EMPTY (触发条件: 行数 < 1000)

      - rule: NULL_CHECK (限制 pilot_id 缺失占比 <= 1.0%)

      - rule: FRESHNESS_CHECK (主表延迟更新时效上限为 120 分钟)`,

        antiPattern: '严禁只配置调度而不挂载任何质量 Data Health 监控。裸奔的管道在遇到物理源 Schema 突变时会导致全网脏数据污染，引起 Ontology 实体灾难。'

      },

      5: {

        role: 'PRIMARY',

        roleLabel: '绝对主导',

        desc: '一键建立物理表与逻辑实体的映射通道。Pipeline Builder 生成的 Gold（黄金级）生产大宽表，可以被 Ontology Manager 选作标准物理支撑表，并直接进行主键绑定和关系建立。',

        antiPattern: '不要将未经 Pipeline Builder 清洗聚合的 Bronze 原始脏表直接投射到 Ontology 实体。Ontology 必须绑定高可用、高净度的数据。'

      }

    }

  },

  {

    toolId: 'code-repositories',

    toolName: 'Code Repositories (代码仓库)',

    toolIcon: 'FileCode',

    phases: {

      1: {

        role: 'SECONDARY',

        roleLabel: '辅助支持',

        desc: '支持通过编写高代码 Doris Ingress 驱动（如复杂的文件加密解析、非结构化 JSON 级联扁平化）来直接抽取特殊的自定义物理数据。',

        antiPattern: '不要针对常规的标准 JDBC PostgreSQL/MySQL 编写复杂的 Java/Scala 同步代码，这会增加长线维护成本，应优先使用 Data Connections 配置。'

      },

      2: {

        role: 'PRIMARY',

        roleLabel: '绝对主导',

        desc: '高代码（Code-First）场景下的终极清洗与关联武器。针对百亿级极速计算体量、涉及大规模级联哈希 Join、高级聚合窗口函数（row_number、window_lead）或需引入机器学习预测模型的数据集。',

        code: `# [Doris & Python 生产级极速转换算子规范]

import pandas as pd

# 平台全自动下推至 Doris 极速计算引擎，或在内存中执行高速矢量计算



def process_flight_maint_lag(flights_df, pilots_df):

    # 1. 物理字段清洗

    clean_f = flights_df[flights_df["flight_id"].notna()]

    clean_f["flight_num"] = clean_f["flight_num"].str.strip().str.upper()

    

    # 2. 内存高速 Hash Join (Doris / Vectorized)

    joined = pd.merge(clean_f, pilots_df, on="pilot_id", how="left")

    

    # 3. 内存聚合与排序计算：计算每个飞行员的前序航班时刻

    joined["prev_flight_time"] = joined.groupby("pilot_id")["scheduled_departure"].shift(1)

    return joined`,

        antiPattern: '严禁将易读、简单的过滤或拼接逻辑用高度复杂的 Repositories 代码层层包裹。保持“简单逻辑低代码化，算法逻辑高代码化”是金牌策划案的最高原则。'

      },

      3: {

        role: 'PRIMARY',

        roleLabel: '绝对主导',

        desc: '代码级 Git 协作的核心闭环。基于内置标准 Git 分支架构（Git Branching as Code），支持团队多人并行编写 Doris/SQL 管道。任何合并动作必须提交 PR 并自动触发系统内置 YARN/Doris 编译器静态校验、运行测试单元。',

        code: `# [Repositories Code-First CI 静态编译与自验规范]

$ git checkout -b dev/flight-weather-enrichment

$ py-compile src/pipeline_logic.py

  >> Loading dependency libraries: doris-connector, numpy, pandas...

  >> Running Unit Test Cases: test_schema_match(), test_delay_metric()...

  >> Checking SLA downstream dependency impact map...

  >> STATUS: 🟢 ALL TEST CASES PASSED. PR APPROVED BY PEER G.XIAO.`,

        antiPattern: '坚决杜绝不跑静态测试、不提交 PR 就将代码直接推送合并到 master 分支的行为。CI 系统具有强制拦截权。'

      },

      4: {

        role: 'SECONDARY',

        roleLabel: '辅助协作',

        desc: '支持在代码级别通过特定的配置文件（如 pipeline.yml 或 schedules.json）声明复杂的联动调度策略或 Job Groups，以便进行细粒度控制。',

        antiPattern: '不要在代码中手写复杂的 cron 计时线程。应当使用平台内置的 Job Scheduler 进行调度托管，以保证统一的运维观测性。'

      },

      5: {

        role: 'PRIMARY',

        roleLabel: '绝对主导',

        desc: '利用強类型 SDK 的终极赋能。Code Repositories 支持直接编写 Functions on Objects（对象函数）。通过绑定 Ontology 实体类型，开发者可以使用 TypeScript/Java 编写安全、类型受控、具备自动补全的业务指标算子，向平台下游应用输送纯净计算服务。',

        code: `// [TypeScript Functions on Objects 标准规范]

import { Function, Objects, Flight } from "@ecos/functions-api";



export class FlightAnalyticsService {

    @Function()

    public getHighRiskFlights(flights: Flight[]): Flight[] {

        // 利用 Ontology 强类型直接进行高级业务筛选过滤

        return flights.filter(f => {

            const delay = f.delayMinutes ?? 0;

            return delay > 60 && f.status === 'ACTIVE';

        });

    }

}`,

        antiPattern: '严禁在应用前端（如 Slate 或 Workshop）编写大量散落的、无版本控制的 JS 脚本来计算业务核心指标。应当统一收拢在 Repositories 实体方法中，确保单一事实来源。'

      }

    }

  },

  {

    toolId: 'code-workbooks',

    toolName: 'Code Workbooks (探索工作簿)',

    toolIcon: 'BookOpen',

    phases: {

      1: {

        role: 'NONE',

        roleLabel: '无直接关联',

        desc: '不具备物理采集、拉取和注册物理源的功能。',

        antiPattern: '不要尝试在 Workbook 中编写临时的 JDBC 连接去手动拉取数据，这不仅会绕过平台的数据安全审计，还会带来严重的账号明文泄露风险。'

      },

      2: {

        role: 'SECONDARY',

        roleLabel: '辅助支持',

        desc: '数据清洗前中期的“沙箱敏捷验证”和“机器学习原型设计”。允许使用 SQL、Python、R 混写。可在沙箱中实时输出中间表、柱状图、箱线图，帮助数据科学家探查质量，验证关联逻辑是否合理。',

        code: `# [Code Workbooks 混写探查与统计分布打印]

import pandas as pd

import numpy as np



def analyze_maint_cycle(flights_with_pilots_df):

    # 即时沙箱环境，可以打印 dataframe 的多维统计分布图并进行异常检测

    pandas_df = flights_with_pilots_df.toPandas()

    desc = pandas_df.describe()

    print("Maint hours distribution:\\n", desc)

    return flights_with_pilots_df`,

        antiPattern: '严禁将 Code Workbooks 作为定时运行、大规模高并发的线上生产级 ETL 管道。Workbook 的底层容器是交互式的，高并发调度会极易触发 JVM 堆内存溢出。'

      },

      3: {

        role: 'SECONDARY',

        roleLabel: '辅助协作',

        desc: '支持通过拖拽式分支（Interactive Tree Graph）查看本 Workbook 内部节点的逻辑依赖和数据流动，主要用于数据科学探查的思路梳理。',

        antiPattern: '不要混淆 Workbook 内部的草稿依赖树与线上正式的生产 DAG 血缘依赖。Workbook 内部修改不会对生产血缘图腾产生任何 SLA 影响。'

      },

      4: {

        role: 'SECONDARY',

        roleLabel: '辅助协同',

        desc: '当线上调度出现数据健康异常（如 pilot_id 缺失占比超过 1%）时，开发人员可通过一键克隆现场物理快照，在 Code Workbooks 中启动沙箱现场排查，利用 Python 分析缺失空值的概率分布。',

        antiPattern: '严禁在 Workbook 交互节点中直接挂载日常高频监控报警，由于 Workbook 底层服务的非长驻性，会导致监控警报极易丢失。'

      },

      5: {

        role: 'SECONDARY',

        roleLabel: '辅助支持',

        desc: '支持将 Workbook 中训练好的算法模型作为 ML Model Object 注册进 Ontology，作为“模型实体”以供下游决策系统调用。'

      }

    }

  },

  {

    toolId: 'contour',

    toolName: 'Contour (分析下钻)',

    toolIcon: 'Layers',

    phases: {

      1: {

        role: 'NONE',

        roleLabel: '无直接关联',

        desc: '纯业务侧分析产品，无法直连物理底层数据库。',

        antiPattern: '业务分析师不需要关心物理数据源。物理源的注册与托管应当完全交给数据工程团队在 Ingest 和 Connections 环节中隔离完成。'

      },

      2: {

        role: 'SECONDARY',

        roleLabel: '辅助协作',

        desc: '业务分析师可以通过点击交互卡片（Cards）对已入湖的 Bronze/Silver 数据集执行亚秒级的多维汇总和过滤（Curation），从而快速获得一份特定业务场景下的临时数据集或数据报告，不产生生产级物理落湖表。',

        code: `[Contour 敏捷分析板卡流 (Analysis Board Path)]

- 加载: /aviation/silver/ds_flights_clean [4,500 行]

- 卡片 1 (Filter): 过滤 departure_airport == 'PEK' 且延迟时间 > 15分钟

- 卡片 2 (Group By): 按 pilot_id 进行聚合，计算平均延迟次数

- 卡片 3 (Join): 级联加载 pilots_raw 宽表，获取飞行员实名

- 输出: 形成分析路径并在 0.5 秒内呈现可视化热力图`,

        antiPattern: '严禁依靠 Contour 进行长时序、大规模、包含深度算法回溯的后台物理 ETL 任务。Contour 是为了业务即时多维下钻设计的 Presto 式临时视图，不能取代物理大宽表的加工。'

      },

      3: {

        role: 'SECONDARY',

        roleLabel: '辅助协同',

        desc: '业务分析师可以极其直观地在 Contour 画布中一键追溯其所引用数据集的上游所有依赖及数据 SLA，实现端到端的业务质检合规。'

      },

      4: {

        role: 'SECONDARY',

        roleLabel: '辅助协助',

        desc: '作为数据质量审计的“第二双眼睛”。业务主管和质检合规官可以通过 Contour 配置多维时效、异常占比看板，并在日常业务巡检中直接监督生产数据的健康情况。',

        antiPattern: '严禁在 Workbook 交互节点中直接挂载日常高频监控报警，由于 Workbook 底层服务的非长驻性，会导致监控警报极易丢失。'

      },

      5: {

        role: 'PRIMARY',

        roleLabel: '绝对主导',

        desc: '业务消费 Ontology 实体的主流分析通道。业务人员无需知道任何物理表名和 SQL 代码，直接在 Contour 中加载已被 Ontology 映射好的逻辑实体（如“航班”与“飞行员”），双击建立多维交叉对比路径，并在 1 秒内完成百亿级实体的决策分析与看板分享。',

        antiPattern: '杜绝在 Contour 中通过手动编写复杂的原始数据 Join 和硬编码来拼装业务实体。应当优先在 Ontology Manager 中完成统一实体逻辑建模，业务人员只需在 Contour 直接拉取标准对象即可。'

      }

    }

  }

];



export default function DataWorkbenchLayout({

  objectTypes: propObjectTypes,

  datasets: propDatasets,

  onAddDataset: propOnAddDataset,

  showToast: propShowToast,

  activeTab: propActiveTab,

  onActiveTabChange

}: DataWorkbenchLayoutProps = {}) {

  const { t } = useLanguage();
  const { styles } = useTheme();

  // --- Local showToast helper (fallback to console.log) ---

  const showToast = propShowToast || ((type: 'success' | 'info' | 'error', message: string) => {

    console.log('[toast]', type, message);

  });



  // --- onAddDataset guard (optional prop — default to no-op) ---

  const onAddDataset = propOnAddDataset || (() => {});



  // --- Core State (initialized empty, loaded from API) ---

  const [connections, setConnections] = useState<DataConnection[]>([]);

  const [syncTasks, setSyncTasks] = useState<DataSyncTask[]>([]);

  const [pipelines, setPipelines] = useState<DataPipeline[]>([]);

  const [healthChecks, setHealthChecks] = useState<DataHealthCheck[]>([]);

  const [objectTypes] = useState<ObjectType[]>(propObjectTypes || []);

  const [datasets] = useState<Dataset[]>(propDatasets || []);



  // --- Load data from backend API on mount ---

  useEffect(() => {

    import('./data-workbench/api').then(({ fetchDataConnections, fetchDataSyncTasks, fetchDataPipelines, fetchDataHealthChecks }) => {

      // Each fetch fn already try/catches internally and returns [] on error;

      // the .catch here is a defensive guard so a rejected promise never

      // becomes an unhandled rejection that silently drops UI state.

      fetchDataConnections().then(setConnections).catch(e => console.error('[data-workbench] connections load failed:', e));

      fetchDataSyncTasks().then(setSyncTasks).catch(e => console.error('[data-workbench] syncTasks load failed:', e));

      fetchDataPipelines().then(setPipelines).catch(e => console.error('[data-workbench] pipelines load failed:', e));

      fetchDataHealthChecks().then(setHealthChecks).catch(e => console.error('[data-workbench] healthChecks load failed:', e));

    }).catch(e => console.error('[data-workbench] API load failed:', e));

  }, []);



  // --- Shared Global Git & Output States for Seamless Multi-Tool Integration ---

  const [globalGitHistory, setGlobalGitHistory] = useState<any[]>([

    { hash: '4f9ca21', message: 'Merge pull request #12 from dev/add-metadata-index', author: 'ECOS-Spark-CI', time: '1小时前', tool: 'system' },

    { hash: 'bc0e882', message: 'fix: 修复飞行员空名造成的 Spark NullPointerException 报错', author: 'Guo Rongxiao', time: '2小时前', tool: 'code-repositories' },

    { hash: 'e2298a1', message: 'feat: 引入 Bronze 物理数据表映射并设计基本 PySpark 流', author: 'Guo Rongxiao', time: '昨日', tool: 'code-repositories' },

    { hash: '88bbca4', message: 'Initial commit / Setup aviation model templates', author: 'Ontology-Daemon', time: '3天前', tool: 'system' }

  ]);



  const [pipelineBuilderOutput, setPipelineBuilderOutput] = useState<{

    datasetPath: string;

    columns: string[];

    rowCount: number;

    lastCompiled: string;

    expressionsCount: number;

  } | null>(null);



  const handleCommitToGit = (message: string, filesChanged: string[]) => {

    const newHash = Math.random().toString(16).slice(2, 9);

    setGlobalGitHistory(prev => [

      {

        hash: newHash,

        message,

        author: 'Guo Rongxiao',

        time: '刚刚',

        tool: activeTab === 'guide' ? 'guide' : activeTab

      },

      ...prev

    ]);

  };



  // --- UI Navigation Tab ---

  const [localActiveTab, setLocalActiveTab] = useState<'connections' | 'syncs' | 'pipelines' | 'health' | 'lineage' | 'pipeline-builder' | 'code-repositories' | 'code-workbooks' | 'contour' | 'guide' | 'sql-query' | 'engine-config'>('guide');

  const activeTab = propActiveTab !== undefined ? propActiveTab : localActiveTab;

  const setActiveTab = (tab: any) => {

    if (onActiveTabChange) {

      onActiveTabChange(tab);

    } else {

      setLocalActiveTab(tab);

    }

  };



  // --- Guide Tab Interactive States ---

  const [guideActiveStep, setGuideActiveStep] = useState<number>(1);

  const [guideSelectedTool, setGuideSelectedTool] = useState<'pipeline-builder' | 'code-repositories' | 'code-workbooks' | 'contour'>('pipeline-builder');

  const [guideSimProgress, setGuideSimProgress] = useState<Record<number, 'idle' | 'running' | 'success' | 'failed'>>({

    1: 'idle', 2: 'idle', 3: 'idle', 4: 'idle', 5: 'idle'

  });

  const [guideLogs, setGuideLogs] = useState<Record<number, string[]>>({

    1: [], 2: [], 3: [], 4: [], 5: []

  });

  const [selectedMatrixCell, setSelectedMatrixCell] = useState<{ tool: string; phase: number } | null>({

    tool: 'pipeline-builder',

    phase: 2

  });

  const [showExportModal, setShowExportModal] = useState<boolean>(false);

  const [editingPipelineId, setEditingPipelineId] = useState<string | null>(null);



  const runGuideSimulation = (step: number) => {

    setGuideSimProgress(prev => ({ ...prev, [step]: 'running' }));

    setGuideLogs(prev => ({ ...prev, [step]: [`[ECOS Pipeline Agent] 🚀 启动步骤 ${step} 交互式模拟验证中...`] }));



    setTimeout(() => {

      if (step === 1) {

        setGuideSimProgress(prev => ({ ...prev, [1]: 'success' }));

        setGuideLogs(prev => ({

          ...prev,

          [1]: [

            `[ECOS-Ingest] 🚀 连接到物理源 [postgres_prod_db] (PostgreSQL 5432)...`,

            `[ECOS-Ingest] 📡 校验登录凭据及外部网络网关可用性... SUCCESS (握手耗时: 85ms)`,

            `[ECOS-Ingest] 🔍 探查目标表 schema 结构: flights_record [columns: 12, raw_size: 45MB]`,

            `[ECOS-Ingest] 📥 成功下推 SNAPSHOT 同步事务，正在加载数据到 DFS Bronze 文件系统系统...`,

            `[ECOS-Ingest] 📊 写入物理分区: /aviation/bronze/flights_raw/dt=2026-07-02/`,

            `[ECOS-Ingest] 🎉 提取完成！成功拉取 15,000 行原始航班报文记录。耗时 1.25s。`

          ]

        }));

        showToast('success', '步骤 1 模拟运行成功：物理源提取成功入湖！');

      } else if (step === 2) {

        setGuideSimProgress(prev => ({ ...prev, [2]: 'success' }));

        setGuideLogs(prev => ({

          ...prev,

          [2]: [

            `[Pipeline-Builder] 🔍 扫描 DAG 数据流向: flights_raw -> active_flights -> join_flights_pilots`,

            `[Pipeline-Builder] ⚙️ 编译 3 组内置数据清洗函数表达式:`,

            `   - expr 1: trim(flight_num) & upper(flight_num)`,

            `   - expr 2: coalesce(pilot_name, '未知飞行员')`,

            `   - expr 3: date_format(scheduled_departure, 'yyyy-MM-dd HH:mm')`,

            `[Pipeline-Builder] 🔗 执行 Hash Join 物理算子，将 active_flights 与 pilots_raw 依据 pilot_id 强关联...`,

            `[Pipeline-Builder] 💾 正在将计算结果写入 Silver 物理数据集 [ds_flights_clean]...`,

            `[Pipeline-Builder] 🎉 拓扑算子运行完毕！生成 12 列，4,500 行清洗后高质量明细大宽表。`

          ]

        }));

        showToast('success', '步骤 2 模拟运行成功：清洗与 Join 算子表达式运行完毕！');

      } else if (step === 3) {

        setGuideSimProgress(prev => ({ ...prev, [3]: 'success' }));

        setGuideLogs(prev => ({

          ...prev,

          [3]: [

            `[ECOS-Git] 🌲 发现当前活动分支: [dev/flight-weather-enrichment]`,

            `[ECOS-Git] 🔄 正在与主分支 [main] 进行安全差异对比 (Diff schema detection)...`,

            `[ECOS-Git] 🧪 自动触发 CI/CD 管道静态单元测试与 DAG 循环依赖检测...`,

            `[ECOS-Git]   - Test Case 1: Schema Type Matching -> PASSED`,

            `[ECOS-Git]   - Test Case 2: Downstream Lineage SLA Impact -> PASSED`,

            `[ECOS-Git] 📝 自动生成 Pull Request 合并请求 [PR #1204] 并指派 Peer Reviewer...`,

            `[ECOS-Git] 🎉 分支编译测试无冲突，符合 YARN/Doris 生产发布安全规范！`

          ]

        }));

        showToast('success', '步骤 3 模拟运行成功：Git开发分支 CI/CD 静态校验通过！');

      } else if (step === 4) {

        setGuideSimProgress(prev => ({ ...prev, [4]: 'success' }));

        setGuideLogs(prev => ({

          ...prev,

          [4]: [

            `[ECOS-Scheduler] 📅 注册自动调度策略: TRIGGER_ON_DATASET_UPDATE [ds_flights_raw]`,

            `[ECOS-Scheduler] 🛡️ 注册并激活 2 组数据质量健康检查 (Data Health Checks):`,

            `   - Check 1 [Row Count Minimum]: 判定输出行数必须 > 1000 行 & SLA 报警`,

            `   - Check 2 [Null Column Bound]: 监控 pilot_id 空值率，上限 1% 阈值`,

            `[ECOS-Scheduler] 📡 正在对目标物理表进行多维实时检测探针评估...`,

            `[ECOS-Scheduler]   - Check 1 Passed: 实际输出 4,500 行 (阈值 1000)`,

            `[ECOS-Scheduler]   - Check 2 Passed: 实际空值占比 0.12% (低于允许值 1%)`,

            `[ECOS-Scheduler] 🎉 时效与质量健康控制配置完成，自动注入日常监控 SLA。`

          ]

        }));

        showToast('success', '步骤 4 模拟运行成功：调度与 Data Health 检查成功激活！');

      } else if (step === 5) {

        setGuideSimProgress(prev => ({ ...prev, [5]: 'success' }));

        setGuideLogs(prev => ({

          ...prev,

          [5]: [

            `[Ontology-Mapper] 📦 读取清洗完成的生产数据集: [ds_flights_clean]`,

            `[Ontology-Mapper] 🧬 执行逻辑映射校验: 关联物理字段到标准逻辑属性类型:`,

            `   - [flight_id] -> 映射为主键属性: Object ID`,

            `   - [flight_num] -> 映射为核心标题属性: Display Title`,

            `   - [pilot_id] -> 映射为外键并生成链接类型 [Flight_to_Pilot]`,

            `[Ontology-Mapper] 🚀 正在向 Ontology 全局注册中心同步物理元数据契约...`,

            `[Ontology-Mapper] 🎉 发布成功！业务用户现在可在 Object Explorer 模块中搜索和操纵‘航班 (Flight)’与‘飞行员 (Pilot)’！`

          ]

        }));

        showToast('success', '步骤 5 模拟运行成功：数据集已成功映射并发布到 Active Ontology！');

      }

    }, 1200);

  };



  // --- Selected Sub-elements ---

  const [selectedConnId, setSelectedConnId] = useState<string>('postgres_prod_db');

  const [selectedTaskId, setSelectedTaskId] = useState<string>('sync_flights_schedule');

  const [selectedPipelineId, setSelectedPipelineId] = useState<string>('pipeline_aviation_ontology');

  const [selectedCheckId, setSelectedCheckId] = useState<string>('check_flights_freshness');



  // --- Pipeline Editor State ---

  const [selectedNodeId, setSelectedNodeId] = useState<string>('node_join_flights_pilots');

  const [isPipelineRunning, setIsPipelineRunning] = useState(false);

  const [pipelineLogs, setPipelineLogs] = useState<string[]>([]);



  // --- Connection Testing state ---

  const [testingConnId, setTestingConnId] = useState<string | null>(null);

  const [testingLogs, setTestingLogs] = useState<string[]>([]);



  // --- Creation Modals / Forms ---

  const [showAddConn, setShowAddConn] = useState(false);

  const [showAddSync, setShowAddSync] = useState(false);

  const [showAddCheck, setShowAddCheck] = useState(false);



  // --- Form fields state ---

  const [newConnName, setNewConnName] = useState('');

  const [newConnType, setNewConnType] = useState<'postgresql' | 's3' | 'rest_api' | 'sftp' | 'sap'>('postgresql');

  const [newConnHost, setNewConnHost] = useState('');

  const [newConnPort, setNewConnPort] = useState(5432);

  const [newConnUser, setNewConnUser] = useState('');



  const [newSyncName, setNewSyncName] = useState('');

  const [newSyncConn, setNewSyncConn] = useState('');

  const [newSyncTable, setNewSyncTable] = useState('');

  const [newSyncMode, setNewSyncMode] = useState<'snapshot' | 'incremental' | 'append'>('snapshot');

  const [newSyncSched, setNewSyncSched] = useState<'manual' | 'hourly' | 'daily' | 'cron'>('hourly');



  const [newCheckName, setNewCheckName] = useState('');

  const [newCheckDs, setNewCheckDs] = useState('');

  const [newCheckType, setNewCheckType] = useState<'row_count' | 'null_check' | 'schema_check' | 'freshness'>('row_count');

  const [newCheckThreshold, setNewCheckThreshold] = useState('1000');



  // --- External Interfaces List state ---

  const [showExternalInterfaces, setShowExternalInterfaces] = useState(false);



  // --- Enhanced Pipeline Builder (PB) States ---

  const [pipelineSubTab, setPipelineSubTab] = useState<'build' | 'optimization' | 'maintenance'>('build');

  const [computeEngine, setComputeEngine] = useState<'memory' | 'doris'>('memory');

  const [selectedFunctionCategory, setSelectedFunctionCategory] = useState<string>('string');

  const [searchFunctionKeyword, setSearchFunctionKeyword] = useState<string>('');

  const [selectedPBFunction, setSelectedPBFunction] = useState<string>('regex_extract');

  const [pbTargetColumn, setPbTargetColumn] = useState<string>('flight_num');

  const [pbArgs, setPbArgs] = useState<string[]>(['\'([A-Z]+-[0-9]+)\'', '1']);

  

  // Custom added transform steps on nodes

  const [activeTransforms, setActiveTransforms] = useState<Record<string, Array<{

    id: string;

    type: string;

    column: string;

    args: string[];

    description: string;

  }>>>({

    'node_flights_raw': [],

    'node_active_flights': [

      { id: 'step_1', type: 'trim', column: 'flight_num', args: [], description: '修剪 flight_num 首尾空格' },

      { id: 'step_2', type: 'upper', column: 'dep_airport', args: [], description: '将 dep_airport 强制转换为大写' }

    ],

    'node_join_flights_pilots': [

      { id: 'step_3', type: 'coalesce', column: 'pilot_name', args: ["'未知飞行员'"], description: '当 pilot_name 为空时替换为“未知飞行员”' }

    ]

  });



  // Spark Optimizer states

  const [optBroadcastJoin, setOptBroadcastJoin] = useState<boolean>(true);

  const [optSaltKey, setOptSaltKey] = useState<boolean>(true);

  const [optPartitionBy, setOptPartitionBy] = useState<string>('scheduled_departure');

  const [optCacheStrategy, setOptCacheStrategy] = useState<boolean>(true);

  const [optColumnPruning, setOptColumnPruning] = useState<boolean>(true);



  // Branch and Version states

  const [activeBranch, setActiveBranch] = useState<string>('main');

  const [pipelineVersions, setPipelineVersions] = useState<Array<{

    version: string;

    branch: string;

    author: string;

    timestamp: string;

    commitMsg: string;

    status: 'active' | 'merged' | 'draft';

  }>>([

    { version: 'v1.2.0', branch: 'main', author: 'Guorong Xiao', timestamp: '2026-07-02 18:30:00', commitMsg: 'Release 航空核心实体离线宽表融合管道', status: 'active' },

    { version: 'v1.2.1-beta', branch: 'dev/flight-weather-enrichment', author: 'Guorong Xiao', timestamp: '2026-07-02 21:00:00', commitMsg: 'Feat: 引入气象中心落湖数据的左关联计算步骤', status: 'draft' }

  ]);

  const [showPRModal, setShowPRModal] = useState<boolean>(false);



  // --- Helper Icons for Sources ---

  const getSourceIcon = (type: string) => {

    switch (type) {

      case 'postgresql': return 'Database';

      case 's3': return 'Cloud';

      case 'rest_api': return 'Globe';

      case 'sftp': return 'FolderGit';

      case 'sap': return 'Cpu';

      default: return 'HardDrive';

    }

  };



  const getSourceTypeLabel = (type: string) => {

    switch (type) {

      case 'postgresql': return 'PostgreSQL 关系型数据库';

      case 's3': return 'Amazon S3 云端对象存储';

      case 'rest_api': return 'REST OpenAPI 服务端点';

      case 'sftp': return 'SFTP 机组报文共享服务器';

      case 'sap': return 'SAP ERP 业务流连接器';

      default: return '未知数据源';

    }

  };



  // --- Action Handlers ---

  const testConnection = (connId: string) => {

    setTestingConnId(connId);

    setTestingLogs([]);

    const conn = connections.find(c => c.id === connId);

    if (!conn) return;



    const addLog = (msg: string, delay: number) => {

      setTimeout(() => {

        setTestingLogs(prev => [...prev, msg]);

      }, delay);

    };



    addLog(`[ECOS Ingress] 🔍 正在拉取物理适配器配置...`, 200);

    addLog(`[ECOS Ingress] 🔌 初始化协议驱动程序：[Protocol: ${conn.type.toUpperCase()}]`, 400);

    

    if (conn.type === 'postgresql') {

      addLog(`[JDBC Connector] 📡 连线目标端点 ${conn.config.host}:${conn.config.port}...`, 700);

      addLog(`[JDBC Connector] 🔑 提交认证凭据 (User: ${conn.config.username})...`, 1000);

    } else if (conn.type === 's3') {

      addLog(`[AWS S3 Ingress] 📡 连线 S3 API 端点，解析 Bucket: ${conn.config.bucket}...`, 700);

      addLog(`[AWS S3 Ingress] 🔑 调用 IAM 扮演角色 (AssumeRole) 授权校验...`, 1000);

    } else if (conn.type === 'rest_api') {

      addLog(`[REST Ingress] 📡 发送 HTTP GET 探测握手 ${conn.config.endpointUrl}...`, 700);

    } else if (conn.type === 'sftp') {

      addLog(`[SFTP Connector] 📡 建立 SSH 握手套接字 ${conn.config.host}...`, 700);

    } else {

      addLog(`[SAP Connector] 📡 调用 SAP RFC 核心 SDK, 寻址服务器 ${conn.config.host}...`, 700);

    }



    setTimeout(() => {

      if (conn.id === 'crew_schedules_sftp') {

        setTestingLogs(prev => [

          ...prev,

          `[SFTP ERROR] ❌ SSH 握手连接失败: SSH_MSG_DISCONNECT (Connection reset by peer).`,

          `[ECOS Core] ⚠️ 测试未通过。由于网络阻断，暂无法检索对方机组排班目录。`

        ]);

        setConnections(prev => prev.map(c => c.id === connId ? { ...c, status: 'error' } : c));

        showToast('error', `数据源 [${conn.name}] 连通性测试失败！`);

      } else {

        const timeNow = new Date().toISOString().replace('T', ' ').substring(0, 19);

        setTestingLogs(prev => [

          ...prev,

          `[ECOS Ingress] ✅ 连通成功！`,

          `[ECOS Ingress] 📖 成功拉取元数据。当前物理源可用表/目录数: ${conn.tablesAvailable.length} 个。`

        ]);

        setConnections(prev => prev.map(c => c.id === connId ? {

          ...c,

          status: 'connected',

          config: { ...c.config, lastTested: timeNow }

        } : c));

        showToast('success', `数据源 [${conn.name}] 物理连接连通性验证通过！`);

      }

      setTestingConnId(null);

    }, 1800);

  };



  const handleCreateConnection = () => {

    if (!newConnName.trim()) {

      showToast('error', '请输入连接名称');

      return;

    }

    const newId = `conn_${Date.now().toString().slice(-4)}`;

    const newConn: DataConnection = {

      id: newId,

      name: newConnName,

      type: newConnType,

      status: 'testing',

      config: {

        host: newConnHost || 'localhost',

        port: newConnPort,

        username: newConnUser || 'anonymous',

        bucket: newConnType === 's3' ? 's3://new-bucket-name' : undefined,

        endpointUrl: newConnType === 'rest_api' ? 'https://api.external.com/v1' : undefined

      },

      tablesAvailable: [

        {

          name: 'raw_imported_table_1',

          rowCount: 25000,

          columns: [

            { name: 'id', type: 'integer' },

            { name: 'record_payload', type: 'string' },

            { name: 'sync_timestamp', type: 'timestamp' }

          ]

        }

      ]

    };



    setConnections([...connections, newConn]);

    setSelectedConnId(newId);

    setShowAddConn(false);

    showToast('success', `新建物理数据源连接 [${newConnName}] 成功！`);

    

    // Auto test after creation

    setTimeout(() => testConnection(newId), 500);

  };



  const triggerSyncTask = (taskId: string) => {

    setSyncTasks(prev => prev.map(t => t.id === taskId ? { ...t, status: 'running' } : t));

    showToast('info', '正在后台调度集群执行拉取任务...');



    setTimeout(() => {

      const task = syncTasks.find(t => t.id === taskId);

      const randomSuccess = taskId !== 'sync_sap_costs'; // SAP sync is configured to fail

      const timeNow = new Date().toISOString().replace('T', ' ').substring(0, 19);



      if (randomSuccess) {

        setSyncTasks(prev => prev.map(t => t.id === taskId ? {

          ...t,

          status: 'success',

          lastRunTime: timeNow,

          recordsSynced: (t.recordsSynced || 0) + Math.floor(Math.random() * 200) + 5,

          errorMessage: undefined

        } : t));

        showToast('success', `数据提取任务 [${task?.name}] 执行成功，新记录已同步入湖！`);

      } else {

        setSyncTasks(prev => prev.map(t => t.id === taskId ? {

          ...t,

          status: 'failed',

          lastRunTime: timeNow,

          errorMessage: '[SAP RFC ERROR] ERP Gateway Connection timed out. The server was unreachable.'

        } : t));

        showToast('error', `数据提取任务 [${task?.name}] 执行失败，请查看底层诊断日志。`);

      }

    }, 1500);

  };



  const handleCreateSyncTask = () => {

    if (!newSyncName.trim() || !newSyncTable.trim()) {

      showToast('error', '请完整输入任务名称与目标表名');

      return;

    }

    const targetDsId = `ds_raw_${newSyncTable.toLowerCase()}`;

    const newTask: DataSyncTask = {

      id: `sync_${Date.now().toString().slice(-4)}`,

      name: newSyncName,

      sourceConnectionId: newSyncConn || connections[0].id,

      sourceTable: newSyncTable,

      targetDatasetId: targetDsId,

      syncMode: newSyncMode,

      schedule: newSyncSched,

      status: 'paused',

      recordsSynced: 0

    };



    setSyncTasks([...syncTasks, newTask]);

    setSelectedTaskId(newTask.id);

    setShowAddSync(false);

    showToast('success', `同步任务 [${newSyncName}] 初始化完成，默认为“暂停”状态。`);

  };



  const runPipeline = (pipelineId: string) => {

    setIsPipelineRunning(true);

    setPipelineLogs([]);

    const pl = pipelines.find(p => p.id === pipelineId);

    if (!pl) return;



    const addLog = (msg: string, delay: number) => {

      setTimeout(() => {

        setPipelineLogs(prev => [...prev, msg]);

      }, delay);

    };



    if (computeEngine === 'doris') {

      addLog(`[Apache Doris Engine] 🚀 连接到 Doris FE (Frontend) 协调器节点...`, 100);

      addLog(`[Apache Doris Engine] 📥 解析并编译 Doris OLAP 物理查询计划 (SQL Execution Plan)...`, 300);

      addLog(`[Apache Doris Engine] ⚙️ 发现有 ${pl.nodes.length} 个 DAG 执行节点。合并聚合将被下推至分布式 BE 计算...`, 500);

      addLog(`[Doris BE Executor] 📖 执行本地 Colocate Join 策略，分摊 Shuffle 运算开销...`, 800);

      addLog(`[Doris BE Executor] 🔄 成功进行物化视图 (Materialized View) 分区实时预聚合计算...`, 1200);

      addLog(`[Doris BE Executor] 💾 数据列式批量事务写入中 (Batch Insert Transaction committing)...`, 1700);

    } else {

      addLog(`[In-Memory Engine] 🚀 启动浏览器 Sandbox 虚拟 V8 内存计算容器...`, 100);

      addLog(`[In-Memory Engine] 📥 成功加载上游物理源微元数据到 RAM Heap (行内快速内存缓冲区)...`, 300);

      addLog(`[In-Memory Engine] ⚙️ 发现有 ${pl.nodes.length} 个逻辑算子。启用单机高内聚运算...`, 500);

      addLog(`[Memory Executor] 📖 运行 JS Array.prototype.map() 链式过滤与规则格式转换...`, 800);

      addLog(`[Memory Executor] 🔄 运行 Hash Join O(1) 内存探查匹配，完成 pilot_id 关联映射...`, 1200);

      addLog(`[Memory Executor] 💾 计算圆满结束。无物理 IO 磁盘损耗。同步写入虚拟 Cache 缓冲端...`, 1700);

    }



    setTimeout(() => {

      const timeNow = new Date().toISOString().replace('T', ' ').substring(0, 19);

      const engineLabel = computeEngine === 'doris' ? 'Apache Doris OLAP' : 'In-Memory JS';

      const duration = computeEngine === 'doris' ? '1.45s (分布式向量化)' : '0.12s (主内存)';

      setPipelineLogs(prev => [

        ...prev,

        `[${engineLabel}] 📊 计算执行耗时：${duration}, 批写入物化行数: 4,500.`,

        `[${engineLabel}] 🎉 流水线执行及重算已圆满结束，清洗后物化数据集已全部同步发布入 Ontology 数据层！`

      ]);

      setPipelines(prev => prev.map(p => p.id === pipelineId ? { ...p, status: 'success', lastExecuted: timeNow } : p));

      setIsPipelineRunning(false);

      showToast('success', `数据清洗管道 [${pl.name}] ${computeEngine === 'doris' ? 'Doris OLAP 数仓' : '内存'}重算运行成功！`);

    }, 2200);

  };



  const handleCreateHealthCheck = () => {

    if (!newCheckName.trim()) {

      showToast('error', '请输入健康检查名称');

      return;

    }

    const newCheck: DataHealthCheck = {

      id: `check_${Date.now().toString().slice(-4)}`,

      datasetId: newCheckDs || 'ds_flights_clean',

      name: newCheckName,

      checkType: newCheckType,

      config: {

        minRows: newCheckType === 'row_count' ? parseInt(newCheckThreshold) : undefined,

        maxNullPercentage: newCheckType === 'null_check' ? parseFloat(newCheckThreshold) : undefined,

        maxDelayMinutes: newCheckType === 'freshness' ? parseInt(newCheckThreshold) : undefined,

        targetColumn: 'pilot_id'

      },

      status: 'pending',

      lastChecked: '无',

      message: '尚未开始运行检测'

    };



    setHealthChecks([...healthChecks, newCheck]);

    setSelectedCheckId(newCheck.id);

    setShowAddCheck(false);

    showToast('success', `新建数据质量健康检查 [${newCheckName}] 成功！`);

  };



  const runSingleHealthCheck = (checkId: string) => {

    const scanMsg = computeEngine === 'doris' ? '正在下推 Apache Doris 执行多维质量分析扫描...' : '正在启动 Client In-Memory V8 沙箱质量规则校验...';

    setHealthChecks(prev => prev.map(c => c.id === checkId ? { ...c, status: 'pending', message: scanMsg } : c));

    

    setTimeout(() => {

      const timeNow = new Date().toISOString().replace('T', ' ').substring(0, 19);

      setHealthChecks(prev => prev.map(c => {

        if (c.id !== checkId) return c;

        let finalStatus: 'passed' | 'warning' | 'failed' = 'passed';

        let msg = '通过：规则判定完全符合标准。';



        if (c.checkType === 'row_count') {

          const min = c.config.minRows || 1000;

          msg = `通过：检测到目的地数据行数为 1,240,000 行，多于设定的最少阈值 ${min} 行。`;

        } else if (c.checkType === 'null_check') {

          msg = `通过：字段 [pilot_id] 空值率检查，实际空值率仅 0.12%，低于限定的警戒比例。`;

        } else if (c.checkType === 'freshness') {

          finalStatus = 'warning';

          msg = `警告：当前最新航班时间延时达 105 分钟，触及或超过 90 分钟的日常警示水平。`;

        } else {

          msg = `通过：数据集表结构架构与注册的 Ontology 实体的元数据完全保持一致。`;

        }



        return {

          ...c,

          status: finalStatus,

          lastChecked: timeNow,

          message: msg

        };

      }));

      showToast('success', '数据健康规则检测运算结束');

    }, 1000);

  };



  return (

    <div className={`flex-1 flex flex-col min-h-0 ${styles.appBg} relative overflow-hidden font-sans`}>

      

      {/* Upper sub-banner */}

      <div className="bg-slate-900 border-b border-slate-800 text-slate-300 px-6 py-2 flex justify-between items-center select-none text-[11px] font-medium shrink-0">

        <div className="flex items-center gap-6">

          <div className="flex items-center gap-1.5 text-blue-400">

            <LucideIcon name="Workflow" size={13} />

            <span className="font-semibold text-white">{t("dw.txt.7de8db")}</span>

          </div>

          <div className="h-3 w-px bg-slate-800" />

          <div className="text-slate-400">{t("dw.txt.65779d")}</div>

        </div>

        <div className="flex items-center gap-4">

          <button

            onClick={() => setShowExternalInterfaces(!showExternalInterfaces)}

            className="flex items-center gap-1.5 px-2.5 py-1 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded border border-slate-700 transition-colors cursor-pointer"

          >

            <LucideIcon name="Layers" size={11} className="text-amber-500" />

            <span>外界系统接口视图 ({connections.length})</span>

          </button>

          <div className="text-slate-500">

            活跃计算引擎: <span className="text-blue-400 font-bold">{computeEngine === 'doris' ? 'Apache Doris OLAP' : 'In-Memory JS'}</span>

          </div>

        </div>

      </div>



      {/* Main workspace frame: Sidebar + Body */}

      <div className="flex-1 flex overflow-hidden">

        

        {/* Left Sub-Navigation */}

        <div className="w-52 bg-white border-r border-slate-200 flex flex-col justify-between shrink-0 select-none">

          <div className="py-3 px-3 space-y-1">

            <div className="text-[10px] font-bold text-slate-400 uppercase tracking-wider px-2.5 mb-2">{t("dw.txt.58d7c8")}</div>

            

            <button

              onClick={() => setActiveTab('guide')}

              className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs rounded-md transition-all font-semibold ${

                activeTab === 'guide'

                  ? 'bg-indigo-50 text-indigo-800 border-l-2 border-indigo-600 font-extrabold shadow-sm'

                  : 'text-slate-600 hover:bg-slate-100'

              }`}

            >

              <LucideIcon name="Lightbulb" size={14} className={activeTab === 'guide' ? 'text-indigo-600' : 'text-slate-400'} />

              <span>{t("dw.txt.c56266")}</span>

            </button>



            <button

              onClick={() => setActiveTab('connections')}

              className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs rounded-md transition-all font-semibold ${

                activeTab === 'connections'

                  ? 'bg-blue-50 text-blue-700 border-l-2 border-blue-600'

                  : 'text-slate-600 hover:bg-slate-100'

              }`}

            >

              <LucideIcon name="Database" size={14} className={activeTab === 'connections' ? 'text-blue-600' : 'text-slate-400'} />

              <span>{t("dw.txt.102168")}</span>

            </button>



            <button

              onClick={() => setActiveTab('syncs')}

              className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs rounded-md transition-all font-semibold ${

                activeTab === 'syncs'

                  ? 'bg-blue-50 text-blue-700 border-l-2 border-blue-600'

                  : 'text-slate-600 hover:bg-slate-100'

              }`}

            >

              <LucideIcon name="Import" size={14} className={activeTab === 'syncs' ? 'text-blue-600' : 'text-slate-400'} />

              <span>{t("dw.txt.11cefc")}</span>

            </button>



            <button

              onClick={() => setActiveTab('pipelines')}

              className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs rounded-md transition-all font-semibold ${

                activeTab === 'pipelines'

                  ? 'bg-blue-50 text-blue-700 border-l-2 border-blue-600'

                  : 'text-slate-600 hover:bg-slate-100'

              }`}

            >

              <LucideIcon name="Cpu" size={14} className={activeTab === 'pipelines' ? 'text-blue-600' : 'text-slate-400'} />

              <span>{t("dw.txt.fdcb6f")}</span>

            </button>



            <button

              onClick={() => setActiveTab('health')}

              className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs rounded-md transition-all font-semibold ${

                activeTab === 'health'

                  ? 'bg-blue-50 text-blue-700 border-l-2 border-blue-600'

                  : 'text-slate-600 hover:bg-slate-100'

              }`}

            >

              <LucideIcon name="ShieldAlert" size={14} className={activeTab === 'health' ? 'text-blue-600' : 'text-slate-400'} />

              <span>{t("dw.txt.1b23b5")}</span>

            </button>



            <button

              onClick={() => setActiveTab('lineage')}

              className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs rounded-md transition-all font-semibold ${

                activeTab === 'lineage'

                  ? 'bg-blue-50 text-blue-700 border-l-2 border-blue-600'

                  : 'text-slate-600 hover:bg-slate-100'

              }`}

            >

              <LucideIcon name="Workflow" size={14} className={activeTab === 'lineage' ? 'text-blue-600' : 'text-slate-400'} />

              <span>{t("dw.txt.0f541b")}</span>

            </button>


            {/* 核心开发工具菜单隐藏，已根据顶层规划将 Pipeline Builder 等入口合并至 pipelines 列表编辑按钮 */}

            {/* SQL 查询 */}
            <button
              onClick={() => setActiveTab('sql-query')}
              className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs rounded-md transition-all font-semibold ${
                activeTab === 'sql-query'
                  ? 'bg-blue-50 text-blue-700 border-l-2 border-blue-600'
                  : 'text-slate-600 hover:bg-slate-100'
              }`}
            >
              <LucideIcon name="Search" size={14} className={activeTab === 'sql-query' ? 'text-blue-600' : 'text-slate-400'} />
              <span>SQL 查询</span>
            </button>

            {/* 引擎配置 */}
            <button
              onClick={() => setActiveTab('engine-config')}
              className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs rounded-md transition-all font-semibold ${
                activeTab === 'engine-config'
                  ? 'bg-blue-50 text-blue-700 border-l-2 border-blue-600'
                  : 'text-slate-600 hover:bg-slate-100'
              }`}
            >
              <LucideIcon name="Settings" size={14} className={activeTab === 'engine-config' ? 'text-blue-600' : 'text-slate-400'} />
              <span>⚙ 引擎配置</span>
            </button>

            {/*

            ... Pipeline Builder / Code Repositories / Workbooks / Contour 待后续开放

            */}

          </div>



          {/* Quick Stats sidebar footer */}

          <div className="p-4 border-t border-slate-100 bg-slate-50/50 space-y-2 text-[10px] text-slate-500">

            <div className="font-semibold text-slate-700">{t("dw.txt.419d9f")}</div>

            <div className="flex justify-between">

              <span>{t("dw.txt.f7d9ac")}</span>

              <span className="font-mono text-slate-800 font-semibold">{connections.length} 处</span>

            </div>

            <div className="flex justify-between">

              <span>{t("dw.txt.1988fc")}</span>

              <span className="font-mono text-slate-800 font-semibold">{syncTasks.length} 个</span>

            </div>

            <div className="flex justify-between">

              <span>{t("dw.txt.44a230")}</span>

              <span className="font-mono text-slate-800 font-semibold">{objectTypes.length} 类</span>

            </div>

          </div>

        </div>



        {/* Dynamic Inner Body */}

        <div className="flex-1 flex overflow-hidden min-w-0">

          

          {/* TAB 0: OFFICIAL PIPELINE GUIDE */}

        {activeTab === 'guide' && (

            <div className="flex-1 flex flex-col overflow-y-auto bg-slate-50 p-6 select-none">
              
              {/* Header Banner */}
              <div className="mb-6 bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 text-white rounded-2xl p-6 shadow-md border border-slate-800 relative overflow-hidden">
                <div className="absolute right-0 top-0 translate-x-12 -translate-y-8 w-64 h-64 bg-indigo-500/10 rounded-full blur-3xl pointer-events-none" />
                <div className="relative z-10 flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
                  <div>
                    <div className="flex items-center gap-2 mb-2">
                      <span className="bg-amber-500 text-slate-950 text-[10px] font-bold uppercase px-2 py-0.5 rounded-full">
                        Palantir Docs
                      </span>
                      <span className="text-[11px] text-slate-300 font-mono">
                        ECOS / Building Pipelines / Overview
                      </span>
                    </div>
                    <h2 className="text-xl font-black tracking-tight text-white font-sans">
                      ECOS 数据管道构建官方规范与实战中心
                    </h2>
                    <p className="text-xs text-slate-300 mt-1.5 leading-relaxed max-w-3xl">
                      在 ECOS 中，构建数据管道（Pipeline）是将原始物理数据（Bronze）加工融合为高质量干净数据集（Silver/Gold），并最终映射绑定到业务 Ontology 实体对象（Active Objects）的核心方法。
                    </p>
                  </div>
                  <div className="bg-white/10 backdrop-blur-md rounded-xl p-3 border border-white/10 shrink-0 text-right">
                    <span className="text-[9px] text-indigo-200 block font-mono font-bold uppercase tracking-wider">首选数据引擎</span>
                    <span className="text-sm font-extrabold text-amber-400 block font-mono">In-Memory / Apache Doris</span>
                  </div>
                </div>
              </div>

              {/* 🏆 NEW SECTION: TOP PLANNING BLUEPRINT - FOUR TOOLS & FIVE PHASES */}
              <div className="mb-6 bg-white border border-slate-200 rounded-2xl p-6 shadow-sm select-text">
                <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 mb-4 border-b border-slate-100 pb-4">
                  <div>
                    <div className="flex items-center gap-2 mb-1.5">
                      <span className="p-1 bg-amber-100 text-amber-700 rounded-lg">
                        <LucideIcon name="Lightbulb" size={16} className="text-amber-600" />
                      </span>
                      <h3 className="text-base font-extrabold text-slate-900 font-sans">
                        「四工具完美融入五环节」金牌数据架构规划大白皮书
                      </h3>
                    </div>
                    <p className="text-xs text-slate-500 font-sans leading-relaxed">
                      将四大核心开发工具配属在其最擅长的物理/逻辑生命周期环节中。点击矩阵格子可实时查看联动实施细则与反模式警示。
                    </p>
                  </div>
                  
                  <button
                    onClick={() => setShowExportModal(true)}
                    className="flex items-center gap-2 px-3 py-1.5 bg-gradient-to-r from-indigo-600 to-indigo-700 hover:from-indigo-700 hover:to-indigo-800 text-white rounded-lg text-xs font-bold transition-all shadow-xs shrink-0 cursor-pointer"
                  >
                    <LucideIcon name="Download" size={13} />
                    <span>预览并一键导出方案说明书</span>
                  </button>
                </div>

                {/* Matrix Grid */}
                <div className="overflow-x-auto border border-slate-200 rounded-xl mb-6 bg-slate-50/50 select-none">
                  <table className="w-full text-left border-collapse text-xs min-w-[800px]">
                    <thead>
                      <tr className="bg-slate-100/80 border-b border-slate-200 text-[10px] text-slate-500 font-extrabold uppercase">
                        <th className="p-4 border-r border-slate-200 w-[200px]">ECOS 核心开发工具</th>
                        {[
                          { id: 1, name: '环节 1: Ingest', sub: '物理源注册与数据拉取' },
                          { id: 2, name: '环节 2: Transform', sub: '算子清洗与数据建模' },
                          { id: 3, name: '环节 3: Verify', sub: '分支协同与血缘冷演练' },
                          { id: 4, name: '环节 4: Schedule', sub: '调度生命与健康监控' },
                          { id: 5, name: '环节 5: Publish', sub: 'Ontology绑定与全发布' }
                        ].map(col => (
                          <th key={col.id} className="p-3 border-r border-slate-200 last:border-r-0">
                            <div className="font-bold text-slate-800">{col.name}</div>
                            <div className="font-normal text-[9px] lowercase text-slate-400 font-mono mt-0.5">{col.sub}</div>
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {matrixData.map(row => (
                        <tr key={row.toolId} className="border-b border-slate-200 last:border-b-0 bg-white hover:bg-slate-50/40 transition-colors">
                          <td className="p-4 border-r border-slate-200 font-bold text-slate-700 flex items-center gap-2">
                            <div className="p-1 bg-slate-100 rounded-md text-slate-600">
                              <LucideIcon name={row.toolIcon as any} size={14} />
                            </div>
                            <span>{row.toolName}</span>
                          </td>
                          {[1, 2, 3, 4, 5].map(phaseNum => {
                            const detail = row.phases[phaseNum];
                            const isSelected = selectedMatrixCell?.tool === row.toolId && selectedMatrixCell?.phase === phaseNum;
                            
                            let badgeStyle = 'bg-slate-50 text-slate-400 border-slate-200';
                            if (detail.role === 'PRIMARY') {
                              badgeStyle = 'bg-emerald-50 text-emerald-700 border-emerald-200 font-extrabold hover:bg-emerald-100';
                            } else if (detail.role === 'SECONDARY') {
                              badgeStyle = 'bg-indigo-50 text-indigo-700 border-indigo-200 hover:bg-indigo-100';
                            }
                            
                            return (
                              <td
                                key={phaseNum}
                                onClick={() => setSelectedMatrixCell({ tool: row.toolId, phase: phaseNum })}
                                className={`p-2.5 border-r border-slate-200 last:border-r-0 cursor-pointer transition-all relative ${
                                  isSelected ? 'bg-amber-50/55 ring-2 ring-amber-500 ring-inset' : ''
                                }`}
                              >
                                <div className="flex flex-col gap-1">
                                  <div className={`w-fit px-2 py-0.5 text-[10px] rounded-full border ${badgeStyle} transition-colors text-center`}>
                                    {detail.roleLabel || '不关联'}
                                  </div>
                                  <div className="text-[10px] text-slate-500 line-clamp-2 leading-relaxed font-sans">
                                    {detail.desc}
                                  </div>
                                </div>
                                {isSelected && (
                                  <div className="absolute top-1.5 right-1.5 w-1.5 h-1.5 bg-amber-500 rounded-full" />
                                )}
                              </td>
                            );
                          })}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* Detail Panel of Selected Matrix Cell */}
                {selectedMatrixCell && (() => {
                  const toolItem = matrixData.find(t => t.toolId === selectedMatrixCell.tool);
                  const cellDetail = toolItem?.phases[selectedMatrixCell.phase];
                  const phaseNames: Record<number, string> = {
                    1: '环节 1: Ingest (物理源拉取入湖)',
                    2: '环节 2: Transform (算子清洗与建模)',
                    3: '环节 3: Verify (分支协同与血缘冷演练)',
                    4: '环节 4: Schedule (调度生命与健康监控)',
                    5: '环节 5: Publish (Ontology实体模型映射与发布)'
                  };
                  
                  if (!toolItem || !cellDetail) return null;
                  
                  return (
                    <div className="bg-slate-50 rounded-xl border border-slate-200 p-4 transition-all">
                      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-2 mb-3 pb-3 border-b border-slate-200">
                        <div className="flex items-center gap-2">
                          <span className="p-1 bg-indigo-100 text-indigo-700 rounded-md animate-pulse">
                            <LucideIcon name={toolItem.toolIcon as any} size={14} />
                          </span>
                          <span className="font-extrabold text-xs text-slate-800 font-sans">
                            {toolItem.toolName}
                          </span>
                          <span className="text-slate-400 text-xs">➔</span>
                          <span className="font-extrabold text-xs text-indigo-700 font-sans">
                            {phaseNames[selectedMatrixCell.phase]}
                          </span>
                        </div>
                        <div className="flex items-center gap-1.5">
                          <span className="text-[10px] text-slate-400">配属等级:</span>
                          <span className={`px-2.5 py-0.5 text-[10px] font-bold rounded-full uppercase border ${
                            cellDetail.role === 'PRIMARY'
                              ? 'bg-emerald-100 text-emerald-800 border-emerald-300'
                              : cellDetail.role === 'SECONDARY'
                              ? 'bg-indigo-100 text-indigo-800 border-indigo-300'
                              : 'bg-slate-100 text-slate-600 border-slate-300'
                          }`}>
                            {cellDetail.role === 'PRIMARY' ? '★ 核心主导' : cellDetail.role === 'SECONDARY' ? '☆ 辅助协作' : '○ 无直接关联'}
                          </span>
                        </div>
                      </div>

                      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
                        <div className={`${cellDetail.code ? 'lg:col-span-5' : 'lg:col-span-12'} flex flex-col justify-between gap-3`}>
                          <div className="space-y-2">
                            <div className="text-[11px] font-bold text-slate-700 uppercase tracking-wider font-mono">架构规划细则 (Implementation Architecture)</div>
                            <p className="text-xs text-slate-600 leading-relaxed font-sans">
                              {cellDetail.desc}
                            </p>
                          </div>

                          {cellDetail.antiPattern && (
                            <div className="p-3 bg-amber-50/80 border-l-4 border-amber-500 rounded-r-lg text-[11px] text-amber-900 space-y-1">
                              <div className="font-extrabold flex items-center gap-1">
                                <LucideIcon name="ShieldAlert" size={12} className="text-amber-600 animate-bounce" />
                                <span>ECOS 官方反模式警示 (Anti-Pattern Warning)</span>
                              </div>
                              <p className="leading-relaxed font-sans">
                                {cellDetail.antiPattern}
                              </p>
                            </div>
                          )}
                        </div>

                        {cellDetail.code && (
                          <div className="lg:col-span-7 flex flex-col">
                            <div className="flex justify-between items-center mb-1.5">
                              <span className="text-[10px] font-extrabold text-slate-500 uppercase tracking-wider font-mono">
                                生产级规范代码/声明配置示例 (Golden Code Sample)
                              </span>
                              <button
                                onClick={() => {
                                  navigator.clipboard.writeText(cellDetail.code || '');
                                  showToast('success', '代码模板已复制到剪贴板！');
                                }}
                                className="flex items-center gap-1 text-[10px] text-indigo-600 hover:text-indigo-800 bg-indigo-50 hover:bg-indigo-100 transition-colors px-2 py-0.5 rounded cursor-pointer"
                              >
                                <LucideIcon name="Copy" size={10} />
                                <span>复制示例</span>
                              </button>
                            </div>
                            <div className="bg-slate-900 rounded-lg p-3 text-emerald-400 font-mono text-[11px] overflow-x-auto max-h-[180px] leading-relaxed border border-slate-800 shadow-inner select-text">
                              <pre>{cellDetail.code}</pre>
                            </div>
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })()}
              </div>

              {/* 📥 EXPORT BLUEPRINT PROPOSAL MODAL */}
              {showExportModal && (
                <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-sm z-50 flex items-center justify-center p-4 select-text">
                  <div className="bg-white rounded-2xl w-full max-w-4xl shadow-2xl border border-slate-200 flex flex-col max-h-[90vh] overflow-hidden animate-in fade-in zoom-in-95 duration-200">
                    <div className="p-5 border-b border-slate-100 bg-slate-50 flex justify-between items-center shrink-0">
                      <div className="flex items-center gap-2">
                        <span className="p-1.5 bg-indigo-100 text-indigo-700 rounded-lg">
                          <LucideIcon name="BookOpen" size={16} className="text-indigo-600 font-bold" />
                        </span>
                        <div>
                          <h4 className="font-extrabold text-sm text-slate-900 font-sans">
                            Palantir ECOS「四工具完美融入五环节」金牌架构集成策划大白皮书
                          </h4>
                          <p className="text-[11px] text-slate-400 font-sans">
                            标准官方规范 · 生产级落地部署指南 · 杜绝杜撰与反模式
                          </p>
                        </div>
                      </div>
                      <button
                        onClick={() => setShowExportModal(false)}
                        className="text-slate-400 hover:text-slate-600 hover:bg-slate-100 p-1.5 rounded-lg transition-colors cursor-pointer"
                      >
                        <LucideIcon name="X" size={16} />
                      </button>
                    </div>

                    <div className="flex-1 overflow-y-auto p-6 bg-slate-50 space-y-4">
                      <div className="flex justify-between items-center mb-1 select-none">
                        <span className="text-xs font-extrabold text-slate-500 uppercase font-mono">
                          策划案内容预览 (MarkDown 格式)
                        </span>
                        <button
                          onClick={() => {
                            const markdownText = `# 🔷 Palantir ECOS 「四开发工具完美融入五步法数据管道」顶层集成部署策划案

## 1. 顶层数据集成理念 (Top-Level Architecture Philosophy)
Palantir ECOS 的零信任一体化集成架构强调：“物理拉取轻量化，清洗编排标准化，分支协作代码化，质量健康熔断化，业务模型实体化”。通过将 Pipeline Builder、Code Repositories、Code Workbooks、Contour 四大核心工具完美配属到 Ingest（物理入湖）、Transform（逻辑清洗）、Verify（分支演练）、Schedule（质量监控）、Publish（Ontology实体映射） 五个标准生命周期环节，打通从“脏原始物理连接”到“高可用活性业务对象 (Active Objects)”的最后一公里。

---

## 2. 工具与环节完美融合矩阵 (The Synergy Matrix)

| 核心开发工具 | 环节 1: Ingest (物理源拉取) | 环节 2: Transform (逻辑清洗建模) | 环节 3: Verify (版本分支控制) | 环节 4: Schedule (质量时效双控) | 环节 5: Publish (Ontology实体映射) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Pipeline Builder**\\n*(低代码图形算子)* | **辅助协作**\\n支持拖拽导入原始物理表。 | **绝对主导 (Primary)**\\n流批一体无代码级联字段清洗。 | **深度主导 (Primary)**\\n可视化 DAG 血缘及开发分支热部署。 | **绝对主导 (Primary)**\\n图形化配置 Data Health Checks 并挂载。 | **绝对主导 (Primary)**\\n物理黄金宽表一键建立 Ontology 实体映射。 |
| **Code Repositories**\\n*(高代码工程仓库)* | **辅助支持**\\n高级 Spark Ingress 自定义驱动编写。 | **绝对主导 (Primary)**\\n百亿级巨型体量、高复杂度算子及特征计算。 | **绝对主导 (Primary)**\\nGit-First 分支冷演练、PR 自动化 CI 质检。 | **辅助协作**\\nYAML/Config 声明复杂任务协同分组。 | **绝对主导 (Primary)**\\n编写可重用的 Functions on Objects。 |
| **Code Workbooks**\\n*(交互式沙箱工作簿)*| **无直接关联 (N/A)**\\n纯探索产品，不写入物理主表。 | **辅助支持**\\n数据科学前中期多语种混写原型。 | **辅助协作**\\n单 Workbook 自带逻辑分支依赖推演。 | **辅助协同**\\n离线克隆脏数据，通过 PyPandas 排障。 | **辅助支持**\\n支持将机器学习模型发布为逻辑模型对象。 |
| **Contour**\\n*(亚秒分析下钻)* | **无直接关联 (N/A)**\\n无需关注底层物理数据库。 | **辅助协作**\\n通过交互式卡片拼装临时业务聚合表。 | **辅助协同**\\n追踪数据集宏观血缘，辅助质检合规。 | **辅助协助**\\n业务质检合规官的日常数据看板巡检。 | **绝对主导 (Primary)**\\n无需 SQL 极速探查 and 消费 Ontology 对象实体。 |

---

## 3. 五大环节的 golden standard (黄金落地规范)

### 环节 1: Ingest (物理源注册与数据拉取)
- 核心工具配属：Data Connection 托管，Pipeline Builder 作为消费者导入。
- 最佳规范：
  1. 所有物理连接凭证（明文密码、私钥）严禁硬编码。必须统一注入 ECOS Vault 或密钥管理中心，采取零信任 KMS 加密。
  2. 采用分布式 Magritte Agent 作为内网网关代理，支持多点断点续传及高并发限流。

### 环节 2: Transform (算子表达式与逻辑关联)
- 核心工具配属：Pipeline Builder (80% 过滤与关联场景) + Code Repositories (20% 巨型体量/复杂算法/窗口函数场景)。
- 最佳规范：
  1. 层级命名体系：严格沿袭 Bronze (原始区) -> Silver (清洗宽表区) -> Gold (业务可用黄金表区)。
  2. 高性能下推：优先在后台由 Apache Doris / PySpark 引擎作算子下推计算。避免在内存中拉取大对象。

### 环节 3: Verify (管道血缘与版本分支控制)
- 核心工具配属：Code Repositories (代码主战) + Pipeline Builder (血缘主战)。
- 最佳规范：
  1. Git Branching as Code：严禁直接在 main 生产分支修改。
  2. CI 校验流水线：任何 PR 合并前，必须强制运行静态 Schema 兼容性检查、血缘图无环检测（Acyclic Detection）以及至少 3 组单元测试。

### 环节 4: Schedule (调度生命周期与健康检测)
- 核心工具配属：Job Scheduler (调度托管) + Pipeline Builder / Data Health (质量监控)。
- 最佳规范：
  1. 警报拦截机制：在关键 Silver 与 Gold 节点必须强制挂载 Data Health Checks。空值率超出阈值时必须直接熔断（Skip Downstream Jobs），保护下游核心 Ontology 不被脏数据污染。
  2. SLA 时效延迟：对航空调度等高频数据，设置 15/30 分钟级超期警告。

### 环节 5: Publish (Ontology 映射绑定与全局发布)
- 核心工具配属：Ontology Manager + Contour / Workshop (消费)。
- 最佳规范：
  1. 一事实源 (Single Source of Truth)：任何业务核心指标（如“航班延误率”、“飞行员超时度”）严禁在应用前端通过 JavaScript重复拼写。必须通过 Code Repositories 统一注册为 Functions on Objects (对象函数)，保障指标全局口径一致。
  2. 行级安全性 (RLS)：映射成 Ontology 的对象必须无缝应用 Org-based 访问控制及 MAC 强制标记隔离。`;
                            navigator.clipboard.writeText(markdownText);
                            showToast('success', '全套金牌架构策划书已复制到剪贴板！可以直接粘贴到项目文档中。');
                          }}
                          className="flex items-center gap-1.5 px-3 py-1 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-xs font-bold transition-all shadow-sm cursor-pointer animate-pulse"
                        >
                          <LucideIcon name="Copy" size={12} />
                          <span>一键复制全套方案</span>
                        </button>
                      </div>

                      <div className="bg-slate-900 rounded-xl p-5 text-slate-300 font-mono text-[11px] leading-relaxed overflow-x-auto border border-slate-800 shadow-inner select-text select-all">
                        <pre className="whitespace-pre-wrap font-mono">
{`# 🔷 Palantir ECOS 「四开发工具完美融入五环节」顶层集成部署策划案

## 1. 顶层数据集成理念 (Top-Level Architecture Philosophy)
Palantir ECOS 的零信任一体化集成架构强调：“物理拉取轻量化，清洗编排标准化，分支协作代码化，质量健康熔断化，业务模型实体化”。通过将 Pipeline Builder、Code Repositories、Code Workbooks、Contour 四大核心工具完美配属到 Ingest（物理入湖）、Transform（逻辑清洗）、Verify（分支演练）、Schedule（质量监控）、Publish（Ontology实体映射） 五个标准生命周期环节，打通从“脏原始物理连接”到“高可用活性业务对象 (Active Objects)”的最后一公里。

---

## 2. 工具与环节完美融合矩阵 (The Synergy Matrix)

| 核心开发工具 | 环节 1: Ingest (物理源拉取) | 环节 2: Transform (逻辑清洗建模) | 环节 3: Verify (版本分支控制) | 环节 4: Schedule (质量时效双控) | 环节 5: Publish (Ontology实体映射) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Pipeline Builder**\\n*(低代码图形算子)* | **辅助协作**\\n支持拖拽导入原始物理表。 | **绝对主导 (Primary)**\\n流批一体无代码级联字段清洗。 | **深度主导 (Primary)**\\n可视化 DAG 血缘及开发分支热部署。 | **绝对主导 (Primary)**\\n图形化配置 Data Health Checks 并挂载。 | **绝对主导 (Primary)**\\n物理黄金宽表一键建立 Ontology 实体映射。 |
| **Code Repositories**\\n*(高代码工程仓库)* | **辅助支持**\\n高级 Spark Ingress 自定义驱动编写。 | **绝对主导 (Primary)**\\n百亿级巨型体量、高复杂度算子及特征计算。 | **绝对主导 (Primary)**\\nGit-First 分支冷演练、PR 自动化 CI 质检。 | **辅助协作**\\nYAML/Config 声明复杂任务协同分组。 | **绝对主导 (Primary)**\\n编写可重用的 Functions on Objects。 |
| **Code Workbooks**\\n*(交互式沙箱工作簿)*| **无直接关联 (N/A)**\\n纯探索产品，不写入物理主表。 | **辅助支持**\\n数据科学前中期多语种混写原型。 | **辅助协作**\\n单 Workbook 自带逻辑分支依赖推演。 | **辅助协同**\\n离线克隆脏数据，通过 PyPandas 排障。 | **辅助支持**\\n支持将机器学习模型发布为逻辑模型对象。 |
| **Contour**\\n*(亚秒分析下钻)* | **无直接关联 (N/A)**\\n无需关注底层物理数据库。 | **辅助协作**\\n通过交互式卡片拼装临时业务聚合表。 | **辅助协同**\\n追踪数据集宏观血缘，辅助质检合规。 | **辅助协助**\\n业务质检合规官的日常数据看板巡检。 | **绝对主导 (Primary)**\\n无需 SQL 极速探查 and 消费 Ontology 对象实体。 |

---

## 3. 五大环节的 golden standard (黄金落地规范)

### 环节 1: Ingest (物理源注册与数据拉取)
- 核心工具配属：Data Connection 托管，Pipeline Builder 作为消费者导入。
- 最佳规范：
  1. 所有物理连接凭证（明文密码、私钥）严禁硬编码。必须统一注入 ECOS Vault 或密钥管理中心，采取零信任 KMS 加密。
  2. 采用分布式 Magritte Agent 作为内网网关代理，支持多点断点续传及高并发限流。

### 环节 2: Transform (算子表达式与逻辑关联)
- 核心工具配属：Pipeline Builder (80% 过滤与关联场景) + Code Repositories (20% 巨型体量/复杂算法/窗口函数场景)。
- 最佳规范：
  1. 层级命名体系：严格沿袭 Bronze (原始区) -> Silver (清洗宽表区) -> Gold (业务可用黄金表区)。
  2. 高性能下推：优先在后台由 Apache Doris / PySpark 引擎作算子下推计算。避免在内存中拉取大对象。

### 环节 3: Verify (管道血缘与版本分支控制)
- 核心工具配属：Code Repositories (代码主战) + Pipeline Builder (血缘主战)。
- 最佳规范：
  1. Git Branching as Code：严禁直接在 main 生产分支修改。
  2. CI 校验流水线：任何 PR 合并前，必须强制运行静态 Schema 兼容性检查、血缘图无环检测（Acyclic Detection）以及至少 3 组单元测试。

### 环节 4: Schedule (调度生命周期与健康检测)
- 核心工具配属：Job Scheduler (调度托管) + Pipeline Builder / Data Health (质量监控)。
- 最佳规范：
  1. 警报拦截机制：在关键 Silver 与 Gold 节点必须强制挂载 Data Health Checks。空值率超出阈值时必须直接熔断（Skip Downstream Jobs），保护下游核心 Ontology 不被脏数据污染。
  2. SLA 时效延迟：对航空调度等高频数据，设置 15/30 分钟级超期警告。

### 环节 5: Publish (Ontology 映射绑定与全局发布)
- 核心工具配属：Ontology Manager + Contour / Workshop (消费)。
- 最佳规范：
  1. 一事实源 (Single Source of Truth)：任何业务核心指标（如“航班延误率”、“飞行员超时度”）严禁在应用前端通过 JavaScript重复拼写。必须通过 Code Repositories 统一注册为 Functions on Objects (对象函数)，保障指标全局口径一致。
  2. 行级安全性 (RLS)：映射成 Ontology 的对象必须无缝应用 Org-based 访问控制及 MAC 强制标记隔离。`}
                        </pre>
                      </div>
                    </div>

                    <div className="p-4 border-t border-slate-100 bg-slate-50 flex justify-end shrink-0 select-none">
                      <button
                        onClick={() => setShowExportModal(false)}
                        className="px-4 py-2 bg-slate-200 hover:bg-slate-300 text-slate-700 rounded-lg text-xs font-bold transition-all cursor-pointer"
                      >
                        关闭预览
                      </button>
                    </div>
                  </div>
                </div>
              )}

              {/* Grid 1: Four Core Tools in Palantir ECOS */}
              <div className="mb-6">
                <h3 className="text-xs font-bold text-slate-800 uppercase tracking-wider mb-3 flex items-center gap-2">
                  <LucideIcon name="Settings" size={14} className="text-blue-600" />
                  <span>一、ECOS 核心管道构建四大开发工具</span>
                </h3>
                
                <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                  {[
                    {
                      id: 'pipeline-builder',
                      title: 'Pipeline Builder (图形算子)',
                      desc: '无代码/低代码图形化编排界面。支持高性能计算，一键拖拽完成 Schema 解析、Casting、Joins 和内置函数注入，无需维护底层 Doris 配置。',
                      lang: '图形化表达式 + PB 算子集',
                      engine: 'Apache Doris / In-Memory 内存算子',
                      pros: '学习门槛极低，版本自动追踪，算子开箱即用',
                      cons: '对极端复杂的统计算法或第三方依赖包支持稍弱',
                      icon: 'Workflow',
                      color: 'border-blue-500 bg-blue-50/10 text-blue-700'
                    },
                    {
                      id: 'code-repositories',
                      title: 'Code Repositories (代码仓库)',
                      desc: '代码至上（Code-First）的高级开发仓库。基于内置 Git 分支协同、Pull Requests 评审以及多语言编译器。可直接编写复杂算子逻辑。',
                      lang: 'Doris SQL / Python / Java / TS',
                      engine: 'ECOS Doris Engine (Columnar / Vectorized)',
                      pros: '代码复用性高，支持多分支 Git 协同，可引用外部包',
                      cons: '需要一定的 SQL/Doris 语法基础，编译速度视资源而定',
                      icon: 'FileCode',
                      color: 'border-indigo-500 bg-indigo-50/10 text-indigo-700'
                    },
                    {
                      id: 'code-workbooks',
                      title: 'Code Workbooks (探索工作簿)',
                      desc: '沙箱交互式分析环境。支持 Python、R、SQL 混写，通过可视化分支图实时查看中间运行结果，多用于数据探查、统计分析及机器学习模型微调。',
                      lang: 'Python / R / SQL',
                      engine: 'Dynamic Interactive In-Memory Server',
                      pros: '交互反馈即时，自带丰富图表，非常适合原型设计与探索',
                      cons: '不推荐作为超大规模定时调度及线上 Ontology 同步的生产管道',
                      icon: 'BookOpen',
                      color: 'border-violet-500 bg-violet-50/10 text-violet-700'
                    },
                    {
                      id: 'contour',
                      title: 'Contour (分析看板路经)',
                      desc: '基于“分析板（Boards）”的数据过滤、汇总和快速分析工具。多用于临时对百亿级巨型数据集进行交互式下钻过滤并直接输出结果报表。',
                      lang: '可视化配置路经',
                      engine: 'ECOS Contour Engine (Presto-like)',
                      pros: '亚秒级百亿级下钻响应，对多维交互式漏斗分析极其优异',
                      cons: '无法导出为物理落湖数据集，无法直接做 Ontology 属性更新',
                      icon: 'Layers',
                      color: 'border-amber-500 bg-amber-50/10 text-amber-700'
                    }
                  ].map(tool => {
                    const isSelected = guideSelectedTool === tool.id;
                    return (
                      <button
                        key={tool.id}
                        onClick={() => setGuideSelectedTool(tool.id as any)}
                        className={`text-left p-4 rounded-xl border-2 transition-all flex flex-col gap-2 relative ${
                          isSelected
                            ? 'bg-white border-slate-900 shadow-md ring-1 ring-slate-950/20'
                            : 'bg-white border-slate-200/60 hover:border-slate-300 hover:shadow-2xs'
                        }`}
                      >
                        <div className="flex justify-between items-center w-full">
                          <div className="flex items-center gap-2">
                            <div className={`p-1.5 rounded-md ${tool.color}`}>
                              <LucideIcon name={tool.icon} size={14} />
                            </div>
                            <span className="text-xs font-extrabold text-slate-800">{tool.title}</span>
                          </div>
                          {isSelected && (
                            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 animate-ping" />
                          )}
                        </div>
                        <p className="text-[11px] text-slate-500 leading-relaxed font-sans mt-1">
                          {tool.desc}
                        </p>
                        
                        {isSelected && (
                          <div className="mt-2 pt-2 border-t border-slate-100 space-y-1.5 text-[10px] w-full bg-slate-50 p-2 rounded-lg font-mono">
                            <div>
                              <span className="text-slate-400 font-bold">🛠️ 支撑语言:</span>{' '}
                              <span className="text-slate-700">{tool.lang}</span>
                            </div>
                            <div>
                              <span className="text-slate-400 font-bold">🚀 物理引擎:</span>{' '}
                              <span className="text-slate-700">{tool.engine}</span>
                            </div>
                            <div className="text-emerald-700">
                              <span className="text-slate-400 font-bold">🟢 核心优势:</span> {tool.pros}
                            </div>
                            <div className="text-rose-700">
                              <span className="text-slate-400 font-bold">🔴 开发避坑:</span> {tool.cons}
                            </div>
                          </div>
                        )}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* 🆕 HIGH-FIDELITY INTERACTIVE PROTOTYPE CONTAINER */}
              <div className="mb-8 bg-white border border-slate-200 rounded-2xl shadow-sm overflow-hidden flex flex-col h-[650px]">
                <div className="bg-slate-950 text-white px-5 py-3 border-b border-slate-800 flex justify-between items-center shrink-0">
                  <div className="flex items-center gap-2">
                    <span className="h-2 w-2 rounded-full bg-amber-400 animate-pulse" />
                    <span className="text-xs font-black tracking-wide font-sans">
                      ECOS 核心开发工具 · 可交互实战演练：
                      <strong className="text-amber-400 font-extrabold ml-1.5 font-sans">
                        {guideSelectedTool === 'pipeline-builder' && 'Pipeline Builder (图形算子)'}
                        {guideSelectedTool === 'code-repositories' && 'Code Repositories (代码仓库)'}
                        {guideSelectedTool === 'code-workbooks' && 'Code Workbooks (探索分析工作簿)'}
                        {guideSelectedTool === 'contour' && 'Contour (交互式分析看板路径)'}
                      </strong>
                    </span>
                  </div>
                  <div className="bg-white/10 text-white text-[9px] font-bold px-2 py-0.5 rounded border border-white/10 font-mono">
                    PROTOTYPE ACTIVE
                  </div>
                </div>
                
                <div className="flex-1 overflow-hidden min-h-0 bg-slate-50">
                  {guideSelectedTool === 'pipeline-builder' && (
                    <PipelineBuilderPrototype 
                      onCommitToGit={handleCommitToGit}
                      onCompileComplete={setPipelineBuilderOutput}
                      showToast={showToast}
                    />
                  )}
                  {guideSelectedTool === 'code-repositories' && (
                    <CodeRepositoriesPrototype 
                      onCommitToGit={handleCommitToGit}
                      globalGitHistory={globalGitHistory}
                    />
                  )}
                  {guideSelectedTool === 'code-workbooks' && (
                    <CodeWorkbooksPrototype 
                      onCommitToGit={handleCommitToGit}
                    />
                  )}
                  {guideSelectedTool === 'contour' && (
                    <ContourPrototype 
                      onCommitToGit={handleCommitToGit}
                    />
                  )}
                </div>
              </div>

              {/* Grid 2: Operational Steps & Live Simulator */}
              <div>
                <h3 className="text-xs font-bold text-slate-800 uppercase tracking-wider mb-3 flex items-center gap-2">
                  <LucideIcon name="Activity" size={14} className="text-blue-600" />
                  <span>二、官方标准数据管道构建五步法 (可交互演练环境)</span>
                </h3>

                <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">
                  
                  {/* Left steps navigation timeline (cols 5) */}
                  <div className="lg:col-span-5 bg-white border border-slate-200 rounded-xl p-4 flex flex-col gap-3">
                    <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                      管道构建操作时序 (Pipeline Flow Sequence)
                    </span>
                    
                    <div className="space-y-2 flex-1">
                      {[
                        {
                          step: 1,
                          title: "步骤 1: 物理源注册与数据拉取 (Ingest)",
                          desc: "对接关系型 DB、S3、REST API 等多源协议，并将原始报文直接落入 DFS Bronze 原生存根层。",
                          icon: "Database",
                          color: "from-blue-500 to-indigo-600"
                        },
                        {
                          step: 2,
                          title: "步骤 2: 算子表达式与逻辑关联 (Transform)",
                          desc: "运用内置 PB 转换函数进行空值填充、转换截取与 Colocate Join，形成物理干净的 Silver 数据模型。",
                          icon: "Cpu",
                          color: "from-indigo-500 to-purple-600"
                        },
                        {
                          step: 3,
                          title: "步骤 3: 管道血缘与版本分支控制 (Lineage)",
                          desc: "利用独立 Git 开发分支提交 PR，在不损坏线上生产血缘依赖链条的前提下完成对代码和拓扑的冷演练部署。",
                          icon: "GitBranch",
                          color: "from-purple-500 to-pink-600"
                        },
                        {
                          step: 4,
                          title: "步骤 4: 调度生命周期与健康检测 (Schedule)",
                          desc: "配置按需触发或高频 cron 定时调度策略，并行注入行数、空值上限等健康规则，建立异常时效 SLA 自动熔断报警机制。",
                          icon: "Activity",
                          color: "from-pink-500 to-rose-600"
                        },
                        {
                          step: 5,
                          title: "步骤 5: Ontology 映射绑定与全局发布 (Publish)",
                          desc: "将清洗完的 Gold 物理表关联到业务 Ontology（航空航班、飞行员实名实体），通过主外键自动建立链接以供全栈消费。",
                          icon: "Layers",
                          color: "from-rose-500 to-amber-600"
                        }
                      ].map(s => {
                        const isActive = guideActiveStep === s.step;
                        const status = guideSimProgress[s.step];
                        return (
                          <button
                            key={s.step}
                            onClick={() => setGuideActiveStep(s.step)}
                            className={`w-full text-left p-3.5 rounded-lg border text-xs flex gap-3 items-start transition-all relative ${
                              isActive
                                ? 'bg-slate-900 border-slate-900 text-white shadow-md scale-101'
                                : 'bg-slate-50/50 border-slate-100 hover:bg-slate-50'
                            }`}
                          >
                            <div className={`p-2 rounded-lg shrink-0 flex items-center justify-center text-white bg-gradient-to-br ${s.color}`}>
                              <LucideIcon name={s.icon} size={14} />
                            </div>
                            <div className="flex-1 min-w-0">
                              <div className="flex justify-between items-center mb-1">
                                <span className={`font-extrabold ${isActive ? 'text-amber-400' : 'text-slate-800'}`}>
                                  {s.title}
                                </span>
                                {status === 'success' && (
                                  <span className="text-[10px] text-emerald-500 flex items-center gap-0.5 font-bold font-mono">
                                    <LucideIcon name="Check" size={10} />
                                    <span>PASS</span>
                                  </span>
                                )}
                                {status === 'running' && (
                                  <span className="h-3 w-3 border border-t-transparent border-blue-500 rounded-full animate-spin shrink-0" />
                                )}
                              </div>
                              <p className={`text-[10px] leading-relaxed ${isActive ? 'text-slate-300' : 'text-slate-500'}`}>
                                {s.desc}
                              </p>
                            </div>
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  {/* Right live operation and diagnostics (cols 7) */}
                  <div className="lg:col-span-7 flex flex-col gap-4">
                    
                    {/* Active Step Details */}
                    {(() => {
                      const stepData = [
                        {
                          step: 1,
                          title: "步骤 1: 物理异构源拉取规范",
                          definition: "通过在 Data Connection 模块中安全托管外部账号口令/密钥凭据，开启增量/全量抽取事务，并将未经逻辑污染的原始数据暂存进 DFS 的 Bronze 存储桶中，以达到物理源与逻辑加工层的强力解耦。",
                          coreTech: "ECOS JDBC Driver Gateway / Agent REST Webhooks / AWS IAM Ingress",
                          codeSample: `# PySpark 物理入湖脚本示例\nfrom pyspark.sql import functions as F\n\ndef ingest_raw_sources(spark_session, db_connector):\n    # 建立与 PostgreSQL 库的底层高并发安全连接通道\n    jdbc_df = spark_session.read.format("jdbc") \\\n        .option("url", db_connector.url) \\\n        .option("dbtable", "raw.aviation_flights_schedule") \\\n        .option("user", db_connector.username) \\\n        .option("password", db_connector.password) \\\n        .load()\n    \n    # 按日分区直接写盘\n    jdbc_df.write \\\n        .partitionBy("dt") \\\n        .format("parquet") \\\n        .mode("overwrite") \\\n        .save("/aviation/bronze/flights_raw/")`
                        },
                        {
                          step: 2,
                          title: "步骤 2: 表达式清洗与算子关联规范",
                          definition: "在 Pipeline Builder 中，用户引入多个表达式算子（Regex/Coalesce/Trim 等）实现对空属性的降噪填充和对字符串的格式化规范，并下推 Doris/Spark 行级或列级优化算子，保障在不占用过多 JVM 堆内存的前提下完成大宽表的物理组装与 Join 落地。",
                          coreTech: "Pipeline Builder Functions / Standard Spark SQL Colocate Joins / Memory Caching",
                          codeSample: `-- Apache Doris 级联清洗 SQL 表达规范\nCREATE MATERIALIZED VIEW mv_aviation_clean AS\nSELECT \n    f.flight_id,\n    UPPER(TRIM(f.flight_num)) AS clean_flight_num,\n    COALESCE(p.pilot_name, '未知飞行员') AS fill_pilot_name,\n    IF(f.delay_minutes > 15, '延误', '准点') AS flight_status\nFROM db_raw_flights f\nLEFT JOIN db_raw_pilots p ON f.pilot_id = p.pilot_id\nWHERE f.scheduled_departure >= '2026-01-01';`
                        },
                        {
                          step: 3,
                          title: "步骤 3: 管道血缘分支控制与 Git 协作",
                          definition: "ECOS 平台提供天然的代码即血缘（Lineage as Code）机制。当有新的加工逻辑需要调整时，严禁直接在生产环境 main 分支上修改。开发者应当在独立开发分支（如 dev/new-logic）下拉取血缘镜像，由 CI 系统校验通过并经 Peer 审核批准（PR approved）后方可安全合并发布。",
                          coreTech: "Branching as Code / Conflict Check / PR Peer Review / CI Autopilot",
                          codeSample: `# PySpark 单元自验逻辑\ndef test_filter_valid_flights():\n    mock_data = [\n        {"flight_id": "FL01", "dep_airport": "PEK", "arr_airport": "PVG"},\n        {"flight_id": "FL02", "dep_airport": None, "arr_airport": "SHA"}\n    ]\n    df = spark.createDataFrame(mock_data)\n    res = df.filter(F.col("dep_airport").isNotNull())\n    \n    # 验证测试: 过滤非空机场\n    assert res.count() == 1\n    print("CI 单元测试通过！[dep_airport] 校验无误。")`
                        },
                        {
                          step: 4,
                          title: "步骤 4: 自动化调度与健康监测监控规范",
                          definition: "数据管道上线后，由 ECOS Job Scheduler 进行自动化全链路调度。调度可通过时间触发（每天凌晨 2 点）或事件数据集更新触发。同时，必须针对关键输出表配置 Data Health Checks (DHC)，当遇到异常脏数据或 SLA 时效严重超时，触发自动回滚并向系统管理员派发飞书或邮件 SLA 警报。",
                          coreTech: "Trigger-based Jobs / Data Quality Checks (Null, Row Limits, Delay) / Slack & Email Alerts",
                          codeSample: `# ECOS 数据质量健康判定 YAML schema 配置样例\nhealthCheckRules:\n  - dataset: "/aviation/silver/ds_flights_clean"\n    rules:\n      - checkType: "ROW_COUNT_DELTA"\n        minLimit: 1000\n        deviationTolerance: 0.15\n      - checkType: "NULL_PERCENTAGE"\n        targetColumn: "pilot_id"\n        maxLimit: 0.01\n      - checkType: "FRESHNESS_DELAY"\n        maxDelayMinutes: 120`
                        },
                        {
                          step: 5,
                          title: "步骤 5: Ontology 实体映射建模与发布",
                          definition: "这是管道闭环的终极步骤。干净且已聚合的物理 Gold 表被加载到 Ontology Manager。将物理主键、列字段与定义好的物理属性映射关联（如：flight_id 映射到实体 ID，flight_num 映射为标题）。点击 Publish 按钮后，业务实体正式对平台所有业务 APP 可见，赋能行动决策中心。",
                          coreTech: "Ontology Manager Mapping / Primary Key Binding / Interface Sync / Active Object API",
                          codeSample: `// TypeScript 业务逻辑控制器 (Ontology-Native SDK)\nimport { Objects, Flight } from "@ecos/ontology-sdk";\n\nexport async function getActiveFlightByNum(flightNum: string): Promise<Flight | undefined> {\n    // 从经过 Ontology 模型注册的物理表直接执行类型安全的 OQL 检索\n    const flights = await Objects.Flight.search()\n        .filterBy(f => f.cleanFlightNum.exactMatch(flightNum))\n        .all();\n    \n    return flights[0];\n}`
                        }
                      ].find(st => st.step === guideActiveStep);

                      if (!stepData) return null;

                      const simStatus = guideSimProgress[stepData.step];
                      const currentLogs = guideLogs[stepData.step] || [];

                      return (
                        <div className="bg-white border border-slate-200 rounded-xl p-5 flex-1 flex flex-col justify-between overflow-hidden">
                          <div className="space-y-3 flex-1 overflow-y-auto">
                            <div className="flex justify-between items-center border-b border-slate-100 pb-2.5">
                              <h4 className="font-extrabold text-slate-800 text-xs flex items-center gap-1.5">
                                <span className="bg-blue-600 text-white rounded-full h-5 w-5 flex items-center justify-center font-mono text-[10px] font-bold">
                                  {stepData.step}
                                </span>
                                <span>{stepData.title}</span>
                              </h4>
                              <span className="text-[10px] bg-slate-100 text-slate-500 font-mono font-semibold px-2.5 py-1 rounded">
                                ECOS 架构设计
                              </span>
                            </div>

                            <div>
                              <span className="text-[10px] text-slate-400 font-bold uppercase block mb-1">功能描述与规范定义</span>
                              <p className="text-[11px] text-slate-600 leading-relaxed font-sans">
                                {stepData.definition}
                              </p>
                            </div>

                            <div className="grid grid-cols-2 gap-4 bg-slate-50 p-3 rounded-lg text-[10px]">
                              <div>
                                <span className="text-slate-400 font-bold block mb-0.5">⚙️ 核心支撑组件</span>
                                <span className="text-slate-700 font-medium font-sans">{stepData.coreTech}</span>
                              </div>
                              <div>
                                <span className="text-slate-400 font-bold block mb-0.5">📊 数据生命周期流转</span>
                                <span className="text-slate-700 font-medium font-mono">
                                  {stepData.step === 1 ? 'External DB -> Bronze File' :
                                   stepData.step === 2 ? 'Bronze File -> Silver DB Table' :
                                   stepData.step === 3 ? 'Silver Draft -> Production Branch' :
                                   stepData.step === 4 ? 'Scheduled Job -> Health Telemetry' :
                                   'Gold Dataset -> Ontology Objects'}
                                </span>
                              </div>
                            </div>

                            {/* Code Sample Block */}
                            <div>
                              <div className="flex justify-between items-center mb-1">
                                <span className="text-[10px] text-slate-400 font-bold uppercase">📂 ECOS 生产级底层代码参考</span>
                                <span className="text-[8px] font-mono text-indigo-500 font-bold">PySpark / SQL / TypeScript</span>
                              </div>
                              <pre className="p-3 bg-slate-950 text-indigo-300 rounded-lg text-[10px] font-mono leading-relaxed overflow-x-auto border border-slate-900 shadow-inner max-h-48 whitespace-pre select-text">
                                {stepData.codeSample}
                              </pre>
                            </div>
                          </div>

                          {/* Interactive simulation console panel */}
                          <div className="mt-4 pt-4 border-t border-slate-100 flex flex-col gap-3">
                            <div className="flex justify-between items-center">
                              <span className="text-[10px] text-slate-400 font-bold uppercase">
                                🔌 该步骤本地运算仿真沙箱
                              </span>
                              
                              <button
                                onClick={() => runGuideSimulation(stepData.step)}
                                disabled={simStatus === 'running'}
                                className={`px-4 py-1.5 text-xs font-bold rounded-lg transition-all cursor-pointer flex items-center gap-1.5 shadow-xs ${
                                  simStatus === 'running'
                                    ? 'bg-slate-200 text-slate-400 cursor-not-allowed'
                                    : 'bg-slate-900 hover:bg-slate-800 text-white'
                                }`}
                              >
                                {simStatus === 'running' ? (
                                  <>
                                    <span className="h-3 w-3 border-2 border-slate-400 border-t-transparent rounded-full animate-spin"></span>
                                    <span>正在运行物理运算模拟...</span>
                                  </>
                                ) : (
                                  <>
                                    <LucideIcon name="Play" size={12} className="text-emerald-400 animate-pulse" />
                                    <span>▶️ 触发步骤 {stepData.step} 物理仿真测试</span>
                                  </>
                                )}
                              </button>
                            </div>

                            {/* Logs Console Container */}
                            <div className="bg-slate-950 rounded-lg border border-slate-900 p-3 h-32 overflow-y-auto font-mono text-[10px] leading-relaxed text-slate-300 select-text">
                              {currentLogs.length === 0 ? (
                                <div className="text-slate-500 italic h-full flex items-center justify-center">
                                  点击上方按钮，仿真在内存与 Doris 计算层中模拟此管道步骤的实际下推流程...
                                </div>
                              ) : (
                                <div className="space-y-1">
                                  {currentLogs.map((log, lidx) => (
                                    <div key={lidx} className={
                                      log.includes('SUCCESS') || log.includes('🎉') || log.includes('通过') ? 'text-emerald-400 font-semibold' :
                                      log.includes('FAILED') || log.includes('异常') ? 'text-rose-400 font-semibold' :
                                      log.includes('[ECOS Pipeline Agent]') ? 'text-blue-400' : 'text-slate-300'
                                    }>
                                      {log}
                                    </div>
                                  ))}
                                </div>
                              )}
                            </div>
                          </div>
                        </div>
                      );
                    })()}
                  </div>

                </div>
              </div>

            </div>
        )}

        {activeTab === 'connections' && (
          <ConnectionsTab {...{connections, setConnections, showToast, handleCreateConnection, testingConnId, setTestingConnId, testingLogs, selectedConnId, setSelectedConnId, showAddConn, setShowAddConn, newConnName, setNewConnName, newConnType, setNewConnType, newConnHost, setNewConnHost, newConnPort, setNewConnPort, newConnUser, setNewConnUser, t} as any}
            setConnections={setConnections}
            showToast={showToast}
            handleCreateConnection={handleCreateConnection}
            testingConnId={testingConnId}
            setTestingConnId={setTestingConnId}
            testingLogs={testingLogs}
            selectedConnId={selectedConnId}
            setSelectedConnId={setSelectedConnId}
            showAddConn={showAddConn}
            setShowAddConn={setShowAddConn}
            newConnName={newConnName}
            setNewConnName={setNewConnName}
            newConnType={newConnType}
            setNewConnType={setNewConnType as any}
            newConnHost={newConnHost}
            setNewConnHost={setNewConnHost}
            newConnPort={newConnPort}
            setNewConnPort={setNewConnPort}
            newConnUser={newConnUser}
            setNewConnUser={setNewConnUser}
            t={t}
          />
        )}

        {activeTab === 'syncs' && (
          <SyncsTab {...{syncTasks, setSyncTasks, showToast, showAddSync, setShowAddSync, newSyncName, setNewSyncName, newSyncConn, setNewSyncConn, newSyncTable, setNewSyncTable, newSyncMode, setNewSyncMode, newSyncSched, setNewSyncSched, handleCreateSyncTask, selectedTaskId, setSelectedTaskId, connections, triggerSyncTask, t} as any} />
        )}

        {activeTab === 'pipelines' && (
          <PipelinesTab
            pipelines={pipelines}
            editingPipelineId={editingPipelineId}
            setEditingPipelineId={setEditingPipelineId}
            setPipelines={setPipelines}
            showToast={showToast}
            connections={connections}
            computeEngine={computeEngine}
            setComputeEngine={setComputeEngine as any}
            t={t}
          />
        )}

        {activeTab === 'health' && (
          <HealthTab {...{healthChecks, setHealthChecks, showToast, showAddCheck, setShowAddCheck, newCheckName, setNewCheckName, newCheckDs, setNewCheckDs, newCheckType, setNewCheckType, newCheckThreshold, setNewCheckThreshold, handleCreateHealthCheck, t, runSingleHealthCheck} as any} />
        )}

        {activeTab === 'lineage' && (
          <DataLineageTab
            connections={connections}
            pipelines={pipelines}
            showToast={showToast}
          />
        )}

        {activeTab === 'pipeline-builder' && (
          <PipelineBuilderTab
            connections={connections}
            showToast={showToast}
            pipelineBuilderOutput={pipelineBuilderOutput}
            setPipelineBuilderOutput={setPipelineBuilderOutput}
          />
        )}

        {activeTab === 'code-repositories' && (
          <CodeReposTab />
        )}

        {activeTab === 'code-workbooks' && (
          <CodeWorkbooksTab />
        )}

        {activeTab === 'contour' && (
          <ContourTab />
        )}

        {activeTab === 'sql-query' && (
          <SqlQueryConsole />
        )}

        {activeTab === 'engine-config' && (
          <DataEngineConfigPanel showToast={showToast} />
        )}


        </div>

      </div>




      {/* MODAL 1: ADD PHYSICAL CONNECTION */}

      {showAddConn && (

        <div className="absolute inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4 select-none">

          <div className="bg-white rounded-xl shadow-lg border border-slate-200 max-w-md w-full overflow-hidden flex flex-col">

            <div className="px-5 py-4 border-b border-slate-100 flex justify-between items-center bg-slate-50/50">

              <h3 className="text-xs font-bold text-slate-800 flex items-center gap-1.5">

                <LucideIcon name="Database" size={14} className="text-blue-500" />

                <span>{t("dw.txt.332103")}</span>

              </h3>

              <button onClick={() => setShowAddConn(false)} className="text-slate-400 hover:text-slate-600 p-1">

                <LucideIcon name="X" size={14} />

              </button>

            </div>

            

            <div className="p-5 space-y-4 text-xs">

              <div className="space-y-1">

                <label className="text-[10px] font-semibold text-slate-600 block">{t("dw.txt.58314a")}</label>

                <input

                  type="text"

                  placeholder="e.g. 生产派班主库_Read"

                  value={newConnName}

                  onChange={e => setNewConnName(e.target.value)}

                  className="w-full px-3 py-1.5 border border-slate-300 rounded focus:border-blue-500 focus:outline-hidden"

                />

              </div>



              <div className="grid grid-cols-2 gap-4">

                <div className="space-y-1">

                  <label className="text-[10px] font-semibold text-slate-600 block">{t("dw.txt.fe3609")}</label>

                  <select

                    value={newConnType}

                    onChange={e => setNewConnType(e.target.value as any)}

                    className="w-full px-2.5 py-1.5 border border-slate-300 rounded bg-white font-mono"

                  >

                    <option value="postgresql">PostgreSQL</option>

                    <option value="s3">Amazon S3</option>

                    <option value="rest_api">REST API</option>

                    <option value="sftp">SFTP</option>

                    <option value="sap">SAP ERP</option>

                  </select>

                </div>

                <div className="space-y-1">

                  <label className="text-[10px] font-semibold text-slate-600 block">{t("dw.txt.7adcd8")}</label>

                  <input

                    type="number"

                    value={newConnPort}

                    onChange={e => setNewConnPort(parseInt(e.target.value) || 0)}

                    className="w-full px-3 py-1.5 border border-slate-300 rounded focus:outline-hidden font-mono"

                  />

                </div>

              </div>



              <div className="space-y-1">

                <label className="text-[10px] font-semibold text-slate-600 block">{t("dw.txt.aaf147")}</label>

                <input

                  type="text"

                  placeholder="10.120.30.15"

                  value={newConnHost}

                  onChange={e => setNewConnHost(e.target.value)}

                  className="w-full px-3 py-1.5 border border-slate-300 rounded focus:outline-hidden font-mono"

                />

              </div>



              <div className="space-y-1">

                <label className="text-[10px] font-semibold text-slate-600 block">{t("dw.txt.f786e8")}</label>

                <input

                  type="text"

                  placeholder="db_read_only"

                  value={newConnUser}

                  onChange={e => setNewConnUser(e.target.value)}

                  className="w-full px-3 py-1.5 border border-slate-300 rounded focus:outline-hidden font-mono"

                />

              </div>

            </div>



            <div className="px-5 py-3 border-t border-slate-100 flex justify-end gap-2 bg-slate-50/50">

              <button

                onClick={() => setShowAddConn(false)}

                className="px-3 py-1.5 bg-white border border-slate-200 text-slate-600 hover:bg-slate-50 rounded text-xs transition-colors cursor-pointer"

              >

                取消

              </button>

              <button

                onClick={handleCreateConnection}

                className="px-3.5 py-1.5 bg-blue-600 hover:bg-blue-500 text-white font-semibold rounded text-xs transition-colors cursor-pointer"

              >

                保存并测试

              </button>

            </div>

          </div>

        </div>

      )}



      {/* MODAL 2: ADD SYNC TASK */}

      {showAddSync && (

        <div className="absolute inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4 select-none">

          <div className="bg-white rounded-xl shadow-lg border border-slate-200 max-w-md w-full overflow-hidden flex flex-col">

            <div className="px-5 py-4 border-b border-slate-100 flex justify-between items-center bg-slate-50/50">

              <h3 className="text-xs font-bold text-slate-800 flex items-center gap-1.5">

                <LucideIcon name="Import" size={14} className="text-violet-500" />

                <span>{t("dw.txt.ff8464")}</span>

              </h3>

              <button onClick={() => setShowAddSync(false)} className="text-slate-400 hover:text-slate-600 p-1">

                <LucideIcon name="X" size={14} />

              </button>

            </div>

            

            <div className="p-5 space-y-4 text-xs">

              <div className="space-y-1">

                <label className="text-[10px] font-semibold text-slate-600 block">{t("dw.txt.d32eec")}</label>

                <input

                  type="text"

                  placeholder="e.g. 气象气温指数每日快照同步"

                  value={newSyncName}

                  onChange={e => setNewSyncName(e.target.value)}

                  className="w-full px-3 py-1.5 border border-slate-300 rounded focus:border-blue-500 focus:outline-hidden"

                />

              </div>



              <div className="grid grid-cols-2 gap-4">

                <div className="space-y-1">

                  <label className="text-[10px] font-semibold text-slate-600 block">{t("dw.txt.74eeaf")}</label>

                  <select

                    value={newSyncConn}

                    onChange={e => setNewSyncConn(e.target.value)}

                    className="w-full px-2.5 py-1.5 border border-slate-300 rounded bg-white"

                  >

                    {connections.map(c => (

                      <option key={c.id} value={c.id}>{c.name}</option>

                    ))}

                  </select>

                </div>

                <div className="space-y-1">

                  <label className="text-[10px] font-semibold text-slate-600 block">{t("dw.txt.5ac0cc")}</label>

                  <input

                    type="text"

                    placeholder="weather_stats_table"

                    value={newSyncTable}

                    onChange={e => setNewSyncTable(e.target.value)}

                    className="w-full px-3 py-1.5 border border-slate-300 rounded focus:outline-hidden font-mono"

                  />

                </div>

              </div>



              <div className="grid grid-cols-2 gap-4">

                <div className="space-y-1">

                  <label className="text-[10px] font-semibold text-slate-600 block">{t("dw.txt.824885")}</label>

                  <select

                    value={newSyncMode}

                    onChange={e => setNewSyncMode(e.target.value as any)}

                    className="w-full px-2.5 py-1.5 border border-slate-300 rounded bg-white"

                  >

                    <option value="snapshot">{t("dw.txt.d5588c")}</option>

                    <option value="incremental">{t("dw.txt.831c7d")}</option>

                    <option value="append">{t("dw.txt.97a690")}</option>

                  </select>

                </div>

                <div className="space-y-1">

                  <label className="text-[10px] font-semibold text-slate-600 block">{t("dw.txt.813a1c")}</label>

                  <select

                    value={newSyncSched}

                    onChange={e => setNewSyncSched(e.target.value as any)}

                    className="w-full px-2.5 py-1.5 border border-slate-300 rounded bg-white"

                  >

                    <option value="manual">{t("dw.txt.004669")}</option>

                    <option value="hourly">{t("dw.txt.d4e181")}</option>

                    <option value="daily">{t("dw.txt.e9bc2c")}</option>

                    <option value="cron">{t("dw.txt.80aaef")}</option>

                  </select>

                </div>

              </div>

            </div>



            <div className="px-5 py-3 border-t border-slate-100 flex justify-end gap-2 bg-slate-50/50">

              <button

                onClick={() => setShowAddSync(false)}

                className="px-3 py-1.5 bg-white border border-slate-200 text-slate-600 hover:bg-slate-50 rounded text-xs transition-colors cursor-pointer"

              >

                取消

              </button>

              <button

                onClick={handleCreateSyncTask}

                className="px-3.5 py-1.5 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded text-xs transition-colors cursor-pointer"

              >

                配置并注册任务

              </button>

            </div>

          </div>

        </div>

      )}



      {/* MODAL 3: ADD HEALTH CHECK */}

      {showAddCheck && (

        <div className="absolute inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4 select-none">

          <div className="bg-white rounded-xl shadow-lg border border-slate-200 max-w-md w-full overflow-hidden flex flex-col">

            <div className="px-5 py-4 border-b border-slate-100 flex justify-between items-center bg-slate-50/50">

              <h3 className="text-xs font-bold text-slate-800 flex items-center gap-1.5">

                <LucideIcon name="ShieldAlert" size={14} className="text-amber-500" />

                <span>{t("dw.txt.91ab83")}</span>

              </h3>

              <button onClick={() => setShowAddCheck(false)} className="text-slate-400 hover:text-slate-600 p-1">

                <LucideIcon name="X" size={14} />

              </button>

            </div>

            

            <div className="p-5 space-y-4 text-xs">

              <div className="space-y-1">

                <label className="text-[10px] font-semibold text-slate-600 block">{t("dw.txt.f2e8f6")}</label>

                <input

                  type="text"

                  placeholder="e.g. 机队维修日志行数波动阈值"

                  value={newCheckName}

                  onChange={e => setNewCheckName(e.target.value)}

                  className="w-full px-3 py-1.5 border border-slate-300 rounded focus:border-blue-500 focus:outline-hidden"

                />

              </div>



              <div className="grid grid-cols-2 gap-4">

                <div className="space-y-1">

                  <label className="text-[10px] font-semibold text-slate-600 block">{t("dw.txt.52d7f4")}</label>

                  <select

                    value={newCheckDs}

                    onChange={e => setNewCheckDs(e.target.value)}

                    className="w-full px-2.5 py-1.5 border border-slate-300 rounded bg-white"

                  >

                    <option value="ds_flights_clean">ds_flights_clean</option>

                    <option value="ds_pilot_stats_clean">ds_pilot_stats_clean</option>

                    <option value="ds_sap_costs">ds_sap_costs</option>

                  </select>

                </div>

                <div className="space-y-1">

                  <label className="text-[10px] font-semibold text-slate-600 block">{t("dw.txt.b316a5")}</label>

                  <select

                    value={newCheckType}

                    onChange={e => setNewCheckType(e.target.value as any)}

                    className="w-full px-2.5 py-1.5 border border-slate-300 rounded bg-white"

                  >

                    <option value="row_count">{t("dw.txt.bca5fe")}</option>

                    <option value="null_check">{t("dw.txt.fb1d84")}</option>

                    <option value="freshness">{t("dw.txt.8be220")}</option>

                    <option value="schema_check">{t("dw.txt.c4a9c9")}</option>

                  </select>

                </div>

              </div>



              {newCheckType !== 'schema_check' && (

                <div className="space-y-1">

                  <label className="text-[10px] font-semibold text-slate-600 block">{t("dw.txt.2063a2")}</label>

                  <input

                    type="text"

                    placeholder={newCheckType === 'row_count' ? '1000' : newCheckType === 'null_check' ? '2.0' : '120'}

                    value={newCheckThreshold}

                    onChange={e => setNewCheckThreshold(e.target.value)}

                    className="w-full px-3 py-1.5 border border-slate-300 rounded focus:outline-hidden font-mono"

                  />

                  <span className="text-[10px] text-slate-400 mt-1 block">

                    {newCheckType === 'row_count' ? '指定最少应保障多少行数据。' :

                     newCheckType === 'null_check' ? '允许的最大空值占比比例 (例如 2.0 代表 2%)' : '数据产生与系统当前时间的最大可承受时差 (分钟)'}

                  </span>

                </div>

              )}

            </div>



            <div className="px-5 py-3 border-t border-slate-100 flex justify-end gap-2 bg-slate-50/50">

              <button

                onClick={() => setShowAddCheck(false)}

                className="px-3 py-1.5 bg-white border border-slate-200 text-slate-600 hover:bg-slate-50 rounded text-xs transition-colors cursor-pointer"

              >

                取消

              </button>

              <button

                onClick={handleCreateHealthCheck}

                className="px-3.5 py-1.5 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded text-xs transition-colors cursor-pointer"

              >

                保存规则

              </button>

            </div>

          </div>

        </div>

      )}



      {/* FLOATING DRAWER: EXTERNAL INTERFACES LIST */}

      {showExternalInterfaces && (

        <div className="absolute top-12 right-0 bottom-0 w-96 bg-slate-900 text-slate-100 border-l border-slate-800 shadow-2xl z-40 flex flex-col overflow-hidden select-none">

          <div className="px-5 py-4 border-b border-slate-800 flex justify-between items-center bg-slate-950/40 shrink-0">

            <h3 className="text-xs font-bold text-white flex items-center gap-2">

              <LucideIcon name="Layers" size={14} className="text-amber-500 animate-pulse" />

              <span>{t("dw.txt.c5dda0")}</span>

            </h3>

            <button onClick={() => setShowExternalInterfaces(false)} className="text-slate-400 hover:text-white p-1">

              <LucideIcon name="X" size={14} />

            </button>

          </div>



          <div className="flex-1 overflow-y-auto p-5 space-y-4">

            <p className="text-[11px] text-slate-400 leading-relaxed font-sans">

              以下是当前 ECOS 集成平台与外界各大航司物理系统、调度系统、AWS 云对象存储以及 ERP 财务系统的注册接口。

            </p>



            {connections.map(conn => (

              <div key={conn.id} className="p-3 bg-slate-950 rounded-lg border border-slate-800 text-xs space-y-2.5">

                <div className="flex justify-between items-center border-b border-slate-900 pb-1.5">

                  <span className="font-bold text-white font-mono">{conn.id}</span>

                  <span className={`text-[9px] font-mono px-1.5 rounded-full ${

                    conn.status === 'connected' ? 'bg-emerald-900/30 text-emerald-400' : 'bg-red-900/30 text-red-400'

                  }`}>

                    {conn.status.toUpperCase()}

                  </span>

                </div>



                <div className="space-y-1 text-[11px] text-slate-400">

                  <div>

                    <span className="text-slate-600 font-semibold uppercase text-[9px] block">{t("dw.txt.f274bd")}</span>

                    <span className="text-slate-200">{conn.name}</span>

                  </div>

                  <div>

                    <span className="text-slate-600 font-semibold uppercase text-[9px] block">{t("dw.txt.dec92b")}</span>

                    <span className="text-slate-300 font-mono">ECOS Connector v1.2 [{conn.type.toUpperCase()}]</span>

                  </div>

                  {conn.config.host && (

                    <div>

                      <span className="text-slate-600 font-semibold uppercase text-[9px] block">{t("dw.txt.15e5bb")}</span>

                      <span className="text-slate-300 font-mono">{conn.config.host}:{conn.config.port || 5432}</span>

                    </div>

                  )}

                  {conn.config.bucket && (

                    <div>

                      <span className="text-slate-600 font-semibold uppercase text-[9px] block">{t("dw.txt.f17556")}</span>

                      <span className="text-slate-300 font-mono break-all">{conn.config.bucket}</span>

                    </div>

                  )}

                  {conn.config.endpointUrl && (

                    <div>

                      <span className="text-slate-600 font-semibold uppercase text-[9px] block">{t("dw.txt.0d63d3")}</span>

                      <span className="text-slate-300 font-mono break-all">{conn.config.endpointUrl}</span>

                    </div>

                  )}

                </div>

              </div>

            ))}

          </div>



          <div className="p-4 bg-slate-950 border-t border-slate-800 text-[10px] text-slate-500 text-center select-none font-mono">

            Aviation Integration Gateway (Total: {connections.length} Endpoints)

          </div>

        </div>

      )}



    </div>

  );

}



// Standalone wrapper for full-page data workbench (used by route: data-workbench)

export function DataWorkbenchLayoutStandalone() {
  const { styles } = useTheme();

  return (

    <div className={`h-screen flex flex-col ${styles.appBg} ${styles.appText} font-sans`}>

      <DataWorkbenchLayout />

    </div>

  );

}
