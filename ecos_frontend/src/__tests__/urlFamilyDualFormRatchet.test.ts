/**
 * urlFamilyDualFormRatchet.test.ts — 「同一 URL 族的第二生产方」双形态棘轮门 (#143/B5-19)
 *
 * 收口缺陷 **D-B5-40**（差距台账 §13.2.4 C1-C6 注 ㉘）：仓内「出现第二处硬编码字面量即违例」
 * 型单源判据普遍只认引号形态（`["'`]/api…`），对 C1 派生态 `` `${API_BASE}/…` `` 不可见。
 * C1 基址单源每扩一批域，这类腿就静默变薄：建门批（#143/B5-19）实跑读出 680 个生产文件、
 * 258 个 URL 族、30 个族存在 >1 生产方，而**只认裸字面量的口径当时只能看到 9 个** ⇒ 21 个重复族
 * 对旧判据完全隐形。#143/B5-20 复跑 = 30/6/24（该批把知识服务域 32 处裸面搬成派生态，
 * dupBoth 不减而裸形态口径又瞎三族）。#143/B5-21 复跑 = 680 文件 / 258 族 / 裸面 87 / 派生面 247
 * （两面之和 **334 恒定**，纯转换只挪点位）/ dupBoth 30 / dupRawOnly **5** / blind **25**：
 * 该批把 data-workbench/api 域 26 处裸面派生化，`/v1/engine/data/pipeline/runs/{}` 一族只剩
 * `pipeline-editor/PipelineExecutionMonitor.tsx` 一个裸面属主 ⇒ 又掉出旧判据视野。
 * #143/B5-22 复跑 = 680 / 258 / 裸面 **71** / 派生面 **263**（和仍 334）/ dupBoth 30 / dupRawOnly 5 /
 * blind 25 —— 三个 dup 数**未随本批再动**：本批把 data-workbench 页面域 16 处裸面派生化，涉及的两族
 * （`…/pipeline/runs/{}`、`/v1/engine/data/settings`）的另一属主在改前就已是纯派生态（各 raw 0 / der 1，
 * settings 另一属主 EngineMonitor.tsx 系 #143/B5-11g 已派生）⇒ 旧判据视野本来就不含这两族，无族可掉。
 * 登记此归因以防「dupRawOnly 必随裸面总数单调下降」的误推断：它数的是**族内 ≥2 裸面属主**，不是裸面条数。
 * #143/B5-23 复跑 = 680 / 258 / 裸面 **54** / 派生面 **280**（和仍 334）/ dupBoth 30 / dupRawOnly **1** /
 * blind **29**：本批把场景域页面 8 文件 17 处裸面派生化，**一次掉出旧判据视野四族**——
 * `/v1/cognitive/beliefs?domain={}`（OverrideTab + ReplayTab 两属主全转）、`/v1/workspace/scenarios`
 * （ScenarioManagementView + workbench/index 全转，第三属主 NewScenarioModal.tsx 改前即 r0/d1 纯派生）、
 * `/v1/workspace/scenarios/{}`（ScenarioManagementView + [scenarioId]/index 全转）、
 * `/v1/ontology/proposals`（ScenarioManagementView 转后只剩 aiworkbench/api.ts 一个裸面属主）。
 * 唯一幸存的裸面重复族 = `/v1/ecos/ontologies`（OntologyDomainPanel 2 处 + BusinessWorkbenchLayout 1 处，
 * 两文件均在 api-base 基线内、属后续批）。dupRawOnly 5→1 正是上一批登记的机制的反向实证：
 * 族内裸面属主数而非条数决定可见性，一批可以只挪 17 条却抹掉 4 个族的旧判据可见性。
 * #143/B5-24 复跑 = 680 / 258 / 裸面 **46** / 派生面 **288**（和仍 334）/ dupBoth 30 / dupRawOnly **0** /
 * blind **30**：本批把 AI/工作台散点域 4 文件 11 处裸面派生化，其中 `/v1/ecos/ontologies` 那一族的
 * 两个裸面属主（OntologyDomainPanel 2 处 + BusinessWorkbenchLayout 1 处）**同批全转** ⇒ 旧判据视野
 * 里的最后一族消失，**dupRawOnly 面归零、盲区 30/30**。这不是好消息而是该缺陷的完成态：D-B5-40 登记的
 * 「只认引号形态的判据对派生态不可见」至此对全部 30 个重复族成立——它们在生产代码里仍在双写，
 * 但 12 个引号形态门**一族都看不见**。裸面只剩 46 点且每族至多一个裸面属主，即旧口径的重复面已被
 * C1 进度清空；自此本门是这条腿的唯一守卫，后继批的 dupBoth 减才代表存量债真收敛（不是旧判据变瞎）。
 * 三个 pin 随批更新，读的是当批实测而非建门批快照。
 * #143/B5-25 复跑 = 680 / 258 / 裸面 **19** / 派生面 **315**（和仍 334）/ dupBoth 30 / dupRawOnly **0** /
 * blind **30**：`pages/aiworkbench/api.ts` 单文件 27 处裸面派生化（46→19 的差额即这 27 点），
 * 三个 dup 相关 pin 一个没动——这正是 B5-23/24 两批登记的机制的又一次实证：**点数从裸面搬到派生面
 * 只改变旧判据的可见度，不改变重复族的数量**（dupRawOnly 早已归零，本批把 `/v1/ontology/proposals`
 * 等几族的最后裸面属主转尽，也只是让"本来就已看不见"的面更空）。所以本批的绿灯**不能**读成
 * "存量双写债少了 27 条"——族面 258、dupBoth 30 都原地不动，债的收敛只可能由 dupBoth 下降来记账。
 * 后继批若发现 dupRawOnly/blind 又动起来，先查是不是扫描根或判据变了，而不是当成进度。
 * #143/B5-26 复跑 = 680 / 258 / 裸面 **0** / 派生面 **334**（和仍 334）/ dupBoth 30 / dupRawOnly **0** /
 * blind **30**：C1 基址单源末批把残余散点 15 文件 21 处派生化，其中 19 处落在 `/v1/` 族面 ⇒
 * **prod 面裸形态站点第一次归零**（`/api/graphql`、`/api/telemetry/…` 两枚无版本段，从来不在族面口径内）。
 * 这带来的不是债收敛（族面 258、dupBoth 30 第三次原地不动，与 B5-25 同因），而是**本门自身的形状变了**：
 * 「裸形态分支必须在扫」不能再拿 prod 面 `rawSites > 0` 当证据（0 命中与判据失灵同形），故把 prod 面
 * 钉成 0（再写裸面即红），裸分支活性下沉到合成 fixture（单/双引号各一条）承证。自此 D-B5-40 的
 * 「旧判据面」在生产代码里是**全域**看不见，不再只是「部分族看不见」——本门是族面重复的唯一守卫。
 * #145/B6-1 复跑 = 680 / 258 / 裸面 **0** / 派生面 **328** / dupBoth **29** / dupRawOnly **0** /
 * blind **29**：B6 存量债首批收口 `/v1/knowledge` 一族——改前 7 个生产方各自写一份
 * `` `${API_BASE}/v1/knowledge` `` 基址常量（`api/knowledgeClient.ts` + `pages/knowledge/services/`
 * 六文件），现由 `api/knowledgeClient.ts` 的**导出** `KNOWLEDGE_BASE` 单源持有，其余六文件改成
 * `import { KNOWLEDGE_BASE }`。消费方文件里从此没有该族的 URL 体 ⇒ 属主 7→1、族退出重复面、
 * BASELINE 同批剪除。两点形状须记清，防止后继批误读：
 *   ① **dupBoth 第一次真实下降**（30→29，欠 B5-21~26 六批一直原地不动的账）。下降的因是**生产方
 *      个数**变了，不是形态变了——与 B5-25「点数搬家≠债收敛」互为正反证；记账只认 dupBoth，
 *      不认派生态点数（本批 derSites 334→328 的 6 点下降是收口的**副产物**，不是进度本身）。
 *   ② 单源化会让**该族之下的子路径腿对族面口径隐形**：`${KNOWLEDGE_BASE}/assets` 不含 `/v1/` 体，
 *      `familiesOf` 吃不到。本批刻意**只**把 6 枚基址声明换成 import，各文件里 `${API_BASE}/v1/knowledge/…`
 *      的具体子路径腿（graph.ts 2 处、overview.ts 3 处、wiki.ts 6 处、knowledgeClient.ts 3 处）**保持原样**，
 *      故族面 258 一点未动。后继 B6 批若要连子路径一起合流，必须预期族面会降，且那降是可见性变化
 *      而非新增重复——不得反过来当成"族面变小=债少了"。
 * #145/B6-2 复跑 = 680 / **257** / 裸面 **0** / 派生面 **323** / dupBoth **27** / dupRawOnly **0** /
 * blind **27**：存量债第二批收口 **两族一对**——`/v1/workspace/scenarios`（3 属主）与
 * `/v1/workspace/scenarios/{}`（2 属主），五文件合一为 `services/scenarioActionClient.ts` 导出的
 * `WORKSPACE_SCENARIOS_BASE` 单源，四页面改成 import。本批与 B6-1 有两处形状差，都必须记清：
 *   ① **族面第一次下降**（258→257）：`/v1/workspace/scenarios/{}` 的两个属主（ScenarioManagementView +
 *      `[scenarioId]/index`）是本批**唯一**写出该族体的人，收口后该族在生产面一条构造点都不剩 ⇒
 *      族从 `familiesOf` 索引里整体消失。这正是 B6-1 文件头 ② 预告过的「连子路径一起合流则族面必降」
 *      的兑现，降的是**可见性**，不是新增重复；`dupBoth` 的账仍只按属主数记（29→27 即两族退出）。
 *   ② derSites 328→323 的 **5 点净差额 = 删 6 增 1**：改前 6 条 `` `${API_BASE}/v1/workspace/scenarios…` ``
 *      （两页面根腿 3+1+1、详情腿 1）被单源取代，改后只剩属主的 1 条基址声明。同 B6-1：点数是副产物，
 *      不记进度。
 * 收口的 9 条腿是否一条不少、面值是否一字未改，**不在本门证明范围内**（本门对隐形腿无能为力），
 * 由同批新增的 `b6WorkspaceScenariosSingleSource.spec.ts` 用常量解析器按 HEAD 快照守恒承证。
 * #145/B6-3 复跑 = 680 / **256** / 裸面 **0** / 派生面 **317** / dupBoth **24** / dupRawOnly **0** /
 * blind **24**：存量债第三批收口 **AIP 域三族同一对生产方**——`/v1/aip/agents`（2 属主）、
 * `/v1/aip/agents/{}`（2 属主）、`/v1/aip/audit-logs`（2 属主）全部由
 * `pages/aiworkbench/api.ts` 与 `services/aiworkbenchApi.ts` 各写一份，现收口为该页 api 层
 * **导出**的 `AIP_AGENTS_BASE` + `AIP_AUDIT_LOGS_BASE` 双常量，服务层改为 import。三处形状须记清：
 *   ① **属主方向由 import 图决定，不是挑好看的**：`services/aiworkbenchApi.ts` 本来就 import
 *      `pages/aiworkbench/api`（既有四个 fetch 函数），反向放常量会成环 ⇒ 本批单源落在**页面 api 层**。
 *      B6-1/B6-2 的「单源在 api/services 层」不是普适规则，后继批先查 import 方向再定属主。
 *   ② **族面第二次下降**（257→256）：`/v1/aip/agents/{}` 两属主全退出族体书写 ⇒ 族从索引整体消失；
 *      derSites 323→317 = **删 8 枚派生点（3+5）增 2 枚属主声明**。两者皆副产物，账仍只记 dupBoth 与属主。
 *   ③ **同根子路径族刻意不并进常量**：`/v1/aip/agents/templates`、`/v1/aip/agents/instantiate` 各只有
 *      页面 api 层一个生产方（不在重复面），按 #145/B6-1 口径保持 `${API_BASE}` 原样以保住族面可见性；
 *      并进去会让两族对棘轮隐形而**不减少任何债**。此判断由新门反向钉死（子路径腿仍在原位可扫）。
 * 收口的 8 条腿面值守恒不在本门证明范围内，由同批新增的 `b6AipFamilySingleSource.spec.ts` 承证。
 * #145/B6-4 复跑 = 680 / **256** / 裸面 **0** / 派生面 **312** / dupBoth **22** / dupRawOnly **0** /
 * blind **22**：存量债第四批收口 **本体域两族各三属主**——`/v1/ontology/mappings`（3 属主：
 * `services/ontology/ontologyMappingApi` + `pages/knowledge/pages/ExtractionPage` +
 * `pages/knowledge/services/overview`）与 `/v1/ontology/proposals`（3 属主：
 * `services/ontology/ontologyProposalApi` + `pages/ScenarioManagementView` + `pages/aiworkbench/api`），
 * 单源落在两族各自的原服务拆分文件（导出 `ONTOLOGY_MAPPINGS_BASE` / `ONTOLOGY_PROPOSALS_BASE`），
 * 四个页面侧消费者改为 import。三处形状须记清：
 *   ① **属主可以就是其中一个原属主**：前两批的单源都落在"另立的" api/services 文件，本批两个属主
 *      （`services/ontology/*.ts`）本来就只 import `../../api` + `../httpClient` + 类型 ⇒ 四个消费方
 *      引它不成环；门面 `services/ontologyApi.ts` 是纯 re-export、不持有 URL 体，且没有任何门强制
 *      "消费者只准引门面"，故直接引拆分文件。**门面不是属主**这一判读要留档。
 *   ② **族面不降**（256→256）：本批两族的属主声明本身仍是 `` `${API_BASE}/v1/ontology/…` `` 构造点，
 *      收口后族体在生产面各剩 1 条 ⇒ 族继续在索引里、只是从 dupBoth 退出。这与 B6-2/B6-3 的
 *      「 `{}` 子族两属主全退场 ⇒ 族整体消失」是两种形状，不得互相外推。
 *   ③ derSites 317→312 = **删 5 枚消费者派生点、属主声明一枚未动**。仍是副产物，账只记
 *      dupBoth 24→22 与属主 51→45。同根子路径族（`proposals/{}/approve|reject|execute`）各只有
 *      一个生产方，按 B6-1/B6-3 口径保持 `${API_BASE}` 原样以保住可见性，由新门反向钉死。
 * 收口的 7 条腿面值守恒不在本门证明范围内，由同批新增的 `b6OntologyFamilySingleSource.spec.ts` 承证。
 * #145/B6-5 复跑 = 680 / **256** / 裸面 **0** / 派生面 **310** / dupBoth **21** / dupRawOnly **0** /
 * blind **21**：存量债第五批收口 **pipeline 定义族三属主**——`/v1/pipeline/definitions`（3 属主：
 * `api/dataClient` 的私有 `const PIPELINE_BASE` + `pages/data-workbench/api/_base.ts` 的
 * `PIPELINE_DEFS` + `pages/aiworkbench/api.ts` 的一枚内联 `` `${API_BASE}/v1/pipeline/definitions` ``），
 * 单源导出为 `PIPELINE_DEFINITIONS_BASE` 落在 `api/dataClient`，另两属主改为取值。四处形状须记清：
 *   ① **属主层位由 import 图裁决到 api 层**：前两批把单源落在"原属主之一"，本批三属主里只有
 *      `api/dataClient` 处于分层底部（`aiworkbench/api` 本就经 `src/api.ts` 门面吃 dataClient），
 *      反向落页面层会把 api→page 的依赖倒置。dataClient 侧 6 枚消费者腿同批改用属主常量，
 *      故该文件从"私有 const + 6 腿"变成"导出常量 + 6 腿"，族体仍只有声明那一枚。
 *   ② **别名重导出行不是属主**：`_base.ts` 的 `PIPELINE_DEFS` 是 `pages/data-workbench/api/index.ts`
 *      的 `export * from './_base'` 导出面一员，#143/B5-8 头注红线要求常量逐字不变 ⇒ 该文件改为
 *      `export { PIPELINE_DEFINITIONS_BASE as PIPELINE_DEFS } from '../../../api/dataClient';`。
 *      这一行不含 URL 体，不构成第二个属主，但守恒门把它钉为一条**别名腿**（恰 1 行、旧名无本地声明、
 *      消费者 `pipeline.ts` 6 腿仍引 `_base` 且不直取属主），防止别名与属主日后各走各路。
 *   ③ **族面不降**（256→256）、**derSites 312→310 = 删 2 枚派生点**（`_base` 声明 + AI 台内联），
 *      属主声明一枚未动 ⇒ 仍是可见性副产物，账只记 dupBoth 22→21 与属主 45→42。
 *   ④ 本批新增两条判据形状（缺任一即误计），由守恒门反向 fixture 钉死：**⑧ 属主声明行自身的
 *      名 token 不算腿**（否则声明被计两次）；**⑨ 别名重导出行上的两个名 token 不算腿**
 *      （朴素裸名计数会凭空造出两枚）。
 * 收口的 4 文件 15→14 枚面值守恒不在本门证明范围内，由同批新增的
 * `b6PipelineDefinitionsSingleSource.spec.ts` 承证。
 * #145/B6-6 复跑 = 680 / **256** / 裸面 **0** / 派生面 **305** / dupBoth **18** / dupRawOnly **0** /
 * blind **18**：存量债第六批收口 **知识域三族（各两属主）**——`/v1/knowledge/query` 与
 * `/v1/knowledge/rag` 同为 `services/knowledgeQueryApi` + `pages/knowledge/services/wiki`，
 * `/v1/ontology/glossary` 为 `services/glossary` + 同一 wiki。三枚单源分别落在各族原服务文件
 * （`KNOWLEDGE_QUERY_BASE` / `KNOWLEDGE_RAG_BASE` / `ONTOLOGY_GLOSSARY_BASE`），wiki 侧改成 import。
 * 两处形状须记清：
 *   ① **一枚属主可以搬走而非删除**：glossary 族根声明本来就有两枚（`glossary.ts` 私有 + wiki 私有），
 *      本批把 wiki 那枚删掉、`glossary.ts` 那枚改名并 export ⇒ 声明位 2→1，面值一字未动。这与
 *      B6-1~B6-5 的「N 枚声明里留 1 枚」同形，但本批第一次出现"同一文件（wiki）在同一批里退出
 *      三个族"，故守恒门把 wiki 的 13→12 条面按**整文件**扣、不按面值筛（面值筛会把同值的消费腿一起吃掉）。
 *   ② **query/rag 两族改前没有常量**：wiki 与属主各写 `` `${API_BASE}/v1/knowledge/rag` `` 裸派生态，
 *      收口后新增 2 枚属主声明 ⇒ 面总量 18→19 是 **+1**（增 2 枚声明、删 1 枚重复声明），
 *      消费者腿 16→16 逐字不变。守恒判据因此拆成两面分别钉，不能只比总量——总量升 1 看着像"多请求"，
 *      拆开才是"声明位搬移 + 请求面不动"。derSites 310→305 的 −5 同理（删 7 枚 `${API_BASE}` 站点、
 *      增 2 枚声明），仍只作可见性副产物记账，进度只认 dupBoth 21→18 与属主 42→36。
 *   ③ **族面 256 不变**：三族收口后各剩 1 枚族根声明（写成 `${API_BASE}` 裸形态）⇒ 族继续留在索引里，
 *      第二生产方出现即红。同根子路径族 `/v1/knowledge/compliance-rules` 与 nav 两族本批不动（B6-7 议题）。
 * 收口的 3 文件 18→19 枚面值守恒不在本门证明范围内，由同批新增的
 * `b6KnowledgeQueryRagGlossarySingleSource.spec.ts` 承证。
 * #145/B6-7 复跑 = 680 / **254** / 裸面 **0** / 派生面 **300** / dupBoth **17** / dupRawOnly **0** /
 * blind **17**：存量债第七批收口 **知识域规则仓族两属主**——`/v1/knowledge/compliance-rules`
 * （`pages/knowledge/services/wiki.ts` 的私有 `const RULES_BASE` ＋
 * `pages/knowledge/pages/components/govern/RuleRepoTable.tsx` 的 5 枚内联 `` `${API_BASE}/v1/…` `` 腿），
 * 单源＝把 wiki 那枚**改名并 export** 为 `COMPLIANCE_RULES_BASE`，页面侧改成 import。三处形状须记清：
 *   ① **属主声明位不增不减**（与 B6-6 的「新增 2 枚声明」相对）：本族改前已有一枚族根声明，收口只是
 *      把它从私有升为导出 ⇒ 声明面 1→1、面值一字未动，属主从 2 降到 1 全靠页面侧 5 枚内联腿改引常量。
 *   ② **族面第一次降两枚**（256→254）：消失的不是被收口的族本身，而是只由 RuleRepoTable 一家书写的
 *      两枚**同根子族** `/v1/knowledge/compliance-rules/{}`（PUT/DELETE 腿归一化）与
 *      `/v1/knowledge/compliance-rules?{}`（跨行模板字面量的列表腿）。此即 B6-1 文件头 ② 预告、
 *      B6-2/B6-3 各兑现一次的「经属主常量拼出的腿对 `familiesOf` 隐形」机制，本批一次隐两枚。
 *      **检测面不受损**：再有人直接写第二枚族体腿仍判红；隐形只发生在引常量的合法消费腿——那正是单源的定义。
 *      族面降 2 属可见性变化，严禁读成「债多还了两族」；账仍只记 dupBoth 18→17 与属主 36→34。
 *   ③ derSites 305→300 = 删 5 枚内联派生态站点、属主声明一枚未动，同为副产物。
 * 收口的 2 文件面值守恒（wiki 声明 1 ＋ 消费腿 9、RuleRepoTable 5 枚腿）不在本门证明范围内，
 * 由同批新增的 `b6ComplianceRulesSingleSource.spec.ts` 承证。
 * #145/B6-8 复跑 = 680 / **253** / 裸面 **0** / 派生面 **297** / dupBoth **15** / dupRawOnly **0** /
 * blind **15**（改前 680 / 254 / 0 / 300 / 17 / 0 / 17 系把同款扫描器指向 `git archive HEAD` 解出的
 * src 树实跑读出，非推算）：存量债第八批收口 **数据网元数据两族四属主**——`/v1/datanet/metadata`
 * 改前两属主＝`api/dataClient.ts` 的私有 `DATANET_META` ＋ `services/dataCatalogClient.ts` 的私有
 * `DATANET_BASE`（同一族根的两枚 `` `${API_BASE}/v1/datanet/metadata` `` 声明，该文件的 3 枚消费腿
 * 走 `` `${DATANET_BASE}/…` `` ⇒ 对本门 DER_RE **从来隐形**）；`/v1/datanet/metadata/preview/{}?limit={}`
 * 改前两属主＝`pages/data-workbench/api/metadata.ts` ＋ `services/datanetMetadataApi.ts`（后者该族两条腿
 * 均为行内 `` `${API_BASE}/v1/…` ``，含 `resources/all` 一枚）。单源＝在 `api/dataClient.ts` 把
 * `DATANET_META` **改名并 export** 为 `DATANET_METADATA_BASE`，另两文件改成常量引用；页面门面
 * `metadata.ts` 本批一字未动。五处形状须记清：
 *   ① **属主层位由 import 图裁决到 api 层**（与 B6-5 同判据、与 B6-3「落在原属主之一」相对）：
 *      `services/dataCatalogClient.ts` 本来就值导入 `../api` 门面取 `apiFetchData`，而 `src/api.ts`
 *      又 re-export `api/dataClient.ts`。单源若落服务侧、让 api 层反向引它，即成
 *      `api.ts → dataClient → dataCatalogClient → api.ts` 循环。导入方向只能是 services/页面 → api。
 *   ② **preview 族只动一腿即闭合**：`metadata.ts` 保持原样 ⇒ 改后它是该族**唯一**属主，从 dupBoth 面
 *      退出但仍留在族索引里（实测本族 owners=1），再冒出第二个裸面写手照样变红。动的是
 *      `datanetMetadataApi.ts` 那枚行内腿——「收口一条腿就够」只在族确为两属主、且另一属主是本批
 *      刻意不动的页面门面时成立，不是普适做法。
 *   ③ **属主文件内部混用单源常量与裸派生态不是遗漏**：`dataClient.ts` 仍留
 *      `` `${API_BASE}/v1/datanet/metadata/resources/${active.datasourceId}` `` 一枚在册裸面腿
 *      （该族实扫 owners=1）。B6 只按 dupBoth 属主数记账，动这枚在册的裸面属主＝把可见性关掉而不减债
 *      （B6-1 文件头 ② 判例）。
 *   ④ **族面降 1**（254→253）：消失的那枚是 `/v1/datanet/metadata/resources/all`——改前唯一生产方就是
 *      `datanetMetadataApi.ts` 的行内腿，改成引常量后对 `familiesOf` 整体隐形；`dataCatalogClient` 的
 *      同名腿本来就隐形（见本段开头），`fields/{}?limit={}` 等 12 枚 datanet 元数据子族仍在册。
 *      同 B6-2/B6-3/B6-7：族面升降一律不作进度读，账只记 dupBoth 17→15 与属主 34→30。
 *      **族根本体构造点判据必须带族边界负向前查**（`` `(?!…[A-Za-z0-9_/-])` ``），否则
 *      `/v1/datanet/metadata/preview` 这类族内腿被误吃成族根第二构造方 ⇒ 新门第一条腿假红。
 *   ⑤ derSites 300→297 = 删 3 枚 `` `${API_BASE}` `` 站点（`dataCatalogClient.ts` 的 `DATANET_BASE` 声明 1
 *      ＋ `datanetMetadataApi.ts` 行内腿 2）、增 0（`DATANET_METADATA_BASE` 只是 `DATANET_META` 改名并
 *      export，同一枚 `` `${API_BASE}/v1/datanet/metadata` `` 声明位）。净 −3 全属形态搬运，不计进度；
 *      与本门 dupBoth −2 不同值，因为 `dataCatalogClient` 的 3 枚消费腿从来不在本门面里。
 * 收口的 3 文件面值守恒（5 条面值逐字不变 + 属主唯一性）不在本门证明范围内，
 * 由同批新增的 `b6DatanetMetadataSingleSource.spec.ts` 承证。
 * #145/B6-9 复跑 = 680 / **253** / 裸面 **0** / 派生面 **294** / dupBoth **12** / dupRawOnly **0** /
 * blind **12**（改前 680 / 253 / 0 / 297 / 15 / 0 / 15 系把同款扫描器指向 `git archive HEAD` 解出的
 * src 树实跑读出，非推算）：存量债第九批收口 **引擎监控域三族各两属主**——
 * `/v1/engine/cognitive`（`pages/MonitoringCenter.tsx` 的 `ENGINES` 一行 ＋ 服务侧
 * `services/cognitiveClient.ts` 已有的 `COGNITIVE_ENGINE_BASE` 声明，本批只把它从私有升为导出）、
 * `/v1/engine/ontology`（`pages/MonitoringCenter.tsx` ＋ `services/ontology/ontologyEngineApi.ts`
 * 的文件私有 `ENGINE_BASE`，改名并 export 为 `ONTOLOGY_ENGINE_BASE`）、
 * `/v1/engine/data/settings`（`pages/EngineMonitor.tsx` 的 `ENGINE_META.data.configPath` ＋
 * `pages/data-workbench/DataEngineConfigPanel.tsx` 的保存腿，两枚都是行内 `` `${API_BASE}/v1/…` ``，
 * 改前**没有**任何族根常量）。四处形状须记清：
 *   ① **属主层位不是一套规则**：cognitive/ontology 两族按 B6-4 判据落在**原属主之一的服务文件**
 *      （页面引它不成环）；settings 族按 B6-5/B6-8 判据必须落到 **api 层** `api/dataClient.ts`——
 *      两枚消费腿分属两个页面，若把单源放任一页面，另一页面就得"页面→页面"取常量，属分层倒置。
 *      先查 import 图再定属主，不看哪个文件顺眼。
 *   ② **门面不是第二属主**：`services/ontologyApi.ts` 的 `export * from "./ontology/ontologyEngineApi"`
 *      行不含 URL 体 ⇒ 页面经门面取值不会造出第二个族根构造方（B6-5 ② 别名判例的又一兑现）。
 *   ③ **声明位三种命运同批并存**：cognitive/ontology 两枚是「私有升导出」（0 增 0 删）、
 *      settings 是「新建一枚导出常量」（+1）。derSites 297→294 = 删 4 枚消费点（MonitoringCenter 2 ＋
 *      EngineMonitor 1 ＋ DataEngineConfigPanel 1）增 1 枚声明，净 −3 全属形态搬运，不计进度。
 *   ④ **同根子路径族仍按 B6-3/B6-1 口径不并进常量**：`/v1/engine/data/settings/groups`、`/defaults`、
 *      `/refresh` 三枚各只有一个生产方（不在重复面），保持 `${API_BASE}` 行内形态以保住族面可见性；
 *      并进去只是对棘轮隐形而不减债。故族面 253 一点不降：三族族根声明各剩 1 枚且仍写
 *      `` `${API_BASE}/v1/…` `` ⇒ 族留在索引、只退出 dupBoth，第二裸面写手即红。
 * 收口的 3 族 6 枚属主合并后的面值守恒与属主唯一性不在本门证明范围内，
 * 由同批新增的 `b6EngineFamiliesSingleSource.spec.ts` 承证。
 * #145/B6-10 复跑 = 680 / **253** / 裸面 **0** / 派生面 **290** / dupBoth **9** / dupRawOnly **0** /
 * blind **9**（改前 680 / 253 / 0 / 294 / 12 / 0 / 12 = 上一批落地后工作树的同门实跑读出，非推算）：
 * 存量债第十批收口 **ecos 域三族各两属主**——`/v1/ecos/ontologies`
 * （`components/ontology-workbench/OntologyDomainPanel.tsx` 的 `ONTOLOGY_API` 与 `ENTITY_API`
 * ——同文件两枚常量、同一面值，加上 `pages/business-workbench/BusinessWorkbenchLayout.tsx`
 * 的 `ENGINES` 取数腿——本批唯一「两属主都在页面侧」的族）、
 * `/v1/ecos/versions`（`pages/knowledge/tabs/DataWorkbenchImportTab.tsx` ＋
 * `services/ontology/ontologyVersionApi.ts` 的私有 `VERSION_BASE`）、
 * `/v1/ecos/world-model-graph`（`api/monitorClient.ts` 的私有 `WM_BASE` ＋
 * `services/worldModelGraphApi.ts` 的私有 `WMG`）。三处形状须记清：
 *   ① **两属主都在页面侧 ⇒ 属主落服务侧**：ontologies 族两个写手都是页面，任挑其一都会让另一侧
 *      「页面→页面」取常量（分层倒置，B6-9 ① 同判据），故新建 `ECOS_ONTOLOGIES_BASE` 落在
 *      `services/ontology/ontologyModelApi.ts`——该文件本就是 `/v1/ecos/ontologies` 的正典实现面。
 *   ② **api → services 方向已有判例**：world-model-graph 族两属主分属 api 层与 services 层，
 *      属主裁到 `api/monitorClient.ts`（腿多的一侧＝正典实现），服务侧反向 import——与 #145/B6-8
 *      的 `services/datanetMetadataApi.ts → api/dataClient.ts` 同方向，不成环。
 *   ③ **同值双别名一并收**：`monitorClient.ts` 内 `const CAUSAL_BASE = WM_BASE;` 改前是同族第三个
 *      构造点（面值同值），改后引属主常量 ⇒ 它不在 DER_RE 面里，族根构造点计数仍恰 1。
 *   ④ derSites 294→290 = 删 5 枚消费/声明点（OntologyDomainPanel 2 ＋ BusinessWorkbenchLayout 1 ＋
 *      DataWorkbenchImportTab 1 ＋ worldModelGraphApi 的 `WMG` 声明 1）、增 1 枚新建属主声明
 *      （`ECOS_ONTOLOGIES_BASE`；另两枚是私有升导出）。净 −4 全属形态搬运，不计进度；
 *      账只记 dupBoth 12→9 与属主 24→18。族面 253 一点不降：三族族根声明各剩 1 枚且仍写
 *      `` `${API_BASE}/v1/…` `` ⇒ 第二裸面写手即红。
 * 收口的 3 族 6 枚属主合并后的面值守恒与属主唯一性不在本门证明范围内，
 * 由同批新增的 `b6EcosFamiliesSingleSource.spec.ts` 承证。
 * #145/B6-11 复跑 = 680 / **253** / 裸面 **0** / 派生面 **287** / dupBoth **7** / dupRawOnly **0** /
 * blind **7**（改前 680 / 253 / 0 / 290 / 9 / 0 / 9 = 上一批落地后工作树的同门实跑读出，非推算）：
 * 存量债第十一批收口 **两族各两属主**——`/v1/agent-mesh`（`api/agentClient.ts` 的私有 `MESH` ＋
 * `services/agentMeshApi.ts` 的私有 `MESH`，把前者**升为导出** `AGENT_MESH_BASE`、后者改 import）、
 * `/v1/guardrails/policies`（`services/cognitiveEngineApi.ts` 的两枚内联腿 ＋
 * `pages/aiworkbench/api.ts` 的一枚内联腿，新建并导出 `GUARDRAILS_POLICIES_BASE` 落在服务侧）。
 * 四处形状须记清：
 *   ① **属主方向按 B6-3/B6-8 判据裁到服务层**：`pages/aiworkbench/api.ts` 与 `pages/Guardrails/constants.ts`
 *      都是页面侧，把单源放任一页面会让另一侧「页面→页面」取常量（B6-9 ① 分层倒置），故落服务侧。
 *   ② **别名重导出行不是属主**：`pages/Guardrails/constants.ts` 原来是本族的**第三个**真实生产方，
 *      但它写的是 `import { API_BASE as API_ROOT }` ＋ `` export const API_BASE = `${API_ROOT}/v1/…` ``，
 *      两个 token 名都不在本门 DER_RE 的写死口径里 ⇒ 从来隐形。本批把它换成不含 URL 体的
 *      `export { GUARDRAILS_POLICIES_BASE } from '../../services/cognitiveEngineApi';`（B6-5 ② 判例）。
 *      **门面口径记 4→2，真实生产方口径记 3→1**——台账两个数都写，防止后来者以为门面即全集。
 *   ③ **NEW：D-B5-40 在名字轴上的同形缺陷（登记为缺陷，非只补常量）**：判据把基址 token 名写死成
 *      `API_BASE`，于是 `API_BASE as X` 别名之后的 `` `${X}/v1/…` `` 构造点对本门完全不可见——
 *      形态是派生的、只换了名字。实测 prod 面改前**恰 1 枚**（即 ② 那处）。修法两条一起做：
 *      判据侧按文件取 token 集（`derivedRe`，`API_BASE` ∪ 本文件 `API_BASE as X` 的 X），
 *      生产侧把这个形状**禁绝**（'扫描面合规' 里硬钉 prod 面零枚 `API_BASE as X` 导入别名）。
 *      只补判据不禁形状 ⇒ 下一个人还能造出别的名字；只禁形状不补判据 ⇒ 门本身仍是瞎的。
 *   ④ **同根子路径族按 B6-3 ③/B6-9 ④ 口径刻意不并进常量**：`/v1/guardrails/policies/{}` 的 DELETE 腿
 *      保持 `` `${API_BASE}/v1/guardrails/policies/${id}` `` 行内形态，保住该子族在族索引里的可见性。
 *      故族面 253 一点不降（两族族根声明各剩 1 枚 ⇒ 第二裸面写手即红）。
 *   ⑤ **本批 pin 面值 290→287 里混进了「判据变宽 +1」，必须拆开记**（同一份扫描器分别指向
 *      `git archive HEAD` 解出树与本工作树，各按 strict＝旧写死名／wide＝本批补别名 token 集读一遍，
 *      只取本批涉及的 7 个文件）：HEAD strict 39 → 工作树 wide 36 ＝ pin 的 −3；同口径 wide 对 wide
 *      是 40 → 36 ＝ 净 **−4**，明细＝`agentMeshApi` 私有 `MESH` 声明 −1、`cognitiveEngineApi` 列表＋新增
 *      2 枚并为属主声明 1 枚 −1、`pages/aiworkbench/api.ts` 护栏列表腿 −1、`pages/Guardrails/constants.ts`
 *      影子族根 −1（这枚只在 wide 档显形、strict 档恒 0 ⇒ pin 面因此少减 1，是判据修形的代价不是进度）。
 *      另照登一处**与本批无关的既有口径差**：`pages/GuardrailsView.tsx` 的 5 枚 `` `${API_BASE}/…` `` 腿
 *      在两档都读 0——`familiesOf` 只认捕获体以 `/v1/` 开头的族体，而该文件的族根已在导入常量里、
 *      拼接段是 `/${encodeURIComponent(id)}` 归一成 `/{}` ⇒ 天然脱面；C1 门按行级 token 计数照常计入。
 *      这就是本批 C1 地板 −8 而棘轮同口径 −4 的全部来源，不得误读成「棘轮漏计」。
 *      账只记 dupBoth 9→7 与属主 18→14。
 * 收口的 2 族面值守恒与属主唯一性不在本门证明范围内，由同批新增的
 * `b6AgentMeshGuardrailsSingleSource.spec.ts` 承证。
 * 逐门去改 12 个遗留门的正则形状是开放式工作量且会再次漂移，
 * 本批改为在**树级**用一条双形态门覆盖这些腿共同保护的性质：
 * 「同一 URL 族在生产代码里只允许一个生产方」。
 *
 * 两种形态都计入（缺一即为本缺陷复发）：
 *   A 裸字面量：`"/api/v1/x"` / `'/api/v1/x'` / `` `/api/v1/x` ``（插值 `${…}` 归一为 `{}`）；
 *   B 派生态：  `` `${API_BASE}/v1/x` ``（API_BASE 单源 = services/httpClient.ts，值 "/api"）。
 * 单源正主 `services/httpClient.ts` 本身豁免（它必须持有 "/api"），测试面豁免。
 *
 * 棘轮口径 = **族 → 属主清单**（两侧都只减不增）：
 *   新族出现在重复面 → 红；基线族新增属主 → 红；基线族/属主已不再重复 → 陈旧豁免亦红
 *   （与 privateTransportRatchet「陈旧豁免即红」同口径：基线预算不留给已收口的项）。
 *   本门**不宣称**证明单源成立——基线剩 7 个重复族（#143/B5-19 建门 30 族 69 属主，#145/B6-1 收口
 *   `/v1/knowledge` 一族后 29 族 62 属主，#145/B6-2 收口 `/v1/workspace/scenarios` 两族一对后 27 族 57 属主，
 *   #145/B6-3 收口 AIP 域三族（同一对生产方）后 24 族 51 属主，#145/B6-4 收口本体域两族六属主后 22 族 45 属主，
 *   #145/B6-5 收口 pipeline 定义族三属主后 21 族 42 属主，#145/B6-6 收口知识域三族六属主后 18 族 36 属主，
 *   #145/B6-7 收口 `/v1/knowledge/compliance-rules` 族两属主后 17 族 34 属主——该族属主合并后，
 *   两个单属主子族（`/{}` 与 `?{}` 形态）随唯一生产方改引属主常量而退出族面（族面 256→254，
 *   属主合并≠漏检：新生产方若再写族体裸串/派生串仍会被本门抓到，论证见文件头 B6-7 形状②），
 *   #145/B6-8 收口数据网元数据两族四属主（族根双声明合一 + preview 族动一腿）后 15 族 30 属主
 *   ——两族收口后各留 1 属主、仍留在族索引里（详见文件头 B6-8 形状②③），
 *   #145/B6-9 收口引擎监控域三族六属主后 12 族 24 属主，#145/B6-10 收口 ecos 域三族六属主后 9 族 18 属主，
 *   #145/B6-11 收口 `/v1/agent-mesh` + `/v1/guardrails/policies` 两族后 **7 族 14 属主**），
 *   本门钉死的是「债不再增 + 派生态不再隐身 + 基址 token 名不再被写死」。
 *
 * 四防恒绿：
 *   ① 两条分支真在扫：派生分支拿 prod 面 `derSites > 0` 证，裸形态分支自 #143/B5-26 起 prod 面为 0、
 *      改由合成 fixture 证（防正则形状漂移后该分支恒 0 命中）；
 *   ② 双形态计数严格大于裸形态计数（结构性证明本门比旧判据强，不是等价改写）；
 *   ③ DEFECT/CLEAN 双向 fixture，且 DEFECT 面专门放一条「两文件都用派生态造同一族」
 *      ——旧判据对它零命中，本门必须判红；
 *   ④ #145/B6-11 起补**名字变体** fixture：一族由 `` `${API_BASE}/…` `` 与
 *      `` `${API_ROOT}/…` ``（`import { API_BASE as API_ROOT }`）两文件各造一次，
 *      写死 token 名的判据对它零命中 ⇒ 本门必须判红；反向腿：无别名导入时 `` `${API_ROOT}/…` ``
 *      不得被误认成基址构造点（防把任意 `${VAR}/v1/…` 吃进来造成误红面）。
 */
