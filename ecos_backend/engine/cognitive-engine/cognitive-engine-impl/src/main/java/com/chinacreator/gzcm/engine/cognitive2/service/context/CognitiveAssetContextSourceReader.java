package com.chinacreator.gzcm.engine.cognitive2.service.context;

import com.chinacreator.gzcm.engine.cognitive.api.context.ContextBuildRequest;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextSourceRef;
import com.chinacreator.gzcm.engine.cognitive2.service.asset.CognitiveAssetLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * {@link ContextSourceReader} 的 C 资产面（详细设计-认知引擎 §4.3/§8）。
 *
 * <p>只读复用 {@link CognitiveAssetLifecycleService#resolve(String)}（同进程既有受控解析）；
 * 版本取资产侧 {@code version/updatedAt}，缺失即失败；资产不存在/非法前缀同样失败，
 * 不把“查不到”折叠成空成功。权限裁剪由资产服务与 Builder 承接。</p>
 */
@Component
public class CognitiveAssetContextSourceReader implements ContextSourceReader {

    private static final Logger log = LoggerFactory.getLogger(CognitiveAssetContextSourceReader.class);
    static final String SOURCE_TYPE_COGNITIVE = "COGNITIVE";

    private final CognitiveAssetLifecycleService lifecycleService;

    public CognitiveAssetContextSourceReader(CognitiveAssetLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @Override
    public boolean supports(String sourceType) {
        return SOURCE_TYPE_COGNITIVE.equals(sourceType);
    }

    @Override
    public SourceReadResult read(ContextBuildRequest request, ContextSourceRef ref) {
        if (ref == null || ref.getSourceId() == null || ref.getSourceId().isBlank()) {
            throw new ContextReadException(SOURCE_TYPE_COGNITIVE, null, "sourceId required");
        }
        Map<String, Object> asset;
        try {
            asset = lifecycleService.resolve(ref.getSourceId());
        } catch (RuntimeException e) {
            log.warn("cognitive asset resolve failed: id={} err={}", ref.getSourceId(), e.getClass().getSimpleName());
            throw new ContextReadException(SOURCE_TYPE_COGNITIVE, ref.getSourceId(),
                    "asset resolve failed: " + e.getMessage());
        }
        if (asset == null) {
            throw new ContextReadException(SOURCE_TYPE_COGNITIVE, ref.getSourceId(), "asset resolve returned null");
        }
        String version = firstText(asset, "version", "updatedAt", "updated_at", "versionNo", "version_no");
        if (version == null) {
            throw new ContextReadException(SOURCE_TYPE_COGNITIVE, ref.getSourceId(),
                    "asset exposes no version/updatedAt (request version must not be echoed)");
        }
        String status = firstText(asset, "status", "lifecycleState", "lifecycle_state", "state");
        SourceReadResult result = new SourceReadResult();
        result.setVersion(version);
        result.setAsOfTime(firstText(asset, "updatedAt", "updated_at", "createdAt", "created_at"));
        result.setAssetRefs(List.of(new AssetProjection(ref.getSourceId(), version, status,
                result.getAsOfTime())));
        result.setAuthoritative(false);
        result.setDenied(false);
        return result;
    }

    private static String firstText(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null && !String.valueOf(v).isBlank()) {
                return String.valueOf(v);
            }
        }
        return null;
    }
}
