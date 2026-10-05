package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Git 仓库根路径单源：sys_config key ecos_git_repo_root（经 sysman api 门面读取）。 */
@Component
public class GitRepoRootResolver {

    private static final Logger log = LoggerFactory.getLogger(GitRepoRootResolver.class);

    public static final String KEY_REPO_ROOT = "ecos_git_repo_root";

    /**
     * {@code required=false}：跨 JAR 独立 boot 场景（datanet :18082 fat jar）里
     * sysman-impl 的 {@code SysConfigService} 不在 component-scan 范围内时 provider 给 null，
     * {@link #readConfig()} 直接落到 {@link #defaultRoot()}。
     */
    @Autowired(required = false)
    private SysConfigService sysConfigService;

    /** 单测/老调用便捷构造（保留 API 面；Spring 用默认 constructor + 字段注入）。 */
    public GitRepoRootResolver(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    public GitRepoRootResolver() {
        // Spring componentScan 场景：走 @Autowired(required=false) 字段注入
    }

    private String readConfig() {
        if (sysConfigService == null) {
            log.debug("GitRepoRootResolver: SysConfigService 门面不可用（跨 JAR 独立 boot 场景） → defaultRoot()");
            return null;
        }
        return sysConfigService.getString(KEY_REPO_ROOT);
    }

    public String resolveRepoRoot() {
        String configured = readConfig();
        String root = (configured == null || configured.trim().isEmpty()) ? defaultRoot() : configured.trim();
        return requireSafeRoot(root);
    }

    /**
     * F02-12（详细设计-02 §二.12）Git 归档加固：仓库根 **存在性** + **写权限** 校验 +
     * {@code toRealPath} symlink 解析后再做 {@code startsWith} 判定。
     * <p>供归档 / pipeline 归档 / datanet Git 操作等写侧调用；仓库尚未初始化（存在性不成立）
     * 时抛 {@link IllegalArgumentException}，由调用方 catch 后报业务异常（含 traceId），
     * 不裸 500。已在树-mount 后调用者可直接调用 {@link #resolveRepoRoot()}。</p>
     */
    public String resolveExistingRepoRoot() {
        String cfg = readConfig();
        String root = (cfg == null || cfg.trim().isEmpty()) ? defaultRoot() : cfg.trim();
        return requireSafeRootExisting(root);
    }

    /** repositoryId/repoId → 严格存在校验后的仓库根下的单层目录（F02-12 加固路径）。 */
    public String resolveApprovedRepoPath(String repoId) {
        String root = resolveExistingRepoRoot();
        Path base = canonicalBase(Paths.get(root));
        Path target = base.resolve(repoId).normalize();
        requireSafeSegment(repoId);
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("路径越出仓库根范围: " + repoId);
        }
        return target.toString();
    }

    /** 现有 {root / segments...} 语义，追加存在性校验（供 resolveUnderRoot 的调用方 await 使用）。 */
    public String resolveUnderRootExisting(String... segments) {
        String root = resolveExistingRepoRoot();
        Path base = canonicalBase(Paths.get(root));
        Path target = base;
        for (String s : segments) {
            requireSafeSegment(s);
            target = target.resolve(s);
        }
        target = target.normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("路径越出仓库根范围: " + String.join("/", segments));
        }
        return target.toString();
    }

    /** repositoryId/repoId → 仓库根下的单层目录，拒绝路径穿越 */
    public String resolveRepoPath(String repoId) {
        return resolveUnderRoot(repoId);
    }

    public String resolveUnderRoot(String... segments) {
        Path base = Paths.get(resolveRepoRoot());
        Path target = base;
        for (String s : segments) {
            requireSafeSegment(s);
            target = target.resolve(s);
        }
        target = target.normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("路径越出仓库根范围: " + String.join("/", segments));
        }
        return target.toString();
    }

    /** 外部传入的本地路径必须落在仓库根范围内，否则拒绝 */
    public String requireInsideRoot(String localPath) {
        Path base = Paths.get(resolveRepoRoot());
        if (localPath == null || localPath.trim().isEmpty()) {
            throw new IllegalArgumentException("localPath 不能为空");
        }
        Path target;
        try {
            target = Paths.get(localPath.trim()).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("非法 localPath: " + localPath);
        }
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("localPath 越出仓库根范围: " + localPath);
        }
        return target.toString();
    }

    public static void requireSafeSegment(String segment) {
        if (segment == null || segment.trim().isEmpty()
                || segment.contains("..") || segment.contains("/") || segment.contains("\\")
                || segment.indexOf(':') >= 0) {
            throw new IllegalArgumentException("非法路径段: " + segment);
        }
    }

    static String requireSafeRoot(String root) {
        if (root == null || root.trim().isEmpty()) {
            throw new IllegalArgumentException("仓库根路径配置为空");
        }
        String trimmed = root.trim();
        if (trimmed.contains("..")) {
            throw new IllegalArgumentException("非法 repoRoot（包含 ..）: " + root);
        }
        Path p;
        try {
            p = Paths.get(trimmed);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("非法 repoRoot: " + root);
        }
        if (!p.isAbsolute()) {
            throw new IllegalArgumentException("repoRoot 必须为绝对路径: " + root);
        }
        return p.normalize().toString();
    }

    /**
     * F02-12 加固根解析：先走 {@link #requireSafeRoot} 做 shape 校验，再补
     * <ol>
     *   <li>{@link Files#exists} / {@link Files#isDirectory} —— 目录不存在即拒；</li>
     *   <li>{@link Files#isWritable} —— 只读目录拒（归档无法写入）；</li>
     *   <li>{@code toRealPath} — symlink 解析后仍以解析后位置为准（防 symlink 逃逸根外）。</li>
     * </ol>
     * 返回 **已解析后的真实路径**（无 symlink 回旋）。
     */
    static String requireSafeRootExisting(String root) {
        String r = requireSafeRoot(root);
        Path p = Paths.get(r);
        if (!Files.exists(p)) {
            throw new IllegalArgumentException("仓库根路径不存在: " + r);
        }
        if (!Files.isDirectory(p)) {
            throw new IllegalArgumentException("仓库根路径不是目录: " + r);
        }
        if (!Files.isWritable(p)) {
            throw new IllegalArgumentException("仓库根路径不可写: " + r);
        }
        try {
            return p.toRealPath().toString();
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("仓库根路径无法解析真实位置（含 symlink）: " + r);
        }
    }

    /** symlink-safe 基线路径：源不存在（首次归档建库前）→ 用规范化；存在 → 解析到真实位置。 */
    private static Path canonicalBase(Path p) {
        try {
            if (Files.exists(p)) {
                return p.toRealPath();
            }
        } catch (java.io.IOException ignored) {
            // 继续走规范化路径（首启构建前 root 目录可能尚未建立）
        }
        return p;
    }

    private static String defaultRoot() {
        return Paths.get(System.getProperty("user.home"), "ecos-git-repos").toString();
    }
}
