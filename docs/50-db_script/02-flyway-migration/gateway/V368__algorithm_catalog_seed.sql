-- ============================================================================
-- V368 — 六大类认知算法目录种子（2026-11-26 场景工作台下一步落地 · 批次 B1）
-- ============================================================================
-- 目标（只加不删）：登记 40 个算法条目，六大类各成组；Java 执行器条目 ACTIVE，
-- Python 沙盒条目在 B3 模板落地前为 DISABLED（禁空壳可用）。
--
-- 纪律：IR03；MC01 主键 VARCHAR(36) 应用侧 UUID；MC02 类型白名单；MC03 标准 SQL；
--       ST07 落 ecos_control；DR07 version_no；DR08 domain；不建 FK（ST09）。
-- 说明：status=DISABLED 的条目在场景配置可见、运行页不推荐；B3 落地后由后续脚本/配置改 ACTIVE。
-- ============================================================================

INSERT INTO ecos_control.ecos_algorithm_registry
    (id, name, analysis_type, category, version, provider_type, executor_kind, implementation_ref,
     description, status, create_by, create_time)
SELECT v.id, v.name, v.analysis_type, v.category, '1', 'ALGORITHM', v.executor_kind,
       v.implementation_ref, v.description, v.status, 'V368', NOW()
FROM (VALUES
    -- ── 1 信念与证据推理 ────────────────────────────────────────────────
    ('alg-bayes-update', '贝叶斯推理', 'BELIEF', 'BELIEF', 'JAVA', 'java:bayes-update',
     '离散先验与似然按贝叶斯公式归一，输出后验与证据边际概率', 'ACTIVE'),
    ('alg-pgm-inference', '概率图模型推断', 'BELIEF', 'BELIEF', 'JAVA', 'java:pgm-inference',
     '小规模离散贝叶斯网按全枚举精确推断，输出边际与 MAP', 'ACTIVE'),
    ('alg-evidence-fusion', '证据融合', 'BELIEF', 'BELIEF', 'JAVA', 'java:evidence-fusion',
     '多来源可信度加权融合，输出融合置信与来源贡献', 'ACTIVE'),
    ('alg-confidence-calibration', '置信度校准', 'BELIEF', 'BELIEF', 'JAVA', 'java:confidence-calibration',
     '按分箱统计预测置信与命中率，输出 ECE 与校准后置信', 'ACTIVE'),
    ('alg-dempster-shafer', 'Dempster-Shafer 证据理论', 'BELIEF', 'BELIEF', 'JAVA', 'java:dempster-shafer',
     'mass 函数 Dempster 组合规则，输出组合 mass 与冲突系数 K', 'ACTIVE'),

    -- ── 2 因果发现与因果推断 ────────────────────────────────────────────
    ('alg-scm-linear', '结构因果模型（线性）', 'CAUSAL', 'CAUSAL', 'JAVA', 'java:scm-linear',
     '线性结构方程 + do(A) 干预，输出干预前后均值差', 'ACTIVE'),
    ('alg-do-calculus-adjustment', 'Do-calculus 后门调整', 'CAUSAL', 'CAUSAL', 'JAVA', 'java:do-calculus-adjustment',
     '按给定协变量做后门调整，输出调整后因果效应', 'ACTIVE'),
    ('alg-causal-discovery-pc', 'PC 因果结构发现', 'CAUSAL', 'CAUSAL', 'PYTHON_SANDBOX', 'python:causal-discovery-pc',
     '基于偏相关条件独立检验的骨架学习（小规模），输出无向骨架（Python 沙盒）', 'ACTIVE'),
    ('alg-causal-discovery-fci', 'FCI 因果结构发现', 'CAUSAL', 'CAUSAL', 'PYTHON_SANDBOX', 'python:causal-discovery-fci',
     '允许隐变量的 FCI 骨架与定向（小规模），输出 PAG（Python 沙盒）', 'ACTIVE'),
    ('alg-propensity-score', '倾向评分与 IPW', 'CAUSAL', 'CAUSAL', 'PYTHON_SANDBOX', 'python:propensity-score',
     'Logistic 倾向评分 + 逆概率加权，输出 ATE（Python 沙盒）', 'ACTIVE'),
    ('alg-doubly-robust', '双重稳健估计', 'CAUSAL', 'CAUSAL', 'PYTHON_SANDBOX', 'python:doubly-robust',
     '结果模型与倾向模型双稳健 ATE 估计（Python 沙盒）', 'ACTIVE'),
    ('alg-causal-forest', '因果森林', 'CAUSAL', 'CAUSAL', 'PYTHON_SANDBOX', 'python:causal-forest',
     '诚实切分的因果森林 CATE 估计（小样本，待 B3）', 'ACTIVE'),

    -- ── 3 预测与状态估计 ────────────────────────────────────────────────
    ('alg-linear-regression', '线性回归', 'FORECAST', 'FORECAST', 'JAVA', 'java:linear-regression',
     '最小二乘一元/多元线性回归，输出系数、R² 与预测值', 'ACTIVE'),
    ('alg-glm-logistic', '广义线性模型（Logistic）', 'FORECAST', 'FORECAST', 'JAVA', 'java:glm-logistic',
     'IRLS 拟合二项 Logistic，输出系数与概率', 'ACTIVE'),
    ('alg-arima', 'ARIMA', 'FORECAST', 'FORECAST', 'PYTHON_SANDBOX', 'python:arima',
     '差分 + Yule-Walker AR(p) 预测（Python 沙盒）', 'ACTIVE'),
    ('alg-exponential-smoothing', '指数平滑', 'FORECAST', 'FORECAST', 'JAVA', 'java:exponential-smoothing',
     'SES/Holt 指数平滑，输出点预测与残差标准差', 'ACTIVE'),
    ('alg-random-forest', '随机森林', 'FORECAST', 'FORECAST', 'PYTHON_SANDBOX', 'python:random-forest',
     'Bootstrap 回归树集成（Python 沙盒）', 'ACTIVE'),
    ('alg-gbdt', '梯度提升树', 'FORECAST', 'FORECAST', 'PYTHON_SANDBOX', 'python:gbdt',
     '平方损失梯度提升回归树（Python 沙盒）', 'ACTIVE'),
    ('alg-time-series-mlp', '时序神经网络', 'FORECAST', 'FORECAST', 'PYTHON_SANDBOX', 'python:time-series-mlp',
     '单隐层 MLP 自回归预测（Python 沙盒）', 'ACTIVE'),
    ('alg-kalman-filter', '卡尔曼滤波', 'FORECAST', 'FORECAST', 'JAVA', 'java:kalman-filter',
     '一维线性卡尔曼滤波与一步预测，输出滤波值与增益', 'ACTIVE'),
    ('alg-hmm', '隐马尔可夫模型', 'FORECAST', 'FORECAST', 'JAVA', 'java:hmm',
     '离散 HMM 前向-后向与 Viterbi，输出状态后验与最可能路径', 'ACTIVE'),

    -- ── 4 情景仿真与不确定性 ────────────────────────────────────────────
    ('alg-monte-carlo', '蒙特卡洛模拟', 'SIMULATION', 'SIMULATION', 'JAVA', 'java:monte-carlo',
     '独立分布采样 + 线性响应，输出均值、P05/P50/P95 与亏损概率', 'ACTIVE'),
    ('alg-discrete-event-sim', '离散事件仿真', 'SIMULATION', 'SIMULATION', 'JAVA', 'java:discrete-event-sim',
     '多服务台排队事件推进，输出平均等待与队列长度', 'ACTIVE'),
    ('alg-system-dynamics', '系统动力学', 'SIMULATION', 'SIMULATION', 'JAVA', 'java:system-dynamics',
     '存量-流量 Euler 步进，输出各步存量轨迹', 'ACTIVE'),
    ('alg-sensitivity-analysis', '敏感性分析', 'SIMULATION', 'SIMULATION', 'JAVA', 'java:sensitivity-analysis',
     '单因素扰动响应排序（±5%），输出敏感度与排名', 'ACTIVE'),
    ('alg-global-uncertainty-sobol', '全局不确定性（Sobol）', 'SIMULATION', 'SIMULATION', 'JAVA', 'java:global-uncertainty-sobol',
     'Saltelli 采样的一阶/总效应 Sobol 指数（小样本）', 'ACTIVE'),

    -- ── 5 决策、优化与方案排序 ──────────────────────────────────────────
    ('alg-linear-programming', '线性规划', 'DECISION', 'DECISION', 'JAVA', 'java:linear-programming',
     '两阶段单纯形求解 max/min cᵀx s.t. Ax≤b, x≥0，输出最优解与目标值', 'ACTIVE'),
    ('alg-mixed-integer-programming', '混合整数规划', 'DECISION', 'DECISION', 'PYTHON_SANDBOX', 'python:mixed-integer-programming',
     '分支定界 + 线性松弛（小规模，待 B3）', 'ACTIVE'),
    ('alg-multi-objective-pareto', '多目标 Pareto', 'DECISION', 'DECISION', 'JAVA', 'java:multi-objective-pareto',
     '复用 NSGA-II 求非支配前沿（基准目标，输出前沿解）', 'ACTIVE'),
    ('alg-ahp', 'AHP 层次分析', 'DECISION', 'DECISION', 'JAVA', 'java:ahp',
     '判断矩阵幂迭代求权重 + 一致性比率 CR', 'ACTIVE'),
    ('alg-topsis', 'TOPSIS 多准则排序', 'DECISION', 'DECISION', 'JAVA', 'java:topsis',
     '向量归一 + 正负理想解距离，输出贴近度与排序', 'ACTIVE'),
    ('alg-dynamic-programming', '动态规划', 'DECISION', 'DECISION', 'JAVA', 'java:dynamic-programming',
     '0/1 背包资源分配，输出最优值与选择集', 'ACTIVE'),
    ('alg-reinforcement-learning', '强化学习（表格）', 'DECISION', 'DECISION', 'JAVA', 'java:reinforcement-learning',
     '给定小 MDP 的表格 Q-learning，输出策略与收敛回合', 'ACTIVE'),

    -- ── 6 认知结构、异常诊断与假设验证 ──────────────────────────────────
    ('alg-graph-traversal', '图遍历', 'DIAGNOSTIC', 'DIAGNOSTIC', 'JAVA', 'java:graph-traversal',
     '有向图 BFS/DFS 可达集与层级，输出访问序', 'ACTIVE'),
    ('alg-path-analysis', '路径分析', 'DIAGNOSTIC', 'DIAGNOSTIC', 'JAVA', 'java:path-analysis',
     'Dijkstra 最短路径与全部路径计数，输出路径与代价', 'ACTIVE'),
    ('alg-graph-centrality', '图中心性', 'DIAGNOSTIC', 'DIAGNOSTIC', 'JAVA', 'java:graph-centrality',
     '度中心性 + PageRank（幂迭代），输出排序', 'ACTIVE'),
    ('alg-clustering', '聚类与分群', 'DIAGNOSTIC', 'DIAGNOSTIC', 'JAVA', 'java:clustering',
     'k-means（固定种子）与轮廓系数，输出质心与标签', 'ACTIVE'),
    ('alg-anomaly-detection', '异常检测', 'DIAGNOSTIC', 'DIAGNOSTIC', 'JAVA', 'java:anomaly-detection',
     'z-score / IQR 双档异常点判定，输出异常索引与分数', 'ACTIVE'),
    ('alg-hypothesis-testing', '假设检验与多重校正', 'DIAGNOSTIC', 'DIAGNOSTIC', 'JAVA', 'java:hypothesis-testing',
     '单样本/双样本 t 检验 + Bonferroni/Holm 校正', 'ACTIVE'),
    ('alg-drift-changepoint', '漂移与变点检测', 'DIAGNOSTIC', 'DIAGNOSTIC', 'JAVA', 'java:drift-changepoint',
     'CUSUM 累积和变点检测，输出变点索引与统计量', 'ACTIVE')
) AS v(id, name, analysis_type, category, executor_kind, implementation_ref, description, status)
WHERE NOT EXISTS (
    SELECT 1 FROM ecos_control.ecos_algorithm_registry r WHERE r.id = v.id
);