// @vitest-environment node
import { describe, it, expect, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));
const CANONICAL = 'services/httpClient.ts';

type Forms = { raw: number; der: number };
type Index = Map<string, Map<string, Forms>>;

/** 族键 = 去掉基址前缀并以 `{}` 归一插值后的路径串（含 query 骨架）。 */
const normalize = (body: string): string => {
  let s = body;
  for (let i = 0; i < 5 && /\$\{[^{}]*\}/.test(s); i += 1) s = s.replace(/\$\{[^{}]*\}/g, '{}');
  return s.replace(/\$\{[^{}]*\}/g, '{}');
};

const RAW_RE = /["'`]\/api((?:\\.|(?!["'`])[^\\])*)["'`]/g;
const DER_BODY = '((?:\\\\.|(?!`)[^\\\\])*)';
const DER_RE = new RegExp('`\\$\\{API_BASE\\}' + DER_BODY + '`', 'g');

/**
 * #145/B6-11 补的名字变体腿：派生态判据原本把 token 名**写死**成 `API_BASE`，于是
 * `import { API_BASE as X }` 之后写 `` `${X}/v1/…` `` 的族根构造点对本门完全隐形——
 * 这是 D-B5-40（"判据对派生态不可见"）在**名字轴**上的同形缺陷：形态是派生的、只是换了个名字。
 * 实测 prod 面改前仅一枚（`pages/Guardrails/constants.ts` 的 `API_BASE as API_ROOT`，它自持的常量
 * 恰好也叫 `API_BASE`、取值却是 `/api/v1/guardrails/policies`，本批随族单源一起收口）。
 * 这里按文件把 `API_BASE as X` 的 X 并进取 token 集，别名腿与本体腿同口径计入。
 */
