1|# PMO指令：ceos_new 本体工作台替换 c2eos 本体工作台
2|
3|> **来源**: 肖总 | **日期**: 2026-07-04 | **工期**: 预计5天
4|> **铁律**: PMO唯一产出= commit hash + curl PASS/FAIL。不准产出分析文档。
5|
6|---
7|
8|## 0. 前置：了解 ceos_new 工程（PMO/ARCH/FE 全员）
9|
10|ceos_new 是一个 Palantir Foundry 风格的本体管理前端原型，位于 `/home/guorongxiao/ceos_new/`。
11|
12|**必须逐文件阅读并存入记忆的清单：**
13|
14|| 文件 | 用途 |
15||------|------|
16|| `src/types.ts` | 269行，Palantir对齐的类型系统：ObjectType/LinkType/ActionType/FunctionType/InterfaceType/SharedProperty/OntologyDomain/Dataset |
17|| `src/mockData.ts` | 688行，航空域mock数据：4个ObjectType+5个LinkType+3个ActionType+5个Dataset+2个Interface |
18|| `src/App.tsx` | 656行，统一状态管理+Save/Publish事务+CRUD操作+Ctrl+S快捷键 |
19|| `src/components/OntologyGraph.tsx` | 277行，可拖拽SVG关系图画布（替换目标） |
20|| `src/components/OverviewView.tsx` | 815行，总览页，内置审核日志+统计卡片 |
21|| `src/components/ObjectTypeView.tsx` | 对象类型详情编辑器（替换目标） |
22|| `src/components/LinkTypeView.tsx` | 链接类型编辑器（替换目标） |
23|| `src/components/ActionTypeView.tsx` | 操作类型编辑器（替换目标） |
24|| `src/components/FunctionTypeView.tsx` | 函数类型编辑器（替换目标） |
25|| `src/components/Sidebar.tsx` | 左栏：域筛选+实体树+搜索 |
26|| `src/components/AIPWorkbench/` | AI工作台（本期不涉及） |
27|
28|**架构要点**：
29|- 所有状态在 App.tsx 顶层，通过 props 下传，无外部状态库
30|- ObjectType 含 `mapping`（数据集→属性映射），`domainId`（域归属），`interfaces`
31|- LinkType 含 `cardinality`，`mapping.type`（foreign_key/join_table）
32|- ActionType 含 `validationRules`
33|- 保存=localStorage，发布=模拟1.5s延迟
34|
35|---
36|
37|## 1. 禁止清单
38|
39|1. **禁止** 改动 c2eos 路由结构——保持现有路由 `/ontology_workbench` 不变
40|2. **禁止** 删除 c2eos 现有后端API调用——替换UI但接入现API
41|3. **禁止** 引入新的 npm 依赖——ceos_new 已有的依赖 c2eos 都有
42|4. **禁止** 改动后端 databridge-v2 的 Controller 签名——只新增端点
43|5. **禁止** 产出分析文档/评估报告——只产出代码
44|
45|---
46|
47|## 2. Phase 1: 类型系统对齐（FE，0.5天）
48|
49|ceos_new 的类型系统比 c2eos 丰富。目标是让 c2eos 的 API 响应能映射到 ceos_new 的组件。
50|
51|### T1.1: 补齐 c2eos 缺失的类型定义（2h，FE）
52|
53|**目标**: c2eos 新增 `src/types/ontology.ts`，含 ObjectType/LinkType/ActionType/FunctionType/InterfaceType/OntologyDomain 接口，对齐 ceos_new 但保留后端 API 兼容。
54|
55|**改文件**: `/home/guorongxiao/c2eos/src/types/ontology.ts`（新建）
56|
57|**要求**:
58|- 从 ceos_new `src/types.ts` 复制接口定义
59|- ObjectType.mapping 改为可选（`mapping?:`），因为后端不一定返回
60|- 所有 `mockData` 相关 import 去掉
61|
62|**验收**:
63|```bash
64|cd /home/guorongxiao/c2eos && npx tsc --noEmit 2>&1 | grep -c "ontology"  # 期望: 0 errors
65|```
66|
67|---
68|
69|## 3. Phase 2: OntologyGraph 替换 EntityGraph（FE，1天）
70|
71|### T2.1: 移植 OntologyGraph 组件（3h，FE）
72|
73|**目标**: 用 ceos_new 的 `OntologyGraph.tsx`（277行，可拖拽）替换 c2eos 的 `EntityGraph.tsx`（101行，静态网格）。
74|
75|**改文件**: 
76|- `/home/guorongxiao/c2eos/src/components/OntologyGraph.tsx`（新建）
77|- `/home/guorongxiao/c2eos/src/pages/OntologyDesigner/EntityGraph.tsx`（修改为重新导出）
78|
79|**做法**:
80|- 复制 ceos_new `/home/guorongxiao/ceos_new/src/components/OntologyGraph.tsx` → c2eos
81|- 调整 import 路径（types 从 `../types/ontology` 导入，LucideIcon 保持 c2eos 现有方式）
82|- EntityGraph.tsx 改为 `export { OntologyGraph as default } from '../../components/OntologyGraph'`
83|
84|**验收**:
85|```bash
86|cd /home/guorongxiao/c2eos && npx tsc --noEmit 2>&1 | grep -i "ontolog\|entitygraph"  # 期望: 0 errors
87|```
88|
89|### T2.2: 调整 OntologyDesigner 使用新图组件（1h，FE）
90|
91|**目标**: OntologyDesigner.tsx 传递给 EntityGraph 的 props 对齐 OntologyGraph。
92|
93|**改文件**: `/home/guorongxiao/c2eos/src/pages/OntologyDesigner.tsx`
94|
95|**要求**:
96|- 传入 `onSelectNode` + `onSelectEdge` 回调（替代现 `onSelectEntity`）
97|- 节点数据结构适配 Node 接口（id/label/icon/color/x/y/propertiesCount）
98|- 边数据结构适配 Edge 接口（id/source/target/label/cardinality）
99|
100|**验收**:
101|```bash
102|# 编译通过
103|cd /home/guorongxiao/c2eos && npx tsc --noEmit 2>&1 | grep -c "error"  # 期望: 0
104|```
105|
106|---
107|
108|## 4. Phase 3: 替换本体工作台页面（FE，1.5天）
109|
110|### T3.1: 移植 ObjectTypeView / LinkTypeView / ActionTypeView / FunctionTypeView（4h，FE）
111|
112|**目标**: ceos_new 的详情编辑器组件移植到 c2eos，对接现有后端 API。
113|
114|**改文件**（新建4个）:
115|- `/home/guorongxiao/c2eos/src/pages/ontology/ObjectTypeDetail.tsx`
116|- `/home/guorongxiao/c2eos/src/pages/ontology/LinkTypeDetail.tsx`
117|- `/home/guorongxiao/c2eos/src/pages/ontology/ActionTypeDetail.tsx`
118|- `/home/guorongxiao/c2eos/src/pages/ontology/FunctionTypeDetail.tsx`
119|
120|**源文件**:
121|- `/home/guorongxiao/ceos_new/src/components/ObjectTypeView.tsx`
122|- `/home/guorongxiao/ceos_new/src/components/LinkTypeView.tsx`
123|- `/home/guorongxiao/ceos_new/src/components/ActionTypeView.tsx`
124|- `/home/guorongxiao/ceos_new/src/components/FunctionTypeView.tsx`
125|
126|**要求**:
127|- 替换 mockData → 后端 API 调用（用 c2eos 现有 `api.ts` 的 fetch 函数）
128|- 保留编辑/删除/新建的交互逻辑
129|- mapping 区域的 Dataset 下拉选项从后端加载
130|- 属性CRUD对接现有 `/api/v1/ontologies/entities/{id}/properties` 端点
131|
132|**验收**:
133|```bash
134|cd /home/guorongxiao/c2eos && npx tsc --noEmit 2>&1 | grep "ontology/"  # 期望: 0 errors
135|```
136|
137|### T3.2: 改造 OntologyWorkbenchLayout 支持新视图（2h，FE）
138|
139|**目标**: 把现有 OntologyWorkbenchLayout 的 tab 切换连到新组件。
140|
141|**改文件**: `/home/guorongxiao/c2eos/src/pages/OntologyWorkbenchLayout.tsx`
142|
143|**要求**:
144|- 左侧 Sidebar: 替换为 ceos_new 风格的域筛选+实体树（从后端加载域和实体列表）
145|- 中间: 概述tab 显示 OntologyGraph + 统计卡片
146|- Object/Link/Action/Function tab 分别路由到 T3.1 的新组件
147|- 保留 DomainDesignerView 的入口
148|
149|**验收**: 
150|```bash
151|# 启动 dev server 后手动验证（curl 不行，这是前端页面）
152|cd /home/guorongxiao/c2eos && npx vite --port=3000 &
153|sleep 3
154|curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/ontology_workbench  # 期望: 200
155|```
156|
157|---
158|
159|## 5. Phase 4: 后端服务对接（BE，1天）
160|
161|### T4.1: 新增 Ontology Domain API（2h，BE）
162|
163|**目标**: 后端新增域管理端点，支持 ceos_new 风格的域CRUD。
164|
165|**改文件**: `/home/guorongxiao/databridge-v2/databridge-sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/controller/OntologyDomainController.java`（新建）
166|
167|**端点**:
168|```java
169|GET    /api/v1/ontology/domains          → 域列表
170|POST   /api/v1/ontology/domains          → 创建域
171|PUT    /api/v1/ontology/domains/{id}     → 更新域
172|DELETE /api/v1/ontology/domains/{id}     → 删除域
173|PUT    /api/v1/ontology/objects/{id}/domain → 修改对象归属域
174|```
175|
176|**数据库**: 新建表 `ecos_ontology_domain`(id, display_name, description, color, created_at)
177|
178|**验收**:
179|```bash
180|# 获取token（文件法避shell转义，JWT含特殊字符）
181|curl -s -X POST http://localhost:8080/api/v1/auth/login \
182|  -H "Content-Type: application/json" \
183|  -d '{"username":"admin","password":"Admin@123"}' \
184|  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])" \
185|  > /tmp/ecos_token.txt
# 步骤2: 生成auth header（Python方法避免shell转义）
python3 -c "t=open('/tmp/ecos_token.txt').read().strip();open('/tmp/auth_header.txt','w').write('Authorization: Bearer ' + t)"
187|
188|# 创建域
189|curl -s -H @/tmp/auth_header.txt -X POST http://localhost:8080/api/v1/ontology/domains \
190|  -H "Content-Type: application/json" \
191|  -d '{"displayName":"资产域","description":"asset domain","color":"blue"}' \
192|  | python3 -c "import sys,json;d=json.load(sys.stdin);print('PASS' if d.get('code')==200 else 'FAIL')"
193|```
194|
195|### T4.2: ObjectType 新增 mapping + domainId 字段（2h，BE）
196|
197|**目标**: 后端 ObjectType API 返回 mapping 和 domainId 字段。
198|
199|**改文件**: 
200|- Controller: 搜索现有 `ObjectController.java`，确认位置后扩展响应
201|- Service: 查询时 JOIN `ecos_ontology_domain` 获取 domainId
202|- DB migration: `ALTER TABLE ecos_object_def ADD COLUMN domain_id VARCHAR(64); ADD COLUMN dataset_id VARCHAR(64);`
203|
204|**验收**:
205|```bash
206|curl -s -H @/tmp/auth_header.txt http://localhost:8080/api/v1/ontology/objects | python3 -c "
207|import sys,json
208|data=json.load(sys.stdin)['data']
209|has_mapping = all('mapping' in o for o in data.get('records',data) if isinstance(data,dict))
210|has_domain = all('domainId' in o for o in data.get('records',data) if isinstance(data,dict))
211|print('PASS' if has_mapping and has_domain else 'FAIL')
212|"
213|```
214|
215|### T4.3: ActionType 新增 validationRules 字段（1h，BE）
216|
217|**目标**: ActionType 响应含 validationRules。
218|
219|**改文件**: ActionController + ActionService + migration SQL
220|
221|**验收**:
222|```bash
223|curl -s -H @/tmp/auth_header.txt http://localhost:8080/api/v1/ontology/actions | python3 -c "
224|import sys,json
225|data=json.load(sys.stdin)['data']
226|records=data.get('records',data) if isinstance(data,dict) else data
227|print('PASS' if records and 'validationRules' in records[0] else 'FAIL')
228|"
229|```
230|
231|---
232|
233|## 6. Phase 5: 精修复 + QA（FE+QA，1天）
234|
235|### T5.1: 精修复八步法（FE，4h）
236|
237|**目标**: 对替换后的本体工作台执行模块精修。
238|
239|**范围**: `/ontology_workbench` 下所有页面
240|
241|**按 a-h 执行**:
242|- a. 页面加载无白屏无控制台报错
243|- b. 对象/链接/操作/函数的 CRUD 全部可用
244|- c. 样式一致、国际化无 key 裸奔
245|- d. 域下拉/属性类型下拉等配置项从后端动态加载
246|- e. 数据字典（对象类型、链接基数、操作类型等）正确
247|- f. 域筛选→实体树→详情编辑器 联动正常
248|- g. OntologyGraph 拖拽流畅、点击跳转正常
249|- h. 种子数据可见（至少1个域+3个对象+2个链接）
250|
251|**验收**:
252|```bash
253|cd /home/guorongxiao/c2eos && npx tsc --noEmit 2>&1 | tail -5
254|# 期望: "Found 0 errors"
255|```
256|
257|### T5.2: QA 全链路验证（QA，4h）
258|
259|**目标**: QA 按契约逐条验证，不通过打回。
260|
261|**验证清单**:
262|
263|| # | 测试场景 | curl/操作 | 期望 |
264||---|---------|----------|------|
265|| 1 | 页面加载 | `curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/ontology_workbench` | 200 |
266|| 2 | 域列表加载 | `curl -s -H @/tmp/auth_header.txt http://localhost:8080/api/v1/ontology/domains` | code=200, data[] |
267|| 3 | 对象列表 | `curl -s -H @/tmp/auth_header.txt http://localhost:8080/api/v1/ontology/objects` | code=200, data含mapping+domainId |
268|| 4 | 创建对象 | POST 新建对象 → 列表中出现新对象 | 201, 列表+1 |
269|| 5 | 编辑对象属性 | PUT 更新对象属性 → 详情页刷新可见 | 200 |
270|| 6 | 删除对象 | DELETE → 列表-1，关联链接自动清理 | 204 |
271|| 7 | 创建链接 | POST foreign_key链接 → 图上线出现 | 201 |
272|| 8 | 创建join_table链接 | POST M:N链接 → 成功 | 201 |
273|| 9 | OntologyGraph 拖拽 | 浏览器手动拖节点→松开到新位置 | 节点位置更新 |
274|| 10 | OntologyGraph 点击跳转 | 浏览器点节点→右侧详情切换 | 详情正确 |
275|| 11 | 域筛选 | 选一个域→实体树仅显示该域对象 | 过滤正确 |
276|| 12 | 操作CRUD | 创建/编辑/删除 ActionType | 全部200 |
277|| 13 | 编译零错误 | `npx tsc --noEmit` | 0 errors |
278|
279|**QA判定**: 全部13项 PASS → 合并指令，汇报肖总。任何 FAIL → 打回 FE/BE，标注失败项编号。
280|
281|---
282|
283|## 7. 执行顺序
284|
285|```
286|T1.1 (类型系统)
287|  ↓
288|T2.1 → T2.2 (图谱组件)
289|  ↓
290|T3.1 (详情组件，可并行3个) + T4.1 (后端域API，可并行)
291|  ↓
292|T3.2 (布局改造) + T4.2 → T4.3 (后端扩展)
293|  ↓
294|T5.1 (精修复)
295|  ↓
296|T5.2 (QA验证)
297|```
298|
299|**可并行**：T3.1 的4个详情组件之间 | T3.1与T4.1 | T4.2与T4.3
300|
301|---
302|
303|## 8. 交付检查清单
304|
305|| Task | 负责人 | 交付物 | 验收方式 |
306||------|--------|--------|----------|
307|| T1.1 | FE | types/ontology.ts | tsc |
308|| T2.1 | FE | components/OntologyGraph.tsx | tsc |
309|| T2.2 | FE | OntologyDesigner.tsx | tsc |
310|| T3.1 | FE | 4个详情组件 | tsc |
311|| T3.2 | FE | OntologyWorkbenchLayout.tsx | curl 200 |
312|| T4.1 | BE | OntologyDomainController.java | curl PASS |
313|| T4.2 | BE | ObjectController扩展 | curl PASS |
314|| T4.3 | BE | ActionController扩展 | curl PASS |
315|| T5.1 | FE | 精修复后代码 | tsc 0 errors |
316|| T5.2 | QA | 13项验证结果 | 全部PASS |
317|
318|---
319|
320|## 一句话给PMO
321|
322|**把 ceos_new 的本体工作台UI搬进 c2eos，接上现有后端API，QA过关。13项验收，一个FAIL就打回。**
323|