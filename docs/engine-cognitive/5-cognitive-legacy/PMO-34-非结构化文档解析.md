# PMO指令: ECOS 非结构化文档解析（借鉴 Semantica · P1-A）

> **来源**: 肖国荣 | **日期**: 2026-08-20
> **协同**: ECOS-ARCH（kb-engine 主责）
> **架构铁律**: 必须遵循 [ECOS架构铁律](../ARCHITECTURE-RULES.md)
> **关联**: 方案 `../ECOS-借鉴Semantica-完整方案.md`、差距分析 `../ECOS-借鉴Semantica-差距分析.md`

## 零、现状摸底（架构修正说明）

kb-engine `KnowledgeExtractionService` 已有"文档上传→解析→LLM抽取→审核→入库"全链路，但**解析是空壳**（已核实代码）：

```java
// parseFile() L154-166 现状：直接按 UTF-8 读字节，不解析 PDF/Word/Excel
private String parseFile(Path filePath) throws Exception {
    ...
    return new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
}
```

**架构修正**：原方案 v2 写"解析下沉到 data-engine"，但代码侦察发现 kb-engine 已有完整链路，搬移是破坏 + 重复建设。**修正为：非结构化文档解析在 kb-engine 就地升级**，用 Tika 替换 UTF-8 直读。

**KAG 定位**：这条"上传→解析→LLM抽取"链路就是 KAG 知识构建链路（`callAiExtraction` 一次出实体+关系+规则 = KAG Extractor，`ExtractedSubGraph` 是 KAG 核心模型）。本指令补的是 KAG 落地时"文档解析"这一环的空壳，**不引入 Semantica 的 entity-aware chunking**（KAG 用 LLM 抽取已替代）。

LLM 抽取已就绪（`callAiExtraction` 走 ai-engine Agent Loop，一次出实体+关系+规则），**不动**。

## 一、目标架构

parseFile 用 Apache Tika 真解析 PDF/Word/Excel/PPT/HTML/TXT，产出结构化文本 + 元数据，喂给已有的 LLM 抽取。

```
文件 → [Tika DocumentParserService] → 文本 + 元数据 → [LLM抽取(已有)] → 实体/关系/规则
```

## 二、分阶段执行计划（4 个 Task）

| Task | 文件/路径 | 操作 | 工期 |
|:-----|----------|------|:---:|
| T1 | `engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/service/DocumentParserService.java` | Tika 解析服务，支持 pdf/docx/xlsx/pptx/html/txt，返回 `{text, metadata}` | 1.5天 |
| T2 | `engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/service/KnowledgeExtractionService.java` | `parseFile()` 用 DocumentParserService 替换 UTF-8 直读 | 0.5天 |
| T3 | `engine/kb-engine/kb-engine-impl/pom.xml` | 加 `tika-parsers` 依赖（版本 2.9.x，`tika-core` 随行） | 0.5天 |
| T4 | `gateway/src/main/resources/db/migration/V104__ecos_extraction_meta.sql` | `extraction_drafts` 表加元数据列（file_type/page_count/char_count） | 0.5天 |

### T1 DocumentParserService 契约

```java
// 输入文件路径，返回解析结果
public class ParseResult {
    String text;        // 解析出的纯文本
    String fileType;    // pdf/docx/xlsx/pptx/html/txt
    int pageCount;      // 页数（PDF/Word 有，txt 为 1）
    int charCount;      // 字符数
}
// 方法：ParseResult parse(Path filePath)
// 用 Tika AutoDetectParser + BodyContentHandler 抽取文本
// 异常时抛 BusinessException("不支持的文件类型或解析失败: " + type)
```

### T2 替换点

```java
// KnowledgeExtractionService.parseFile() 改造前：
return new String(Files.readAllBytes(filePath), UTF_8);
// 改造后：
ParseResult r = documentParserService.parse(filePath);
return r.text;   // 元数据由调用方写 extraction_drafts
```

### T4 DDL

```sql
-- V104__ecos_extraction_meta.sql（只加列不删列）
ALTER TABLE extraction_drafts ADD COLUMN IF NOT EXISTS file_type VARCHAR(16);
ALTER TABLE extraction_drafts ADD COLUMN IF NOT EXISTS page_count INTEGER DEFAULT 1;
ALTER TABLE extraction_drafts ADD COLUMN IF NOT EXISTS char_count INTEGER DEFAULT 0;
```

## 三、禁止清单

1. **禁止新建 Maven 模块** — 落在 kb-engine 现有 api/impl/boot
2. **禁止动 `callAiExtraction`** — LLM 抽取已就绪，只改解析，不碰抽取
3. **禁止把解析搬移到 data-engine** — 就地升级，避免重复建设（见§零架构修正）
4. **禁止引入 OCR** — 扫描件 PDF 无文本层本期不做，`parse()` 返回空文本并标注 TODO
5. **Tika 依赖冲突时降级 PDFBox+POI，禁止硬塞** — 如 `tika-parsers` 与现有依赖冲突，改用 `pdfbox` + `poi-ooxml` 两个轻量依赖
6. **禁止跨 Phase 预创建文件** — 只做文档解析，KG 分析等留后续指令（**GraphRAG 分块策略不抄**，KAG 已用 LLM 抽取替代）

## 四、风险与回滚

- **风险1**：Tika 依赖重（transitive 多），可能和现有依赖冲突 → 降级 PDFBox+POI（见禁止清单5）。
- **风险2**：扫描件 PDF 无文本层 → 返回空文本，标注 TODO，不影响流程（LLM 抽取空文本直接标记失败）。
- **回滚**：新增 DocumentParserService 删除 + parseFile 还原一行即可。

## 五、工时估算

| Task | 工期 |
|------|:---:|
| T1 解析服务 | 1.5天 |
| T2 替换 | 0.5天 |
| T3 依赖 | 0.5天 |
| T4 元数据 | 0.5天 |
| **合计** | **3天** |

## 交付检查清单

| 验收项 | 命令 | 期望 |
|--------|------|------|
| V1 编译 | `env -i HOME=/home/guorongxiao PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -pl engine/kb-engine/kb-engine-impl -am -DskipTests -q'` | BUILD SUCCESS |
| V2 上传解析 | 通过 `ExtractionController` 上传一个真实 PDF，查 `extraction_drafts.parsed_text` | parsed_text 非空（非乱码/非原始字节） |
| V3 元数据 | 查 `extraction_drafts` 的 file_type/page_count/char_count | file_type=pdf，page_count>0 |
| V4 回归 | 上传一个 TXT 文件，确认原 UTF-8 直读行为不回归 | TXT 仍正常解析 |

## 一句话给 PMO

kb-engine 的 parseFile 现在是"按字节直读"，PDF/Word 根本解析不了——用 Tika 就地升级成真文档解析，别动已经就绪的 LLM 抽取。
