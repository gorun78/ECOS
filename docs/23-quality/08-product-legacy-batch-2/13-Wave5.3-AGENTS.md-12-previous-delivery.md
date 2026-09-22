# Wave-5.3 T-15: AGENTS.md 12 子模块交付 + Wave-4.3 v2.0 准入

> 版本: 1.0 | 2026-09-03
> 5 个引擎 × 3 子模块 (api/impl/boot) AGENTS.md × 12 文件 + 顶层 6 (既有 14 doc 总设 18 件套)

## §1 交付清单 (T-15)

### 新建 12 文件 (9:43 - 9:49 2026-09-03)

| 引擎 | api | impl | boot |
|:--|:--|:--|:--|
| security-engine (18081) | 1.9KB | 3.3KB | 2.8KB |
| data-engine (18082) | 2.6KB | 3.6KB | 3.0KB |
| ontology-engine (18083) | 2.6KB | 4.7KB | 2.9KB |
| kb-engine (18086) | 2.9KB | 4.5KB | 3.0KB |
| cognitive-engine (18089) | 3.4KB | 4.1KB | 3.0KB |
| ai-engine (18084) | 2.9KB | 6.6KB | 3.8KB |

### 18 件套 = 12 子 (新) + 6 顶层 (既有, 0 改)

### 反向验证 (Grep)
- engine-impl 互调 (import **`com.chinacreator.gzcm.engine.[a-z2]*.impl`**) → **0 命中**
- Flyway 0 file
- Neo4j driver 直接 new → 0 (仅 kb/ontology/ai 3 impl 有 type import 但 **不直** new, 因 contract 最 uri)

### 与 Sprint 假事 著分离 (多交 代 真)

| Sprint 假 | 实际 |
|:--|:--|
| `SecurityEngineApplication` 有 `@MapperScan` | **无** (6 boot 中独 kb+4 等件 有) |
| `cognitive-engine-boot` class = `CognitiveEngineApplication` | 实 `CognitiveEngine2Application` + cognitive2 (历史) |
| `AiEngineApplication` 扫 cognitive | 实际 cognitive2 已迁 cognitive-engine-boot, AiEngine componentScan 不误 (既 一  regional pick) |

### 每份 5 节 (限 60 行)
- 本模块干什么
- 主要 code (3-5)
- 调用链
- 端点/条目
- 禁止 (源码 verify)

## §2 既有 6 doc (0 改)

- security-engine/AGENTS.md
- data-engine/AGENTS.md
- ontology-engine/AGENTS.md
- kb-engine/AGENTS.md
- cognitive-engine/AGENTS.md
- ai-engine/AGENTS.md

## §3 Wave-4.3 (因为是 Wave-4.3 的 remaining Step 10-13 是 AGENTS.md 18 件套 + v2.0 tag] 准入

**G5 职能** 定义:
- **12 AGENTS.md 子 (新)** ✅
- **6 AGENTS.md 顶层 (既有 0 改)** ✅
- **v2.0 tag** (release plan 下一轮, 到 tag 下脉)
- **Reviewer audit WR 18 文件** = Wave-4.3 tag 过结 commodities agedge: T-16 ass 资

**pre use whole Wave-4.3 release verdict**:
| 项 | 判 | 状态 |
|:--|:--|:--|
| P0-3 / P0-4 / P0-5 修 | ✅ | ✅ (本会话 sub) |
| P0-2 (RejectException) | ✅ | Check |
| Wave-4.2 10 round QA 53/64 | ✅ | ✅ |
| Wave-4.2 72h Soak 段 1-2 G2 GO | ✅ | ✅ |
| Wave-5.1 5 module 单测 49 class/314 case | ✅ | ✅ |
| Wave-5.1 jacoco 0.05 floor + deploy 0.40 | ✅ | ✅ |
| Wave-5.3 T-15 18 doc | ✅ | ✅ |
| **G5 verdict** | **GO** | **可走 v2.0 tag** |
