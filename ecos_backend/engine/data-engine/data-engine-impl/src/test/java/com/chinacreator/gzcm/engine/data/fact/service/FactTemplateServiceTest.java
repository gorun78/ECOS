package com.chinacreator.gzcm.engine.data.fact.service;

import com.chinacreator.gzcm.engine.data.fact.FactType;
import com.chinacreator.gzcm.engine.data.fact.model.FactTemplateVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 导入模板一致性 — 详细设计-02 D.2 getFactImportTemplate（PRD-02 §1.4）。
 *
 * <p>本类因线上 500 补建：COST/STAGE 示例行曾把可空列写成 {@code null}，
 * 而 {@link List#of} 拒收 null ⇒ 两型模板端点必 500（{@code List.of} 处 NPE）。
 * 既有测试从未调用 {@code FactTemplateService}，缺陷因此长期潜伏，
 * 故本类的价值在于「五型全跑 + 宽度对齐」这类断言必须先能变红再被修绿。
 */
class FactTemplateServiceTest {

    private final FactTemplateService service = new FactTemplateService();

    /** 五型模板全部可取（可空列以空串而非 null 表达，禁 List.of NPE）。 */
    @Test
    void templateAllFactTypes() {
        for (FactType type : FactType.values()) {
            FactTemplateVO vo = service.template(type);
            assertNotNull(vo, "模板为空: " + type);
            assertEquals(type.name(), vo.factType());
            assertEquals(type.tableName(), vo.table());
            vo.exampleRowsSafe().forEach(row -> assertNotNull(row, "示例行为空"));
        }
    }

    /** 表头列数 = 单位说明数 = 每条示例行数（CSV 三元组必须同宽，否则导入模板不可用）。 */
    @Test
    void templateColumnWidthsAligned() {
        for (FactType type : FactType.values()) {
            FactTemplateVO vo = service.template(type);
            int headerCols = vo.headerCsv().split(",", -1).length;
            assertEquals(headerCols, vo.units().size(),
                    type + " 单位说明数与表头列数不符");
            assertEquals(2, vo.exampleRowsSafe().size(), type + " 示例行数应为 2");
            for (List<String> row : vo.exampleRowsSafe()) {
                assertEquals(headerCols, row.size(), type + " 示例行宽度与表头不符");
            }
        }
    }

    /** 必填清单只允许出现该型业务列白名单内的列（MC02 门禁同源）。 */
    @Test
    void templateRequiredWithinWhitelist() {
        for (FactType type : FactType.values()) {
            FactTemplateVO vo = service.template(type);
            for (String required : vo.required()) {
                org.junit.jupiter.api.Assertions.assertTrue(
                        type.columnWhitelist().contains(required),
                        type + " 必填列不在白名单: " + required);
            }
        }
    }
}
