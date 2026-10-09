package com.chinacreator.gzcm.workspace.scenario;

import java.util.List;

/**
 * 状态迁移前置条件上下文（卷07 F07-07 / C-2）。
 * <p>{@code subject} 必须来自 JWT（服务端填充），迁移守卫绝不读取请求体自填的 userId（破 X-70）。</p>
 */
public final class TransitionContext {

    private final List<CompletenessReport.IslandView> islands;
    private final CompletenessReport.Verdict verdict;
    private final boolean cognitiveReachable;
    private final boolean formalRunExists;
    private final String subject;

    private TransitionContext(List<CompletenessReport.IslandView> islands, CompletenessReport.Verdict verdict,
                             boolean cognitiveReachable, boolean formalRunExists, String subject) {
        this.islands = islands == null ? List.of() : islands;
        this.verdict = verdict;
        this.cognitiveReachable = cognitiveReachable;
        this.formalRunExists = formalRunExists;
        this.subject = subject;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<CompletenessReport.IslandView> getIslands() {
        return islands;
    }

    public boolean isIslandsEmpty() {
        return islands.isEmpty();
    }

    public CompletenessReport.Verdict getVerdict() {
        return verdict;
    }

    public boolean isCognitiveReachable() {
        return cognitiveReachable;
    }

    public boolean isFormalRunExists() {
        return formalRunExists;
    }

    /** 来自 JWT 的迁移主体（守卫用；请求体 userId 一律忽略）。 */
    public String getSubject() {
        return subject;
    }

    public static final class Builder {
        private List<CompletenessReport.IslandView> islands = List.of();
        private CompletenessReport.Verdict verdict = CompletenessReport.Verdict.OK;
        private boolean cognitiveReachable = true;
        private boolean formalRunExists;
        private String subject;

        public Builder islands(List<CompletenessReport.IslandView> v) {
            this.islands = v;
            return this;
        }

        public Builder verdict(CompletenessReport.Verdict v) {
            this.verdict = v;
            return this;
        }

        public Builder cognitiveReachable(boolean v) {
            this.cognitiveReachable = v;
            return this;
        }

        public Builder formalRunExists(boolean v) {
            this.formalRunExists = v;
            return this;
        }

        public Builder subject(String v) {
            this.subject = v;
            return this;
        }

        public TransitionContext build() {
            return new TransitionContext(islands, verdict, cognitiveReachable, formalRunExists, subject);
        }
    }
}
