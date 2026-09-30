package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.springframework.stereotype.Component;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Git 仓库根路径单源：sys_config key ecos_git_repo_root（经 sysman api 门面读取）。 */
@Component
public class GitRepoRootResolver {

    public static final String KEY_REPO_ROOT = "ecos_git_repo_root";

    private final SysConfigService sysConfigService;

    public GitRepoRootResolver(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    public String resolveRepoRoot() {
        String configured = sysConfigService.getString(KEY_REPO_ROOT);
        String root = (configured == null || configured.trim().isEmpty()) ? defaultRoot() : configured.trim();
        return requireSafeRoot(root);
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

    private static String defaultRoot() {
        return Paths.get(System.getProperty("user.home"), "ecos-git-repos").toString();
    }
}