const ALIAS_IMPORT_RE = /(?:^|[,{\s])API_BASE\s+as\s+([A-Za-z0-9_]+)/g;

function derivedRe(code: string): RegExp {
  const aliases = [...code.matchAll(ALIAS_IMPORT_RE)].map((m) => m[1]);
  if (aliases.length === 0) return DER_RE;
  return new RegExp('`\\$\\{(?:API_BASE|' + aliases.join('|') + ')\\}' + DER_BODY + '`', 'g');
}

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

function isTestFile(rel: string): boolean {
  return (
    rel.includes('__tests__') ||
    rel.endsWith('.test.ts') || rel.endsWith('.test.tsx') ||
    rel.endsWith('.spec.ts') || rel.endsWith('.spec.tsx') ||
    rel.startsWith('test/')
  );
}

/** 纯函数：从一段源码文本读出 {family → 形态计数}，供树级扫描与 fixture 双向复用。 */
function familiesOf(code: string): Map<string, Forms> {
  const out = new Map<string, Forms>();
  const bump = (f: string, kind: keyof Forms) => {
    const cur = out.get(f) ?? { raw: 0, der: 0 };
    cur[kind] += 1;
    out.set(f, cur);
  };
  const lines = code.split('\n').filter((l) => !isComment(l)).join('\n');
  for (const m of lines.matchAll(RAW_RE)) {
    const b = normalize(m[1]);
    if (b.startsWith('/v1/')) bump(b, 'raw');
  }
  for (const m of lines.matchAll(derivedRe(lines))) {
    const b = normalize(m[1]);
    if (b.startsWith('/v1/')) bump(b, 'der');
  }
  return out;
}

function walk(dir: string, out: string[]): void {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (entry === 'node_modules' || entry.startsWith('.')) continue;
    const st = statSync(full);
    if (st.isDirectory()) walk(full, out);
    else if ((entry.endsWith('.ts') || entry.endsWith('.tsx')) && !entry.endsWith('.d.ts')) out.push(full);
  }
}

