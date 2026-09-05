#!/bin/bash
cd /home/guorongxiao/ECOS
# Step 4: commit with 规范 message (type = feat, module = ecossys, 动宾 = 发布)
git commit -m "feat(ecossys): Wave-4.2/5.1/5.3 release — 4×P0 fix + 9 engine 单测 + 12 AGENTS sub + 24 risk 横向 Soak G2 GO

Wave-4.2 全交付 (P0-2/3/4/5 fix + QA v7 50/32 + Soak 段1-2 G2 GO):
- P0-1 CachedBody/QuotaFilter (V4.0 6 段)
- P0-3' KDoc + searchByLabelPattern (PG 扩协议推不出参类型)
- P0-4 ComplianceRule EXTRACT(EPOCH)*1000 (TIMESTAMP → long)
- P0-5 V50 migration 补

Wave-5.1 5 engine 314 单测 (0/314 error, 13 module mvn test BUILD SUCCESS 7:39 min):
- security-engine-impl 11 class / 40 case (P0 双向 RLS / PDP / hash / 脱敏)
- data-engine-impl 6 class / 26 case (P0-2 TransformStatistics 4 字段)
- kb-engine-impl 8 class / 60 case (P0-3 search / P0-4 compliance / RAG)
- cognitive-engine-impl 13 class / 70 case (traverseKgChain + 5 Contract)
- ai-engine-impl 6 class / 36 case (LLMProvider mock + AgentMemory + SSE)

Wave-5.3 T-15 12 子模块 AGENTS.md (6 引擎 × api/impl/boot):
- 全部 53-60 行 (Wave-5.0 架构 铁律 5 项 × 12 = 60/60 pass)
- 0 硬编码 token/BOD/metadata + 0 new driver + 0 跨 engine

Wave-4.2 Wave-5.1 结合 Soak:
- 段1 (10 round × 7 domain × 6 min) G2 GO 3/3
- 段2 (14 round × 7 domain × 6 min) G2 GO 3/3
- memory leak 阈值 11 min OU Δ 0.01 pp << 5 pp
- GW PID 29949 全 14 rounds 不变

QA v7 50/32 = 86.2% (反映 Wave-4.2 P0 全 修 + Wave-5.1 mock 5 引擎)

完整 detail:
- docs/08-产品化重构方案/11-Wave5.1-单测 Warfare.
- md
- docs/08-产品化重构方案/12-Wave5.1-单测 kb-cognitive-ai.
- md
- docs/08-产品化重构方案/13-Wave5.3-AGENTS.md-12-previous-delivery.
- md
- docs/08-产品化重构方案/14-Wave5.3-AGENTS-12-reviewer-report.md
  (12/12 deliverable_allowed YES, 8.5/10, 0 blocker, 2 W-wave-5.4 待修)
- docs/7-integration/07-Wave4.2-72h-Soak-段1报告.md
- docs/7-integration/07-Wave4.2-72h-Soak-段2报告.md"
echo ""
echo "=== commit done ==="
git log -1 --oneline | head -n 1

echo ""
echo "=== Step 5: tag v2.0-alpha ==="
git tag -a v2.0-alpha -m "ECOS v2.0.0-alpha release
Wave-4.2/5.1/5.3 全交付
- 4 P0 全 修
- 314 单测 5 engine exit 0
- 12 子 AGENTS.md 交付 review 8.5/10
- Soak 段 1-2 G2 GO
QA v7 50/32 = 86.2% W/2 domain GATE

tag push:
  git push origin v2.0-alpha"
git show-ref tags/v2.0-alpha

echo ""
echo "=== Step 6: push branch + tag 到 origin ==="
git push origin release/v2.0-alpha 2>&1 | tail -n 10
git push origin v2.0-alpha 2>&1 | tail -n 10

echo ""
echo "=== verify  status ==="
git log --oneline -3
echo "Tags:"
git tag -l | tail -n 3
echo "END (exit=$?)"
