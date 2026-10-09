package com.chinacreator.gzcm.workspace.scenario;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 向导范围预填服务端（卷07 F07-14-2，单源原则：默认值由服务端算，禁前端从绑定自推）。
 * <p>范围来自场景绑定 + 归属比例表（属主 PRD-02）；归属比例经 {@link ScopeDefaultsProvider} 抽象（跨册接缝，
 * 未装配时给空默认，诚实缺省而非前端造数据）。coverageRatio 消费 {@link ScenarioCompletenessService} 单源。</p>
 */
@Service
public class ForecastScopeService {

    /** 归属比例表读取抽象（PRD-02 属主，未装配则返回空默认）。 */
    @FunctionalInterface
    public interface ScopeDefaultsProvider {
        ScopeDefaults forScenario(String scenarioId, Set<BindingType> boundTypes);
    }

    /** 归属比例快照。 */
    public static final class ScopeDefaults {
        private final List<String> periods;
        private final List<String> projectIds;
        private final List<String> departmentIds;
        private final String caliberId;
        private final long requiredFactCells;

        public ScopeDefaults(List<String> periods, List<String> projectIds,
                             List<String> departmentIds, String caliberId, long requiredFactCells) {
            this.periods = periods == null ? List.of() : periods;
            this.projectIds = projectIds == null ? List.of() : projectIds;
            this.departmentIds = departmentIds == null ? List.of() : departmentIds;
            this.caliberId = caliberId;
            this.requiredFactCells = requiredFactCells;
        }

        public static ScopeDefaults empty() {
            return new ScopeDefaults(List.of(), List.of(), List.of(), null, 0);
        }

        public List<String> getPeriods() {
            return periods;
        }

        public List<String> getProjectIds() {
            return projectIds;
        }

        public List<String> getDepartmentIds() {
            return departmentIds;
        }

        public String getCaliberId() {
            return caliberId;
        }

        public long getRequiredFactCells() {
            return requiredFactCells;
        }
    }

    private final CompletenessDataLoader loader;
    private final ScenarioCompletenessService completenessService;
    private final ObjectProvider<ScopeDefaultsProvider> providerHolder;

    public ForecastScopeService(CompletenessDataLoader loader,
                                ScenarioCompletenessService completenessService,
                                ObjectProvider<ScopeDefaultsProvider> providerHolder) {
        this.loader = loader;
        this.completenessService = completenessService;
        this.providerHolder = providerHolder;
    }

    public ForecastScopeVO build(String scenarioId) {
        List<BindingNode> bindings = loader.bindingsOf(scenarioId);
        List<LinkEdge> links = loader.linksOf(scenarioId);
        Double coverage = completenessService.compute(scenarioId, bindings, links).getCoverage();
        ScopeDefaultsProvider provider = providerHolder.getIfAvailable();
        ScopeDefaults defaults = provider == null
                ? ScopeDefaults.empty()
                : provider.forScenario(scenarioId, typeSet(bindings));
        return compose(scenarioId, defaults, coverage);
    }

    /** 纯组合入口（可测，隔离 IO）：范围默认值来自 provider + coverageRatio 来自完整度单源。 */
    public ForecastScopeVO compose(String scenarioId, ScopeDefaults defaults, Double coverage) {
        ScopeDefaults d = defaults == null ? ScopeDefaults.empty() : defaults;
        return new ForecastScopeVO(scenarioId, d.getPeriods(), d.getProjectIds(),
                d.getDepartmentIds(), d.getCaliberId(), coverage, d.getRequiredFactCells());
    }

    private Set<BindingType> typeSet(List<BindingNode> bindings) {
        Set<BindingType> set = EnumSet.noneOf(BindingType.class);
        for (BindingNode b : bindings) {
            if (b.getType() != null) {
                set.add(b.getType());
            }
        }
        return set;
    }
}