function scan(srcRoot: string): { idx: Index; rawSites: number; derSites: number; prodFiles: number } {
  const files: string[] = [];
  walk(srcRoot, files);
  const idx: Index = new Map();
  let rawSites = 0;
  let derSites = 0;
  let prodFiles = 0;
  for (const f of files) {
    const rel = relative(srcRoot, f).split(sep).join('/');
    if (rel === CANONICAL || isTestFile(rel)) continue;
    prodFiles += 1;
    for (const [fam, forms] of familiesOf(readFileSync(f, 'utf8'))) {
      if (!idx.has(fam)) idx.set(fam, new Map());
      const owners = idx.get(fam)!;
      const cur = owners.get(rel) ?? { raw: 0, der: 0 };
      cur.raw += forms.raw;
      cur.der += forms.der;
      owners.set(rel, cur);
      rawSites += forms.raw;
      derSites += forms.der;
    }
  }
  return { idx, rawSites, derSites, prodFiles };
}

function dupByBothForms(idx: Index): Map<string, string[]> {
  const out = new Map<string, string[]>();
  for (const [fam, owners] of idx) if (owners.size > 1) out.set(fam, [...owners.keys()].sort());
  return out;
}

/** 旧判据口径（只认裸字面量）在同一索引上能看到的重复族 —— 用于量化盲区。 */
function dupByRawOnly(idx: Index): Map<string, string[]> {
  const out = new Map<string, string[]>();
  for (const [fam, owners] of idx) {
    const rawOwners = [...owners.entries()].filter(([, f]) => f.raw > 0).map(([o]) => o).sort();
    if (rawOwners.length > 1) out.set(fam, rawOwners);
  }
  return out;
}

