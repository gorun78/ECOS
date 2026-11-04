package com.chinacreator.gzcm.runtime.access.git.archive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 可选归档器的**强类型门**（§2.9.9 VG-5 收口；#147BL）。
 *
 * <p><b>为什么需要它</b>：四个归档调用方（ai agent / cognitive model / kb rule / workspace scenario）
 * 各自写一段「从 {@code ObjectProvider} 取归档器 → 为 null 就记日志跳过」。跳过是**无返回值**的，
 * 于是「归档腿没装配」这件事只在日志里，调用链上拿不到状态——正是 VG-5 记的
 * 「未装配即静默跳过而非 {@code not_available}」。</p>
 *
 * <p>本类把这段收敛成一处：缺席时返回 {@code not_available}（不是伪成功，也不是什么都不返回），
 * 同时打 WARN 保证可见；在场时原样委托。调用方据此按状态分态处理，各域不再各写一份判空。</p>
 *
 * @author ECOS runtime #147BL
 */
public final class OptionalArchiver {

    private static final Logger log = LoggerFactory.getLogger(OptionalArchiver.class);

    /** 缺席原因码（机读前缀；后接可读说明）。 */
    public static final String CODE_NOT_ASSEMBLED = "ARCHIVER_NOT_ASSEMBLED";

    private final DesignAssetArchiver archiver;

    private OptionalArchiver(DesignAssetArchiver archiver) {
        this.archiver = archiver;
    }

    /** 从 Spring 可选依赖取归档器（provider 本身可为 null）。 */
    public static OptionalArchiver of(ObjectProvider<DesignAssetArchiver> provider) {
        return new OptionalArchiver(provider == null ? null : provider.getIfAvailable());
    }

    /** 直接持有实例的调用方（如控制器字段注入）用本工厂。 */
    public static OptionalArchiver ofNullable(DesignAssetArchiver archiver) {
        return new OptionalArchiver(archiver);
    }

    public boolean available() {
        return archiver != null;
    }

    /**
     * 归档一次。
     *
     * <p>归档器缺席 ⇒ {@code not_available} + WARN（证据：reason 以 {@link #CODE_NOT_ASSEMBLED} 开头），
     * <b>绝不返回伪成功的 {@code committed}/{@code archived}</b>。</p>
     */
    public ArchiveOutcome archive(String repositoryId, String assetType, String assetId,
                                  String versionNo, String content, String commitMessage,
                                  String authorName) {
        if (archiver == null) {
            log.warn("归档腿 NOT_AVAILABLE（{}：DesignAssetArchiver 未装配，禁读成已归档）"
                    + " repositoryId={} assetType={} assetId={} versionNo={}",
                    CODE_NOT_ASSEMBLED, repositoryId, assetType, assetId, versionNo);
            return ArchiveOutcome.notAvailable(
                    CODE_NOT_ASSEMBLED + ": DesignAssetArchiver 未装配（禁读成已归档）");
        }
        return archiver.archive(repositoryId, assetType, assetId, versionNo, content, commitMessage, authorName);
    }
}
