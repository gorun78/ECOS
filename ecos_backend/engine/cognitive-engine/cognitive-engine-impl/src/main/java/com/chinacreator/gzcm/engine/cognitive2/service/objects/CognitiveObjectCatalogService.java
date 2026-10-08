package com.chinacreator.gzcm.engine.cognitive2.service.objects;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.objects.CognitiveObjectClass;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * C 层对象目录只读解析服务（详细设计-05 §8.1 接缝「提供对象目录与只读解析端点」）。
 *
 * <p>三层纪律（C.8）：本服务只从 {@link CognitiveObjectClass} 单源枚举装配视图，
 * <b>不含 SQL、不含业务计算</b>；目录之外的第二套对象清单即视为契约漂移。</p>
 *
 * <p>W Agent 侧消费口径：只读引用，禁在 {@code ecos_ai.ecos_wagent_*} 复制 C 对象定义（ADR-16/17）。</p>
 */
@Service
public class CognitiveObjectCatalogService {

    /** 全量目录（11 类，顺序 = 枚举声明序，稳定可diff）。 */
    public List<CognitiveObjectClass.CatalogEntry> catalog() {
        return java.util.Arrays.stream(CognitiveObjectClass.values())
                .map(CognitiveObjectClass::toEntry)
                .toList();
    }

    /**
     * 单对象只读解析（未登记对象名 ⇒ 400 fail-loud，禁静默返回空对象或默认载体）。
     */
    public CognitiveObjectClass.CatalogEntry resolve(String objectKey) {
        return CognitiveObjectClass.byKey(objectKey)
                .map(CognitiveObjectClass::toEntry)
                .orElseThrow(() -> new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                        CognitiveErrorCode.E400_INVALID_PARAM + ": 未登记的 C 层对象名 "
                                + (objectKey == null ? "<null>" : objectKey)
                                + "（新增类型须先改 PRD-05 §6.1 再入码）"));
    }
}
