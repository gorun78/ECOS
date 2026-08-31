To: 肖总 / PMO (PM), BE, FE
From: 负责人 (Commander)
时间: 2026-08-25 22:50 (CST)
主题: PMO-37 第 1 轮复验 — I1/I2 通报 (I3 monitor 走 channel 归 SM 处理)
【状态: inner draft，不下发 — 等 Next 座位 22:52 复核主 Agent 全量编译后再定是否需要下发】
关联审计: ECOS/docs/07项目管理/07-06审计报告/PMO-37_第1轮复验.md

结论先行:
1. 收到"整包已达 PMO，SM-1 关闭"的闭环报告。
2. 但对照 git/代码验证后发现 **I1/I2 两项 P1 未关闭**，"BE T0-T8 完成 + T10 快完成"的进度口径 **不成立**。
3. **BE 实际 0 个 commit 落 main**（feature/pmo-37-metadata-strategy 上的改动依然 untracked/uncommitted）。
4. **T1/T10 (FE) 实际 0 代码行**，ConnectionsTab 里 rowCount 是旧 I3 commit (647014a) 的存量，**不是** PMO-37 交付。

I1 [P1] 进度蒸馏失真
- 触发: 主 Agent 22:11:38 后向 SM 报到 "BE 完成, T10 快完成, 可以关 PMO-37"
- grep 证据 (工作区):
    grep -rn "collect-async|collect-status|catalog/|metadataStrategy|countMethod" ecos_frontend/src
    → 无任何匹配到 PMO-37 FE 逻辑 (全部命中是 mockData/CommandPalette 旧代码)
    ConnectionsTab.tsx 的 rowCount L255/L390 是 I3 commit 647014a 的存量
- 判定: flow_violation (进度失真)
- 处置: 主 Agent 立即重报到 PMO + SM 出正式进度 sheet, FE T1/T10/T9 三行必须全部改回「未开始」
- 补偿: PMO 收到后 10 分钟内修正"PMO-37 T0-T8 完成, T9-T11 未开始, 编译通过 (boot+gateway 待验)"; 不接受"finishing/快完了/差不多了"等模糊口径

I2 [P1] 编译验证范围不足
- 主 Agent 实际跑: mvn -q compile -pl engine/data-engine/data-engine-impl -am  → EXIT=0 (/tmp/pmo37-c3.log 22:44:57)
- 但 PMO-37 修改: DataEngineApplication.java (@EnableAsync) @ data-engine-boot  ;  V105/V106 SQL @ gateway/migration
- -am 只含**上游**依赖 (common-api/api/runtime-task/runtime-access), 不含 boot 与 gateway
- 结论: "编译过" 凭证 **不成立**, T0-T8 编译门禁**未闭环**
- 处置: 主 Agent 执行情况:
    mvn -q compile -Dmaven.test.skip=true -pl engine/data-engine/data-engine-impl,engine/data-engine/data-engine-boot,gateway -am
    把 EXIT=0 完整日志 tail 提级入 PMO 门签凭证; 单次验收即可, 不用跑 boot 启动

I3 [P1·回归] monitor 未挂
- channel 里 PMO 已收到 "SM-1 关闭" 通报, 但 cronjob list 里 **没有 pmo37-monitor**
- 谭总原话 (2h smoke + 每 2h 进度/diff/完成度 + T 完了不再通知)**不可逆**
- 走另一条 channel 归 SM 处置 (Commander 不越位 SM 的 single client 授权)
- 重申: I1/I2 补齐前, **主 Agent 不得对 PMO 二次出 "PMO-37 完成" 口径**, 防二次失真

主 Agent 动作建议 (今 10 点前):
1. I2 全量编译 (10 分钟): mvn -q compile -pl impl,boot,gateway -am, 把 EXIT=0 打包回 PMO
2. I1 补提交: BE PMO-37 改动 (V105/V106 SQL + metadata/ 11 文件 + controller 修改 + Repository 修改) 分 2-3 个 commit 落到 feature/pmo-37-metadata-strategy 或 main, 每个 commit 附 "PMO-37 T#-标题" 前缀
3. FE: 按 PMO-37 说明做 T9 (ConnectionsTab 策略 UI) + T10 (CatalogTree 接 /catalog) + T11 (集测), 每个 T 独立 commit
4. 提交后向 PMO 回填 BE/FE checklist, **每格**要么 "commit hash" 要么 "blocked: 原因" 二选一, 不接受 "paragraph 完成"

Commander 承诺:
- 本次之后的下一轮复验窗口 (赶下一轮 22h smoke 时点) 内重验 I1/I2; I1 若未落 commit, I1 升级成 P1 ≥ 2, 直接闭环到肖总前端
- I3 由 SM 侧 channel 处置, Commander 只复核 cronjob list 结果

— 负责人 (Commander) / 复核完毕