/**
 * 基线 = 2026-10-19 #143/B5-19 本门实跑读出值（先跑门读实测、再登记，非推算）。
 * 族 → 属主清单；只减不增，收口一族/一属主必须与本批同款改动同批剪除。
 */
const BASELINE: Readonly<Record<string, readonly string[]>> = Object.freeze({
  '/v1/audit/stats': ['api/securityClient.ts', 'pages/SecurityAudit.tsx'],
  '/v1/cognitive/beliefs?domain={}': ['pages/scenario/OverrideTab.tsx', 'pages/scenario/ReplayTab.tsx'],
  '/v1/dq': ['api/dataClient.ts', 'pages/data-quality/api/_base.ts'],
  '/v1/engine/data/pipeline/runs/{}': ['pages/data-workbench/api/pipeline.ts', 'pages/data-workbench/pipeline-editor/PipelineExecutionMonitor.tsx'],
  '/v1/system/organizations/tree': ['api/iamClient.ts', 'pages/WorldModelViewer.tsx'],
});

describe('urlFamilyDualFormRatchet — URL 族第二生产方双形态棘轮门 (#143/B5-19 / D-B5-40)', () => {
  const snapshot = scan(SRC_ROOT);

  it('noNewDuplicateUrlOwner: 双形态扫描的重复族必须逐族逐属主等于基线（新增即红、陈旧亦红）', () => {
    const dup = dupByBothForms(snapshot.idx);
    const failures: string[] = [];

    for (const [fam, owners] of [...dup.entries()].sort()) {
      const allowed = BASELINE[fam];
      if (!allowed) {
        failures.push(`[基线外新重复族] ${fam} 由 ${owners.length} 个生产方各自构造：${owners.join(' + ')} — 单源判据对派生态不可见即为此族而设 (D-B5-40)`);
        continue;
      }
      const extra = owners.filter((o) => !allowed.includes(o));
      const gone = allowed.filter((o) => !owners.includes(o));
      if (extra.length > 0) failures.push(`[基线族新增属主] ${fam} 多出 ${extra.join(' + ')} — 只减不增`);
      if (gone.length > 0) failures.push(`[陈旧豁免] ${fam} 属主 ${gone.join(' + ')} 已不再构造该族，必须同批从 BASELINE 剪除`);
    }

    for (const fam of Object.keys(BASELINE)) if (!dup.has(fam)) failures.push(`[陈旧豁免] 基线族 ${fam} 现已无重复生产方，必须同批从 BASELINE 剪除`);

    expect(
      failures,
      failures.length ? `双形态重复族棘轮变红（实测 ${dup.size} 族重复 / 基线 ${Object.keys(BASELINE).length} 族）：\n${failures.join('\n')}` : undefined,
    ).toEqual([]);
  }, 30_000);

  it('派生分支真在扫 + 双形态口径严格强于裸字面量口径（本门不是旧判据的等价改写）', () => {
    expect(snapshot.derSites, '派生分支必须在扫，否则本门退化为裸形态旧判据').toBeGreaterThan(0);
    // #143/B5-26：C1 基址单源基线归零后，prod 面裸形态站点实跑读出 **0**（#143/B5-25 复跑还是 19，
    // 差额即末批 21 处里落在 `/v1/` 族面的 19 处；`/api/graphql` 与 `/api/telemetry/…` 两枚不带 v1 段，
    // 按本门口径从来不进族面）。原断言 `rawSites > 0` 描述的是「C1 尚未收口」的形状，现改为把 prod 面
    // **钉成 0** —— 有人再写裸面即红。裸形态分支的**活性**不能靠 prod 面证（0 命中与判据失灵同形），
    // 故改由下面两条合成 fixture 承担：单引号与双引号两种裸形态都必须被 RAW_RE 吃到。
    expect(snapshot.rawSites, 'prod 面不得再出现裸形态 /v1 站点（C1 已于 #143/B5-26 归零）').toBe(0);
    const RAW_SQ = "const A = '/api/v1/fixture/raw-branch';\nfetch(A);";
    const RAW_DQ = 'const B = "/api/v1/fixture/raw-branch";\nfetch(B);';
    expect(familiesOf(RAW_SQ).get('/v1/fixture/raw-branch')).toEqual({ raw: 1, der: 0 });
    expect(familiesOf(RAW_DQ).get('/v1/fixture/raw-branch')).toEqual({ raw: 1, der: 0 });
    // #143/B5-21 起把两条 `>=100` 绝对地板换掉：C1 每收口一批就把点位从裸形态面搬到派生面，
    // 地板迟早被真实进度撞红（本批 rawSites 113→87 即撞）。换成**两面之和恒等**这条更强的
    // 判据——纯 raw→derived 转换只在两面之间移动点位，总数不变；扫描根一旦漂移（漏目录、
    // 判据改动）总数必变，比两条独立地板更能证「没在扫空」。334 = 该批实跑读出，与 dup* 同批重钉。
    // #145/B6-1 起这条恒等式的适用面要打个补丁：**存量债收口批（B6）不是 raw→derived 搬运，
    // 而是「多属主各写一份派生态」→「一份派生态 + N 条 import」**，import 行不含 URL 体 ⇒ 点位数
    // 真降（本批 334→**328**，差额恰为被删的 6 条 `${API_BASE}/v1/knowledge` 基址声明）。
    // 恒等式的含义由此从「点数守恒」退成「点数等于本批实跑读出」，仍是有效的防空扫/防漂移钉，
    // 但**不得**再拿它的升降当进度：债的收敛只认 dupBoth 下降（见文件头 B5-25 归因）。
    // #145/B6-5：312 → **310** = `_base.ts` 一枚属主声明改成别名重导出（不含 URL 体）+ AI 台一枚
    // 内联派生态改成 import 裸引用，属主声明本身留在 dataClient 未动。同为可见性副产物，不计进度。
    // #145/B6-6：310 → **305** = 删 7 枚 `` `${API_BASE}` `` 站点（knowledgeQueryApi 3 枚内联消费点 +
    // wiki 4 枚：glossary 族根声明 1 + rag 2 + query 1）、增 2 枚新建属主声明（query/rag，仍写 `${API_BASE}`
    // 形态）；glossary 族根声明只是从 wiki 搬到属主文件、计数不变。净 −5 全是形态搬运，债的账只记
    // dupBoth 21 → 18 与属主 42 → 36。
    // #145/B6-7：305 → **300** = 删 5 枚 `` `${API_BASE}` `` 站点（全部落在 `govern/RuleRepoTable.tsx` 的
    // 内联腿：1 枚列表腿＋1 枚建腿＋2 枚 PUT 腿＋1 枚 DELETE 腿），属主声明位一枚未动（`wiki.ts` 的
    // RULES_BASE 只是改名并 export，仍是同一枚 `` `${API_BASE}/v1/knowledge/compliance-rules` ``）。
    // 净 −5 同属形态搬运；债的账只记 dupBoth 18 → 17 与属主 36 → 34。
    // #145/B6-8：300 → **297** = 删 3 枚 `` `${API_BASE}` `` 站点（`dataCatalogClient.ts` 的 `DATANET_BASE`
    // 族根声明 1 ＋ `datanetMetadataApi.ts` 两枚行内腿，后者 `API_BASE` 导入随之清零）、增 0
    // （`api/dataClient.ts` 的 `DATANET_METADATA_BASE` 只是 `DATANET_META` 改名并 export，
    // 仍写 `` `${API_BASE}/v1/datanet/metadata` `` 同一声明位）。`dataCatalogClient.ts` 的 3 枚消费腿
    // 改前写 `` `${DATANET_BASE}/…` ``、改后写 `` `${DATANET_METADATA_BASE}/…` ``，两形态都不在本门
    // DER_RE 面里 ⇒ 本门面只见它的声明位。净 −3 全属形态搬运；债的账只记 dupBoth 17 → 15 与属主 34 → 30。
    // #145/B6-9：297 → **294** = 删 4 枚 `` `${API_BASE}/v1/engine/… `` 消费点
    // （`MonitoringCenter.tsx` 的 ontology/cognitive 两枚 `ENGINES` 腿、`EngineMonitor.tsx` 的
    // `ENGINE_META.data.configPath`、`DataEngineConfigPanel.tsx` 的 PUT 保存腿），
    // 增 1 枚新建属主声明（`api/dataClient.ts` 的 `DATA_ENGINE_SETTINGS_BASE`）；
    // cognitive/ontology 两枚声明只是私有升导出、位上未增未减。净 −3 全属形态搬运，不计进度。
    // #145/B6-10：294 → **290** = 删 5 枚 `` `${API_BASE}` `` 站点（`OntologyDomainPanel.tsx` 同文件两枚
    // 同值常量 `ONTOLOGY_API`/`ENTITY_API` ＋ `BusinessWorkbenchLayout.tsx` 的 `ENGINES` 取数腿 ＋
    // `DataWorkbenchImportTab.tsx` 的版本列表腿 ＋ `worldModelGraphApi.ts` 的私有 `WMG` 声明）、
    // 增 1 枚新建属主声明（`services/ontology/ontologyModelApi.ts` 的 `ECOS_ONTOLOGIES_BASE`；
    // `ECOS_VERSIONS_BASE`/`ECOS_WORLD_MODEL_GRAPH_BASE` 只是私有升导出、位上未增未减）。净 −4 全属形态搬运。
    // #145/B6-11（存量债第十一批）：290 → **287** = 删 4 枚 `` `${API_BASE}` `` 站点（`agentMeshApi.ts`
    // 的 `MESH` 族根声明 1 ＋ `cognitiveEngineApi.ts` 的 list/create 两枚内联腿 ＋ `aiworkbench/api.ts`
    // 的列表腿 1）、增 1 枚新建属主声明（`cognitiveEngineApi.ts` 的 `GUARDRAILS_POLICIES_BASE`；
    // `AGENT_MESH_BASE` 只是私有升导出、位上未增未减）。净 −3 全属形态搬运。
    // 另记一笔**隐形**变化（不在 derSites 面里）：`pages/Guardrails/constants.ts` 改前的
    // `` `${API_ROOT}/v1/guardrails/policies` `` 一枚族根声明从来不被 DER_RE 认（token 名写死为
    // `API_BASE`），本批把它换成不含 URL 体的别名重导出 ⇒ 该族的真实生产方 3→1，而本门面只见 2→1。
    // 判据侧同步补上「`API_BASE as X` 导入别名也算基址 token」的腿，见 familiesOf/derivedRe，
    // 并由「判据能变红」批里的 ALIAS_A/ALIAS_B 合成 fixture 承证其活性（prod 面零枚，不能靠 prod 面证）。
    // 因此 290→287 这两档不同判据的读数**不可直接相减当进度**：以本批 wide 判据回读 HEAD 归档树是 291
    // （多出的 1 枚正是上面那枚从来隐形的影子族根），wide 对 wide 才是 −4。逐文件实测明细见文件头 B6-11 形状⑤。
    // #145/B6-12（存量债第十二批）：287 → **285** = 删 2 枚 `` `${API_BASE}` `` 站点，**判据本批未动**
    // ⇒ 两侧同口径可比（无需 wide/wide 换算）。逐文件实测：`ontologyWorkbenchApi` 1→0 ＋ `overview.ts` 7→6（改裸引用）；两属主 2→2／1→1（族根仅改名并 export）。
    expect(snapshot.rawSites + snapshot.derSites).toBe(285);
    const dupBoth = dupByBothForms(snapshot.idx);
    const dupRawOnly = dupByRawOnly(snapshot.idx);
    expect(dupBoth.size).toBeGreaterThan(dupRawOnly.size);
    // 三个数 = 本门实跑读出值，与 BASELINE 同批改动（重复面一变，这里必红，防「基线改了、盲区量化注记没跟上」）
    // #143/B5-20：知识服务域 32 处裸面派生化，dupBoth 不变（30），裸形态口径丢三族
    // （/v1/knowledge 五属主全裸面归零、/v1/ontology/mappings 与 /v1/knowledge/compliance-rules
    // 各降到单属主）⇒ 盲区 21 → 24。
    // #143/B5-21：data-workbench/api 域 26 处裸面派生化，dupBoth 仍 30，/v1/engine/data/pipeline/runs/{}
    // 只剩 1 个裸面属主 ⇒ dupRawOnly 6 → 5、盲区 24 → 25。这正是 D-B5-40 的预期行为：
    // C1 每收口一批，只认裸形态的旧判据就更瞎，不是本门放松。
    // #143/B5-22 dupRawOnly/blind 未动（归因见文件头）。#143/B5-23：场景域页面 17 处派生化一次抹掉
    // 四族的裸面可见性 ⇒ dupRawOnly 5 → **1**、盲区 25 → **29**；dupBoth 仍 30（基线不动、无陈旧豁免）。
    // #143/B5-24：散点域 11 处派生化把幸存最后一族 `/v1/ecos/ontologies` 的两个裸面属主同批转尽
    // ⇒ dupRawOnly 1 → **0**、盲区 29 → **30**（= dupBoth 全集）；dupBoth 仍 30、基线不动。
    // #145/B6-1（存量债首批）：`/v1/knowledge` 一族 7 属主收口为「`api/knowledgeClient.ts` 单源导出
    // `KNOWLEDGE_BASE` + 6 文件改为 import」⇒ **dupBoth 30 → 29**、BASELINE 同批剪除该族（属主 69 → 62）。
    // 这是本门建成（#143/B5-19）以来 dupBoth 的**第一次真实下降**，与 B5-21~26 各批的「原地不动」相对：
    // 那六批改的是形态、本批改的是生产方个数，正是文件头「dupBoth 下降才代表存量债真收敛」的兑现。
    // #145/B6-2（存量债第二批）：`/v1/workspace/scenarios` 三属主 + `/v1/workspace/scenarios/{}` 两属主
    // 合一为 `services/scenarioActionClient.ts` 的 `WORKSPACE_SCENARIOS_BASE` 单源 ⇒ **dupBoth 29 → 27**、
    // BASELINE 同批剪除两族（属主 62 → 57）。族面 258 → 257 的 1 族消失是**可见性**变化（该详情族的
    // 两个属主恰是本批唯一写出族体的人），不是新增重复，归因见文件头 ①。
    // #145/B6-3（存量债第三批）：`/v1/aip/agents` 两属主 + `/v1/aip/agents/{}` 两属主 + `/v1/aip/audit-logs`
    // 两属主（同一对生产方 `pages/aiworkbench/api.ts` 与 `services/aiworkbenchApi.ts`）合一为该页 api 层
    // 导出的 `AIP_AGENTS_BASE` / `AIP_AUDIT_LOGS_BASE` 双单源 ⇒ **dupBoth 27 → 24**、BASELINE 同批剪除三族
    // （属主 57 → 51）。族面 257 → 256 的又是**可见性**变化（`/v1/aip/agents/{}` 的两属主全退出族体书写），
    // derSites 323 → 317 = 删 8 枚派生点、增 2 枚属主声明，同为副产物；归因与隐形交代见文件头 B6-3 段。
    // #145/B6-4（存量债第四批）：`/v1/ontology/mappings` 三属主 + `/v1/ontology/proposals` 三属主收口为
    // `services/ontology/` 两分册各一枚导出常量 ⇒ **dupBoth 24 → 22**、BASELINE 同批剪除两族（属主 51 → 45）。
    // derSites 317 → **312** = 5 枚消费者派生点改写成 import 名（import 行不含 URL 体），两属主声明原样未动，
    // 仍是可见性副产物而非进度；债的收敛只记 dupBoth 与属主面。
    // #145/B6-5（存量债第五批）：`/v1/pipeline/definitions` 三属主（api/dataClient + pages/aiworkbench/api
    // + pages/data-workbench/api/_base）收口为 dataClient 一枚导出常量 ⇒ **dupBoth 22 → 21**、BASELINE 同批
    // 剪除该族（属主 45 → 42）。族面 256 不变（本族仍有单属主写出族体），是本波第一次「剪族而族面一分不动」：
    // 前两批族面各降 1 是因为收口把该族唯一写族体的两个人都搬走了，本批属主声明留在原处。
    // #145/B6-6（存量债第六批）：知识域三族各两属主（`/v1/knowledge/query`、`/v1/knowledge/rag` 同为
    // `services/knowledgeQueryApi` + `pages/knowledge/services/wiki`；`/v1/ontology/glossary` 为
    // `services/glossary` + 同一 wiki）收口为三枚导出常量单源 ⇒ **dupBoth 21 → 18**、BASELINE 同批剪除
    // 三族（属主 42 → 36 = 三族 × 2 条基线记录，每族收口后仍各留 1 枚族根声明 ⇒ 族面不降、只退出重复面）。
    // #145/B6-7（存量债第七批）：`/v1/knowledge/compliance-rules` 两属主（`pages/knowledge/services/wiki`
    // 的族根声明＋`pages/knowledge/pages/components/govern/RuleRepoTable` 的 5 枚内联腿）收口为 wiki 一枚
    // **改名并 export** 的 `COMPLIANCE_RULES_BASE` 单源 ⇒ **dupBoth 18 → 17**、BASELINE 同批剪除该族
    // （属主 36 → 34）。本批族面**第一次降两枚**（256 → 254）：消失的不是被收口的族本身（属主声明留在
    // wiki，族继续在索引里），而是只由 RuleRepoTable 一家书写的两枚**同根子族** ——
    // `/v1/knowledge/compliance-rules/{}`（PUT/DELETE 腿归一化后）与 `/v1/knowledge/compliance-rules?{}`
    // （跨行模板字面量的列表腿）。它们改前各只有 1 个生产方、从来不在重复面，收口后写族体的人换成引
    // 常量 ⇒ 对 `familiesOf` 整体隐形（＝B6-1 文件头 ② 预告、B6-2/B6-3 各兑现一次的同一机制，本批一次
    // 隐两枚）。检测面不受损：任何人再写第二枚族体腿仍会被判红，隐形只发生在「经属主常量拼出的腿」，
    // 而那正是单源的定义。族面升降一律不作进度读，进度仍只认 dupBoth 与属主两栏。
    // #145/B6-8（存量债第八批）：数据网元数据两族四属主——`/v1/datanet/metadata`（`api/dataClient.ts`
    // 私有 `DATANET_META` ＋ `services/dataCatalogClient.ts` 私有 `DATANET_BASE`，同一族根的两枚基址声明）
    // 与 `/v1/datanet/metadata/preview/{}?limit={}`（`pages/data-workbench/api/metadata.ts` ＋
    // `services/datanetMetadataApi.ts` 各一枚内联腿）收口为 `api/dataClient.ts` 一枚**改名并 export** 的
    // `DATANET_METADATA_BASE` ⇒ **dupBoth 17 → 15**、BASELINE 同批剪除两行（属主 34 → 30）。三点形状：
    // 属主层位由 import 图裁决到 api 层（服务侧反向引用会成 `api.ts → dataClient → dataCatalogClient → api.ts`
    // 循环，见文件头 B6-8 ①）；preview 族**只动一腿即闭合**、页面门面保持原样（②）；属主文件内部仍留
    // 一枚在册裸面腿（`…/resources/${…}`）不动——动它是关可见性而不减债（③）。族面 254 → 253 降 1 属
    // 可见性副产物（④），账只记 dupBoth 与属主。dupRawOnly 仍 0（prod 面裸形态自 #143/B5-26 归零）。
    // #145/B6-9（存量债第九批）：引擎监控域三族各两属主——`/v1/engine/cognitive`
    // （`pages/MonitoringCenter.tsx` ＋ `services/cognitiveClient.ts` 的 `COGNITIVE_ENGINE_BASE`）、
    // `/v1/engine/ontology`（`pages/MonitoringCenter.tsx` ＋ `services/ontology/ontologyEngineApi.ts`
    // 的 25 枚内联腿）、`/v1/engine/data/settings`（`pages/EngineMonitor.tsx` 的 `configPath` ＋
    // `pages/data-workbench/DataEngineConfigPanel.tsx` 的保存腿）收口为三枚导出常量单源
    // ⇒ **dupBoth 15 → 12**、BASELINE 同批剪除三行（属主 30 → 24）。族面 253 一点不降：三族各留
    // 1 枚族根声明且仍写成 `` `${API_BASE}/v1/…` `` ⇒ 族继续留在索引里，第二裸面写手即红
    // （同 B6-4/B6-5/B6-6/B6-8 形状，与 B6-2/B6-3/B6-7 的「族整体消失」相对）。derSites 297 → **294**
    // = 删 4 枚消费点（MonitoringCenter 2 ＋ EngineMonitor 1 ＋ DataEngineConfigPanel 1）、
    // 增 1 枚属主声明（`api/dataClient.ts` 的 `DATA_ENGINE_SETTINGS_BASE`；另两枚常量本来就在属主文件里，
    // 只是从私有升为导出）——净 −3 全属形态搬运，账只记 dupBoth 与属主两栏。
    // #145/B6-10（存量债第十批）：ecos 域三族各两属主——`/v1/ecos/ontologies`
    // （`components/ontology-workbench/OntologyDomainPanel.tsx` 的同文件两枚同值常量 ＋
    // `pages/business-workbench/BusinessWorkbenchLayout.tsx` 的取数腿）、
    // `/v1/ecos/versions`（`pages/knowledge/tabs/DataWorkbenchImportTab.tsx` ＋
    // `services/ontology/ontologyVersionApi.ts`）、`/v1/ecos/world-model-graph`
    // （`api/monitorClient.ts` ＋ `services/worldModelGraphApi.ts`）收口为三枚导出常量单源
    // ⇒ **dupBoth 12 → 9**、BASELINE 同批剪除三行（属主 24 → 18）。族面 253 一点不降：三族各留
    // 1 枚写成 `` `${API_BASE}/v1/…` `` 的族根声明 ⇒ 第二裸面写手即红（同 B6-9 形状）。
    // #145/B6-11：dupBoth 9 → **7**、盲区 9 → **7**（`/v1/agent-mesh` 两属主合一为
    // `api/agentClient.ts` 的 `AGENT_MESH_BASE`，`/v1/guardrails/policies` 两属主合一为
    // `services/cognitiveEngineApi.ts` 的 `GUARDRAILS_POLICIES_BASE`）。族面 253 一点不降：
    // 两族族根声明各留 1 枚且仍写 `` `${API_BASE}/v1/…` ``（同 B6-9/B6-10 形状），
    // 且 `/v1/guardrails/policies/{}` 的 DELETE 腿刻意保持行内形态以保住该子族可见性（B6-3 ③ 判例）。
    // #145/B6-12：dupBoth 7 → **5**、盲区 7 → **5**（两族各两属主合一为属主文件导出的
    // `ONTOLOGY_DATA_BASE`／`ONTOLOGY_EXPORT_BASE`）。族面 253 一点不降：两枚族根声明各留 1 枚且仍写
    // `` `${API_BASE}/v1/…` ``，且两处都是**改名并 export 既有私有常量**（同一声明位）⇒ 无净新增属主枚。
    expect(dupBoth.size).toBe(5);
    expect(dupRawOnly.size).toBe(0);
    const blind = [...dupBoth.keys()].filter((f) => !dupRawOnly.has(f)).sort();
    expect(blind.length).toBe(5);
    expect(blind.length, '盲区族清单必须非空且全部在册（否则基线口径与本门不符）').toBeGreaterThan(0);
    for (const f of blind) expect(Object.keys(BASELINE)).toContain(f);
  }, 30_000);

  it('判据能变红：DEFECT fixture 里「两文件都用派生态造同一族」必须被本门判红、被裸形态口径放过', () => {
    const DERIVED_A = ['import { API_BASE } from "./httpClient";', 'const A = `${API_BASE}/v1/fixture/dup${id}`;', 'fetch(A);'].join('\n');
    const DERIVED_B = ['import { API_BASE } from "./httpClient";', 'const B = `${API_BASE}/v1/fixture/dup${id}`;', 'fetch(B);'].join('\n');
    const RAW_CLEAN = ['import { API_BASE } from "./httpClient";', 'const C = `${API_BASE}/v1/fixture/only-one/${id}`;', 'fetch(C);'].join('\n');

    expect([...familiesOf(DERIVED_A).keys()]).toEqual(['/v1/fixture/dup{}']);
    expect([...familiesOf(DERIVED_B).keys()]).toEqual(['/v1/fixture/dup{}']);
    expect([...familiesOf(RAW_CLEAN).keys()]).toEqual(['/v1/fixture/only-one/{}']);

    const idx: Index = new Map();
    for (const [rel, code] of [['fixture/A.ts', DERIVED_A], ['fixture/B.ts', DERIVED_B], ['fixture/C.ts', RAW_CLEAN]] as const) {
      for (const [fam, forms] of familiesOf(code)) {
        if (!idx.has(fam)) idx.set(fam, new Map());
        idx.get(fam)!.set(rel, forms);
      }
    }
    // 双形态口径：dup 族两属主 → 判红
    expect(dupByBothForms(idx).get('/v1/fixture/dup{}')).toEqual(['fixture/A.ts', 'fixture/B.ts']);
    // 裸字面量口径（旧判据形状）：该族零裸面 → 看不见（= D-B5-40 复现）
    expect(dupByRawOnly(idx).has('/v1/fixture/dup{}')).toBe(false);
    // 注释行不计（与 api-base / privateTransportRatchet 同口径）
    expect([...familiesOf("// const X = `${API_BASE}/v1/fixture/in-comment`;\n/* `${API_BASE}/v1/fixture/block` */")].length).toBe(0);
  }, 30_000);

  it('判据能变红（名字轴，#145/B6-11）：`API_BASE as X` 别名构造点必须计入族面，且不得把任意 `${VAR}/v1/…` 误吃进来', () => {
    // 本体腿 + 别名腿造同一族 —— 这是 prod 面实测过的形状（pages/Guardrails/constants.ts）。
    // token 名写死为 `API_BASE` 的旧 DER_RE 对 ALIAS_B 零命中 ⇒ 两属主族掉出重复面＝假绿复发。
    const ALIAS_A = ['import { API_BASE } from "./httpClient";', 'const A = `${API_BASE}/v1/fixture/alias-dup${id}`;', 'fetch(A);'].join('\n');
    const ALIAS_B = ['import { API_BASE as API_ROOT } from "./httpClient";', 'const B = `${API_ROOT}/v1/fixture/alias-dup${id}`;', 'fetch(B);'].join('\n');

    // ① 别名腿被本门读到（派生态计数，非裸形态）
    expect(familiesOf(ALIAS_B).get('/v1/fixture/alias-dup{}')).toEqual({ raw: 0, der: 1 });
    // ② 旧判据形状（token 名写死）对同一份代码零命中 —— 证明这条腿真的在补瞎点，不是等价改写
    expect([...ALIAS_B.matchAll(DER_RE)].length, 'DER_RE 若已能吃到别名，说明 derivedRe 的补腿失去意义').toBe(0);
    // ③ 两属主在双形态口径下构成重复族（= 本门会判红），在旧口径下仍然隐形
    const aliasIdx: Index = new Map();
    for (const [rel, code] of [['fixture/A.ts', ALIAS_A], ['fixture/B.ts', ALIAS_B]] as const) {
      for (const [fam, forms] of familiesOf(code)) {
        if (!aliasIdx.has(fam)) aliasIdx.set(fam, new Map());
        aliasIdx.get(fam)!.set(rel, forms);
      }
    }
    expect(dupByBothForms(aliasIdx).get('/v1/fixture/alias-dup{}')).toEqual(['fixture/A.ts', 'fixture/B.ts']);
    expect(dupByRawOnly(aliasIdx).has('/v1/fixture/alias-dup{}')).toBe(false);
    // ④ 反向腿：没有 `API_BASE as X` 导入时，`API_ROOT` 只是别人家的变量，不得被认成基址构造点
    //    （否则派生态判据从"认基址"漂成"认任意模板变量"，会把 `${apiRoot}`/`${RUNTIME}` 之类一起吃进族面）
    const NO_ALIAS = ['import { API_BASE } from "./httpClient";', 'const C = `${API_ROOT}/v1/fixture/alias-negative`;', 'fetch(C);'].join('\n');
    expect([...familiesOf(NO_ALIAS).keys()]).toEqual([]);
    // ⑤ 多别名/同行导入形状也吃到（import { API_BASE as A, OTHER } 与两处别名各计一枚）
    const MULTI_ALIAS = [
      'import { API_BASE as P, API_BASE as Q, OTHER } from "./httpClient";',
      'const D = `${P}/v1/fixture/multi-a`;',
      'const E = `${Q}/v1/fixture/multi-b`;',
    ].join('\n');
    expect([...familiesOf(MULTI_ALIAS).entries()].map(([f, n]) => [f, n.der])).toEqual([['/v1/fixture/multi-a', 1], ['/v1/fixture/multi-b', 1]]);
  }, 30_000);

  it('基线自身可校验：族面/属主面形态合规且与实跑读出同数', () => {
    const entries = Object.entries(BASELINE);
    // #145/B6-1 起 29 族 / 62 属主（`/v1/knowledge` 一族 7 属主收口单源，与本批同款改动同批剪除）
    // #145/B6-2 起 27 族 / 57 属主（`/v1/workspace/scenarios` 两族一对共 5 属主收口单源，同批剪除）
    // #145/B6-3 起 24 族 / 51 属主（`/v1/aip/agents`、`/v1/aip/agents/{}`、`/v1/aip/audit-logs` 三族
    // 共 6 属主收口为页 api 层双常量，同批剪除）
    // #145/B6-4 起 22 族 / 45 属主（`/v1/ontology/mappings`、`/v1/ontology/proposals` 两族共 6 属主
    // 收口为 `services/ontology/` 两分册各一枚导出常量，同批剪除）
    // #145/B6-5 起 21 族 / 42 属主（`/v1/pipeline/definitions` 三属主收口为 `api/dataClient.ts` 的
    // `PIPELINE_DEFINITIONS_BASE` 单源，同批剪除；三属主里两枚声明位消失、一枚改 import 裸引用）
    // #145/B6-6 起 18 族 / 36 属主（知识域 `/v1/knowledge/query`、`/v1/knowledge/rag`、`/v1/ontology/glossary`
    // 三族共 6 属主收口为三枚导出常量，同批剪除）
    // #145/B6-7 起 17 族 / 34 属主（`/v1/knowledge/compliance-rules` 两属主收口为 `pages/knowledge/services/`
    // `wiki.ts` 一枚改名并 export 的 `COMPLIANCE_RULES_BASE`，同批剪除；属主声明位未增未减）
    // #145/B6-8 起 15 族 / 30 属主（`/v1/datanet/metadata` 族根双声明合一为 `api/dataClient.ts` 的
    // `DATANET_METADATA_BASE` ＋ `/v1/datanet/metadata/preview/{}?limit={}` 动一腿即闭合，同批剪除两行；
    // 两族收口后各留 1 属主、仍留在族索引里 ⇒ 在册性探针按「族不退出索引、只退出重复面」口径验）
    // #145/B6-9 起 12 族 / 24 属主（引擎监控域三族各两属主——`/v1/engine/cognitive`、`/v1/engine/ontology`
    // 同为 `pages/MonitoringCenter.tsx` + 各自服务文件，`/v1/engine/data/settings` 为
    // `pages/EngineMonitor.tsx` + `pages/data-workbench/DataEngineConfigPanel.tsx`——收口为三枚导出常量
    // （`COGNITIVE_ENGINE_BASE` / `ONTOLOGY_ENGINE_BASE` / `DATA_ENGINE_SETTINGS_BASE`），同批剪除三行；
    // 三族族根声明各留 1 枚 ⇒ 族面 253 不降、只退出重复面，归因见文件头 B6-9 段）
    // #145/B6-10 起 9 族 / 18 属主（ecos 域三族各两属主——`/v1/ecos/ontologies` 为
    // `components/ontology-workbench/OntologyDomainPanel.tsx`（同文件两枚同值常量）+
    // `pages/business-workbench/BusinessWorkbenchLayout.tsx`，`/v1/ecos/versions` 为
    // `pages/knowledge/tabs/DataWorkbenchImportTab.tsx` + `services/ontology/ontologyVersionApi.ts`，
    // `/v1/ecos/world-model-graph` 为 `api/monitorClient.ts` + `services/worldModelGraphApi.ts`——
    // 收口为三枚导出常量（`ECOS_ONTOLOGIES_BASE` / `ECOS_VERSIONS_BASE` /
    // `ECOS_WORLD_MODEL_GRAPH_BASE`），同批剪除三行；三族族根声明各留 1 枚 ⇒ 族面 253 不降）
    // #145/B6-11 起 7 族 / 14 属主（`/v1/agent-mesh` 的 `api/agentClient.ts` + `services/agentMeshApi.ts`
    // 合一为前者导出的 `AGENT_MESH_BASE`；`/v1/guardrails/policies` 的 `pages/aiworkbench/api.ts` +
    // `services/cognitiveEngineApi.ts` 合一为后者导出的 `GUARDRAILS_POLICIES_BASE`，页面侧
    // `pages/Guardrails/constants.ts` 的同族根声明改为不含 URL 体的别名重导出——它本来就不在本门面里，
    // 属主面按门面口径记 4→2；两族族根声明各留 1 枚 ⇒ 族面 253 不降）
    // #145/B6-12 起 5 族 / 10 属主：`/v1/ontology/data` 的 `ontologyDataApi` + `ontologyWorkbenchApi`
    // 合一为前者导出的 `ONTOLOGY_DATA_BASE`，`/v1/ontology/export` 的 `ontologyExportApi` +
    // `pages/knowledge/services/overview.ts` 合一为前者导出的 `ONTOLOGY_EXPORT_BASE`（各−2 属主，无净新增枚）
    expect(entries.length).toBe(5);
    expect(entries.reduce((n, [, o]) => n + o.length, 0)).toBe(10);
    expect(BASELINE['/v1/knowledge']).toBeUndefined();
    expect(BASELINE['/v1/workspace/scenarios']).toBeUndefined();
    expect(BASELINE['/v1/workspace/scenarios/{}']).toBeUndefined();
    expect(BASELINE['/v1/aip/agents']).toBeUndefined();
    expect(BASELINE['/v1/aip/agents/{}']).toBeUndefined();
    expect(BASELINE['/v1/aip/audit-logs']).toBeUndefined();
    expect(BASELINE['/v1/ontology/mappings']).toBeUndefined();
    expect(BASELINE['/v1/ontology/proposals']).toBeUndefined();
    expect(BASELINE['/v1/pipeline/definitions']).toBeUndefined();
    expect(BASELINE['/v1/knowledge/query']).toBeUndefined();
    expect(BASELINE['/v1/knowledge/rag']).toBeUndefined();
    expect(BASELINE['/v1/ontology/glossary']).toBeUndefined();
    expect(BASELINE['/v1/knowledge/compliance-rules']).toBeUndefined();
    expect(BASELINE['/v1/engine/cognitive']).toBeUndefined();
    expect(BASELINE['/v1/engine/ontology']).toBeUndefined();
    expect(BASELINE['/v1/engine/data/settings']).toBeUndefined();
    expect(BASELINE['/v1/ecos/ontologies']).toBeUndefined();
    expect(BASELINE['/v1/ecos/versions']).toBeUndefined();
    expect(BASELINE['/v1/ecos/world-model-graph']).toBeUndefined();
    expect(BASELINE['/v1/agent-mesh']).toBeUndefined();
    expect(BASELINE['/v1/guardrails/policies']).toBeUndefined();
    expect(BASELINE['/v1/ontology/data']).toBeUndefined();
    expect(BASELINE['/v1/ontology/export']).toBeUndefined();
    for (const [fam, owners] of entries) {
      expect(fam.startsWith('/v1/')).toBe(true);
      expect(fam.includes('\\')).toBe(false);
      expect(owners.length).toBeGreaterThanOrEqual(2);
      expect([...owners].sort()).toEqual(owners);
      expect(new Set(owners).size).toBe(owners.length);
      for (const o of owners) {
        expect(o.startsWith('./')).toBe(false);
        expect(o.includes('\\')).toBe(false);
        expect(isTestFile(o)).toBe(false);
        expect(o).not.toBe(CANONICAL);
      }
    }
  }, 30_000);

  it('扫描面合规：生产文件数与豁免面与本门口径一致（防扫描根漂移成零命中）', () => {
    expect(snapshot.prodFiles).toBeGreaterThanOrEqual(600);
    expect(snapshot.idx.size).toBeGreaterThanOrEqual(200);
    // 族索引面硬钉（#145/B6-11 起）：`>=200` 只能防空扫，读不出"本批族面降了几枚"这种可见性变化；
    // B6 各批的族面升降必须在这里显形，才配得上文件头「族面变化一律记为可见性副产物、不作进度读」。
    // 253 = 本批实跑读出（`/v1/agent-mesh` 与 `/v1/guardrails/policies` 两族族根声明各留 1 枚，
    // `/v1/guardrails/policies/{}` 的 DELETE 腿刻意保持行内形态 ⇒ 本批一族未退出索引）。
    expect(snapshot.idx.size, '族索引面与本批实跑读出脱钩 ⇒ 有族的可见性被关掉而台账没记').toBe(253);
    for (const owners of snapshot.idx.values()) expect(owners.has(CANONICAL)).toBe(false);
    // 名字变体面归零（#145/B6-11）：prod 面不得再有 `import { API_BASE as X }` 形态的基址别名——
    // 它虽然已被 derivedRe 计入族面，但这个形状本身就是 D-B5-40 在名字轴上的成因，出现即红。
    // 用非全局正则（全局 `.test` 会因 lastIndex 前进而漏判，属判据自身缺陷）。
    const aliasImportRe = /^\s*import\s[^\n]*\bAPI_BASE\s+as\s+[A-Za-z0-9_]+/m;
    const files: string[] = [];
    walk(SRC_ROOT, files);
    const aliased = files
      .map((f) => relative(SRC_ROOT, f).split(sep).join('/'))
      .filter((rel) => rel !== CANONICAL && !isTestFile(rel))
      .filter((rel) => aliasImportRe.test(readFileSync(join(SRC_ROOT, rel), 'utf8')));
    expect(aliased, 'prod 面出现 API_BASE 导入别名（名字变体隐形腿的成因形状）').toEqual([]);
    // 量级自播报：台账里的四个数（生产文件面/族面/裸形态命中行/派生态命中行）一律取自此行输出，
    // 防止「写进文档的数字其实是上一代判据的估算」——本台账反复要防的正是凭记忆断言。
    console.info(
      `[urlFamilyDualFormRatchet 实跑量级] prodFiles=${snapshot.prodFiles} families=${snapshot.idx.size} rawSites=${snapshot.rawSites} derSites=${snapshot.derSites}`,
    );
  }, 30_000);
});
