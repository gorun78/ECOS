package com.chinacreator.gzcm.runtime.access.git.publish;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 对账输入（§2.9.6③ 的<b>证据面</b>载体；#147D）。
 *
 * <p>{@link PublishReconciler} 只做判定、不做取数：把「拿什么比」与「怎么比」分开，
 * 是为了让判定表可被单测逐枚钉住，而不必真连仓库与库。</p>
 *
 * <p><b>{@code null} 的语义是「证据未取到」，不是「无差异」</b>——本类所有可选证据都用
 * 包装类型／集合空位表达缺失，缺失项由 {@link PublishReconciler} 落成
 * {@link ReconciliationVerdict#UNVERIFIABLE}。这是本批对假绿的正面处置：收口前
 * 「读不到仓库侧内容 ＝ 无差异」的写法，在判定表里没有对应出口。</p>
 *
 * <p>不可变值对象；用 {@link #builder()} 构造。</p>
 *
 * @author ECOS Runtime底座 #147D
 */
public final class ReconciliationInput {

    /** 权威库侧对象标识集合；{@code null} = 未取到（不参与「一致」的成立）。 */
    private final Set<String> authoritativeObjectKeys;

    /** 仓库快照侧对象标识集合；{@code null} = 未取到。 */
    private final Set<String> repositoryObjectKeys;

    /** 权威库侧内容摘要；{@code null} = 未取到。 */
    private final String authoritativeContentDigest;

    /** 仓库快照侧内容摘要；{@code null} = 未取到。 */
    private final String repositoryContentDigest;

    /** 版本引用是否对齐；{@code null} = 未取到判定。 */
    private final Boolean versionRefMatched;

    /** 依赖是否对齐；{@code null} = 未取到判定。 */
    private final Boolean dependencyMatched;

    /** 本次发布的三腿记录；{@code null} = 无记录（无从判定）。 */
    private final DualWriteOutcome outcome;

    private ReconciliationInput(Builder b) {
        this.authoritativeObjectKeys = b.authoritativeObjectKeys == null
                ? null : Collections.unmodifiableSet(new LinkedHashSet<>(b.authoritativeObjectKeys));
        this.repositoryObjectKeys = b.repositoryObjectKeys == null
                ? null : Collections.unmodifiableSet(new LinkedHashSet<>(b.repositoryObjectKeys));
        this.authoritativeContentDigest = trimToNull(b.authoritativeContentDigest);
        this.repositoryContentDigest = trimToNull(b.repositoryContentDigest);
        this.versionRefMatched = b.versionRefMatched;
        this.dependencyMatched = b.dependencyMatched;
        this.outcome = b.outcome;
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public Set<String> authoritativeObjectKeys() {
        return authoritativeObjectKeys;
    }

    public Set<String> repositoryObjectKeys() {
        return repositoryObjectKeys;
    }

    public String authoritativeContentDigest() {
        return authoritativeContentDigest;
    }

    public String repositoryContentDigest() {
        return repositoryContentDigest;
    }

    public Boolean versionRefMatched() {
        return versionRefMatched;
    }

    public Boolean dependencyMatched() {
        return dependencyMatched;
    }

    public DualWriteOutcome outcome() {
        return outcome;
    }

    /** 双侧对象标识集合是否都取得到（空集合算取得到，{@code null} 不算）。 */
    public boolean hasObjectKeyEvidence() {
        return authoritativeObjectKeys != null && repositoryObjectKeys != null;
    }

    /** 双侧内容摘要是否都取得到。 */
    public boolean hasContentEvidence() {
        return authoritativeContentDigest != null && repositoryContentDigest != null;
    }

    /** 判定「一致」所需的六项证据是否全在（缺任一项即只能 {@link ReconciliationVerdict#UNVERIFIABLE}）。 */
    public boolean hasFullEvidence() {
        return hasObjectKeyEvidence() && hasContentEvidence()
                && versionRefMatched != null && dependencyMatched != null;
    }

    /** 可变构造器。 */
    public static final class Builder {
        private Set<String> authoritativeObjectKeys;
        private Set<String> repositoryObjectKeys;
        private String authoritativeContentDigest;
        private String repositoryContentDigest;
        private Boolean versionRefMatched;
        private Boolean dependencyMatched;
        private DualWriteOutcome outcome;

        private Builder() {
        }

        public Builder authoritativeObjectKeys(Set<String> keys) {
            this.authoritativeObjectKeys = keys;
            return this;
        }

        public Builder repositoryObjectKeys(Set<String> keys) {
            this.repositoryObjectKeys = keys;
            return this;
        }

        public Builder authoritativeContentDigest(String digest) {
            this.authoritativeContentDigest = digest;
            return this;
        }

        public Builder repositoryContentDigest(String digest) {
            this.repositoryContentDigest = digest;
            return this;
        }

        public Builder versionRefMatched(Boolean matched) {
            this.versionRefMatched = matched;
            return this;
        }

        public Builder dependencyMatched(Boolean matched) {
            this.dependencyMatched = matched;
            return this;
        }

        public Builder outcome(DualWriteOutcome outcome) {
            this.outcome = outcome;
            return this;
        }

        public ReconciliationInput build() {
            return new ReconciliationInput(this);
        }
    }
}
