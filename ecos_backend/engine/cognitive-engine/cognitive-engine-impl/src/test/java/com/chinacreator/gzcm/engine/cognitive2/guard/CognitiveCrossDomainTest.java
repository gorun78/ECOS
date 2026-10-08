package com.chinacreator.gzcm.engine.cognitive2.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F05-13/X-31 跨域写消除（设计-05 冻结验收名）：场景认知契约面为<b>只读面</b>（B-2 铁律），
 * 禁出现任何 DML（INSERT/UPDATE...SET/DELETE）；跨域写 {@code sysman.sys_config} /
 * {@code kb.sys_compliance_rule} 的 legacy 消除属 F05-13 M1 已登记项（C111/C112），
 * 不在本批 M0 面内（口径与 {@link CognitiveIronLawsTest} 一致）。
 *
 * <p><b>REQ-COG-07 唯一受控例外</b>（本批 #86，非放宽）：{@code ModelRegistryDao} 是
 * ADR-8 明文允许的"模型资产落盘面"（推理结果仍零落盘），故豁免零 DML 规则，但收紧为
 * <b>DML 目标表白名单</b>——每条 INSERT/UPDATE/DELETE 必须 schema 限定地指向
 * {@code ecos_cognitive.ecos_cognitive_model}；未限定表名或写任何其他表 ⇒ 违例照旧 FAIL。
 * 该例外的依据 = PRD-05 §6.2 REQ-COG-07 规格 5（注册中心载体在 ecos_cognitive）+
 * V261 头注"应用侧待办"；其余 in-scope 文件维持零 DML 原判。</p>
 */
class CognitiveCrossDomainTest {

    private static final Pattern DML = Pattern.compile(
            "\\b(?:INSERT\\s+INTO|UPDATE\\s+[\\w.]+\\s+SET|DELETE\\s+FROM)\\b",
            Pattern.CASE_INSENSITIVE);

    /** 例外面逐条 DML 的目标表提取（三类语句各自成式，统一捕获表名）。 */
    private static final Pattern DML_TARGET = Pattern.compile(
            "(?i)\\b(?:INSERT\\s+INTO|DELETE\\s+FROM)\\s+([\\w.]+)|\\bUPDATE\\s+([\\w.]+)\\s+SET\\b");

    /** REQ-COG-07 模型资产唯一权威载体（schema 限定形态，V261 / ST07）。 */
    private static final String MODEL_ASSET_TABLE = "ecos_cognitive.ecos_cognitive_model";

    @Test
    @DisplayName("契约面零 DML：认知不写任何表（更不写他域表）")
    void cognitiveWritesNoOtherDomainTables() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (var walk = CogScope.streamInScope()) {
            walk.forEach(p -> {
                try {
                    String src = java.nio.file.Files.readString(p);
                    if ("ModelRegistryDao.java".equals(p.getFileName().toString())) {
                        // 受控例外：DML 允许，但每条必须写 ecos_cognitive.ecos_cognitive_model（含未限定表名）
                        var t = DML_TARGET.matcher(src);
                        var d = DML.matcher(src);
                        if (!d.find() && t.find()) {
                            offenders.add(p.getFileName() + " :: DML 形态解析不一致（守卫自身失配）");
                        }
                        t.reset();
                        while (t.find()) {
                            String table = t.group(1) != null ? t.group(1) : t.group(2);
                            if (!MODEL_ASSET_TABLE.equalsIgnoreCase(table)) {
                                offenders.add(p.getFileName() + ":" + CogScope.lineOf(src, t.start())
                                        + " :: 模型资产面写了非权威表 " + table);
                            }
                        }
                        return;
                    }
                    var m = DML.matcher(src);
                    while (m.find()) {
                        offenders.add(p.getFileName() + ":" + CogScope.lineOf(src, m.start())
                                + " :: " + m.group().toUpperCase());
                    }
                } catch (IOException ignored) {}
            });
        }
        assertTrue(offenders.isEmpty(),
                "契约面出现写语义（违反只读面/跨域写禁令 X-31；模型资产面仅限权威表 "
                        + MODEL_ASSET_TABLE + "）：\n" + String.join("\n", offenders));
    }
}
