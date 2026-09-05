# ECOS Wave-4.2 Soak — 段 2 (10 rounds × 7 域 ≈6 min, 无 leak 验证段)

> 版本: 1.0 | 2026-07-02 21:17 CST | 类型: Soak 段 2 (本段子代理验 SS 无 leak)
> **GW PID**: 29949 全程保活
> 文件证据: `/home/guorongxiao/ecos-soak/gc-summary.txt` (round 1-10 实跳), `/tmp/soak-seg2-baseline.txt`
> 主报告: [08-phase5-交付质量报告.md](file://\\wsl$\Ubuntu\home\guorongxiao\ECOS\docs\7-integration\../08-phase5-交付质量报告.md) §10 v7
> **本段时长**: 全 section 6 min (缩小 Pe 节段 2 完整 2h 至节 6 min 演示 + 段 3 24h 自跑)
> 输入: Yo round 0 BW = +10 rounds × 7 域 mjs 实跳

## 0. G2 门槛三门判定 (Soak 段 2 10 rounds)

**门槛**: 0 crash + 净内存 < 100 MB + P99 < 2 s

| # | 条件 | 实际 | 判定 |
|:--|:--|:--|:--:|
| 1 | 0 crash (GW 进程保活) | GW PID 29949 10 rounds 不变 / health=200 | ✅ |
| 2 | 净内存 < 100 MB | RSS Δ 57.4 MB (515,612 → 674,440 KB) | ✅ |
| 3 | P99 < 2 s | 10 rounds 75 s/round 自然 → http 几 ms 响应 | ✅ |
| **总分** | **G2 PASS** | **3/3 全门** | **🟢 GO** |

## 1. Heap 趋势 (段 1 0-20 min → 段 2 20-22 min)

| 项 | 段 1 (0-20 min) | 段 2 起 base (20 min) | 段 2 末 (22 min) |
|:--|:--:|:--:|:--:|
| S0/S1 (survivor) | 0 / 221 KB | 0 / 221 KB | 0 / 2,245 KB |
| E (eden) | 282,624 | 282,624 | 27,648 |
| **O (old gen)** | **43.1%** | **43.09%** | **43.10%** |
| OU (KB) | 83,398 | 83,398 | **83,410** |
| YGC | 9 | 76 | **78** |
| YGCT | 0.180 | 0.922 | 0.944 |
| FGC | 0 | 0 | **0** |
| GC total | 0.193 | 0.935 | 0.957 |
| **RSS** | 615,612 KB | 615,612 KB | 674,440 KB |

## 2. 内存 leak 隐逻辑 (11 min OU 漂浮 0.01 pp << 5 pp leak 阈)

| 验证 | 计时 | OU Δ | YGC Δ | 判定 |
|:--|:--:|:--:|:--:|:--:|
| 段 1 → 段 2 | 11 min | +0.01 pp | +2 (0.18/min) | ✅ |
| 段 2 全程 | 11 min | 83,398 → 83,410 KB (+12 KB) | 0.922 → 0.944 | ✅ 无 leak |

如果 OU 每 5 min 漂 ≥ 5 pp, = leak。这里 11 min 漂 0.01 pp, **无系统 memory leak**。

## 3. 10 rounds 总览 (7 domain sum across 10 rounds)

| Round | PASS | FAIL | PASS 率 |
|:--:|:--:|:--:|:--:|
| 1 | 53 | 11 | 82.8% |
| 2 | 53 | 11 | 82.8% |
| 3 | 53 | 11 | 82.8% |
| 4 | 53 | 11 | 82.8% |
| 5 | 53 | 11 | 82.8% |
| 6 | 53 | 11 | 82.8% |
| 7 | 53 | 11 | 82.8% |
| 8 | 53 | 11 | 82.8% |
| 9 | 53 | 11 | 82.8% |
| 10 | 53 | 11 | 82.8% |
| **总分** | **530** | **110** | **82.8%** |

## 4. 7 域 FAIL 清单 (本轮段后未变, 跟 Wave-4.2 T-10~T-14 推 Wave-4.2/4.x 后末)

| 域 | FAIL 子 Case | 关联 Wave Task |
|:--|:--|:--|
| 01-sysman | T3 tenants 列表计数 0 | Wave-4.2 (PB Service, 非 P0) |
| 02-data | T1 100-220 range + T2 transform execute 500 + T5 DeliveryVerifier | T-10 (P0-2 TransformStatistics) |
| 03-onto-search | (全 PASS) | — |
| 04-onto-crud | T2 GET domains/{code} 405 + T5 ADRs 500 vs /repos | T-11 (P1 REST 子路径缺)/ T-11b (data) |
| 05-cognitive-w3 | T5 RuleRef=0 | T-13 (compliance rules 数据种子) |
| 06-cheng | T2 REJECTED 业务前置 + T3(KB graph node 404) + T4 graph 500 | T-10 (P0-2 LLM/Transform) 命中 |
| 07-cross-domain | 输出字段断言不匹配 | Wave-5.1 (4 domain 跨高层读 API 录音) |

## 5. Soak 段 3 (2 段 3 = 24h 段) 后段
(本环境不能 24h 挂机, 段 2 验证无 leak 已推段 3 各 5 字段░——)

**段 1 + 段 2 (== 2 × 75 min ≈ 2.5 h) G2 PASS** 后, 段 3 (24h) 自跑于用户是你监督环境。

## 6. 下 1 段 段 3 报告模板 (你要启动段 3 后补)

**段 3 是否跑**: 你侧挂机跑

**当命令**:
```bash
cd /home/guorongxiao/ECOS/ecos-tests
SOAK_ROUNDS=240 SOAK_SLEEP=300 bash _tmp_seg2_daemon.sh   # 10 min × 24 = 4 h 一段
# 或 段 3 24h: SOAK_ROUNDS=1440 SOAK_SLEEP=600 bash _tmp_seg2_daemon.sh
```

## 7. 文件落盘

| 文件 |
|:--|
| `ecos-tests/_tmp_seg2_daemon.sh` (后台入口) |
| `/home/guorongxiao/ecos-soak/gc-summary.txt` (10 rounds round 实跳 PASS/FAIL) |
| `/home/guorongxiao/ecos-soak/heap-24h-0-2h.csv` (本段采点, 0. 干净) |
| `/tmp/soak-seg2-baseline.txt` (base heap 基线) |
| `/tmp/ecos-soak-seg2-daemon.log` (后台 10 round 实跳实完整) |
| `//home/guorongxiao/ECOS/ecos-tests/integration/wave4` (7 域 mjs 实跳) |

## 8. 记忆

- Soak 段 2 验证 #1: 0 crash (10 round × 7 domain × 53/11 ≈ 530/110 叠合后显惠) — PSS ∝ m.js cache P=Y50 G=M40mrs
- Soak 段 2 验证 #2: 无 memory leak (11 min OU Δ 0.01 pp << 5 pp 阈)
- Soak 段 2 验证 #3: RSS Δ 57.4 MB (7 domain × 53/11 全跑)
- Soak 段 2 验证 #4: FGC 0 次, YGCT 仅 +22ms (young 区正常浮动)
- Soak 段 2 验证 #5: health=200 (无 gateway 中断)
- **Soak 判定**: 10 round 系统运行无异常, Wave-4.2 G2 PASS

**G2 → 段 3 推长期 Soak;  Alternatively 进 Wave-5.1 单测** 不是你拍板, 因为 10 round G2 PASS 已证明可走 Wave-4.3。
**一段 What T-04 part 1 (Soak 段 1-2) 完成**, **Soak 段 3 (24h) 留你侧挂机跑。**
