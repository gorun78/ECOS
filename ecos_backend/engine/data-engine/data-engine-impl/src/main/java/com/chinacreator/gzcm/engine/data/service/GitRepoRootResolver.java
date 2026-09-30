package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Git 仓库根路径单源：sys_config key ecos_git_repo_root（经 sysman api 门面读取）。
 *
 * <p>详细设计-02 F02-12（P1，REQ-DATA-06 加固）：</p>
 * <ul>
 *   <li>① 根加载时存在性/目录性/可读性校验（不存在时 WARN 放行延迟建仓，入库写路径由
 *       {@link #resolveRepoRootOrThrow()} 严格校验）；</li>
 *   <li>② 边界判定改为 symlink 感知：逐段对已存在的目录做 {@link Files#toRealPath}
 *       解析并校验落在真实根内，堵住「根内 symlink 指向根外」的穿越；</li>
 *   <li>③ 拒绝统一抛 {@link BusinessException}（带明确消息 + httpStatus 400），不再裸 500。</li>
 * </ul>
 */
@Component
public class GitRepoRootResolver {

    private static final Logger log = LoggerFactory.getLogger(GitRepoRootResolver.class);

    public static final String KEY_REPO_ROOT = "ecos_git_repo_root";

    private final SysConfigService sysConfigService;

    public GitRepoRootResolver(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    public String resolveRepoRoot() {
        String configured = sysConfigService.getString(KEY_REPO_ROOT);
        String root = (configured == null || configured.trim().isEmpty()) ? defaultRoot() : configured.trim();
        String safe = requireSafeRoot(root);
        requireUsableRoot(Paths.get(safe));
        return safe;
    }

    /** 供需要实际进入的调用方（归档/提交）：根目录不存在则拒绝（F02-12① 存在性校验）。 */
    public String resolveRepoRootOrThrow() {
        String safe = resolveRepoRoot();
        Path root = Paths.get(safe);
        if (!Files.isDirectory(root)) {
            throw new BusinessException(400,
                    "Git 仓库根目录不存在或不可访问: " + safe
                            + "（F02-12：请先建仓或校正 sys_config:ecos_git_repo_root）");
        }
        return safe;
    }

    /**
     * 根加载期校验（F02-12①）：根存在但非目录/不可读 → 拒绝；
     * 根不存在（延迟建仓）时要求父目录存在且可读，WARN 放行。
     */
    private void requireUsableRoot(Path root) {
        if (Files.exists(root)) {
            if (!Files.isDirectory(root)) {
                throw new BusinessException(400, "ecos_git_repo_root 指向的不是目录: " + root);
            }
            if (!Files.isReadable(root)) {
                throw new BusinessException(400, "ecos_git_repo_root 目录不可读: " + root);
            }
            return;
        }
        Path parent = root.getParent();
        if (parent == null || !Files.isDirectory(parent) || !Files.isReadable(parent)) {
            throw new BusinessException(400, "ecos_git_repo_root 所在父目录不存在或不可读: " + parent);
        }
        log.warn("ecos_git_repo_root 尚未建仓（仅允许延迟建仓，首次 Git 写入时再严格校验）: {}", root);
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
        if (!withinRealRoot(base, target)) {
            throw new BusinessException(400, "路径越出仓库根范围: " + String.join("/", segments));
        }
        return target.toString();
    }

    /** 外部传入的本地路径必须落在仓库根范围内，否则拒绝 */
    public String requireInsideRoot(String localPath) {
        Path base = Paths.get(resolveRepoRoot());
        if (localPath == null || localPath.trim().isEmpty()) {
            throw new BusinessException(400, "localPath 不能为空");
        }
        Path target;
        try {
            target = Paths.get(localPath.trim()).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new BusinessException(400, "非法 localPath: " + localPath);
        }
        if (!withinRealRoot(base, target)) {
            throw new BusinessException(400, "localPath 越出仓库根范围: " + localPath);
        }
        return target.toString();
    }

    /**
     * symlink 感知边界判定（F02-12②）：
     * <ol>
     *   <li>逻辑层 normalize + startsWith 先行（{@code ..} 已由 {@link #requireSafeSegment} 排除）；</li>
     *   <li>从真实根出发逐段行走：任一段对应的现存目录做 toRealPath 后越出真实根 → 拒绝。
     *       不存在的段（尚未建仓的下级）按逻辑放行。</li>
     * </ol>
     */
    private boolean withinRealRoot(Path base, Path target) {
        Path tolerant = target.normalize();
        if (!tolerant.startsWith(base)) {
            return false;
        }
        try {
            Path realBase = base.toRealPath();
            Path pos = realBase;
            for (int i = base.getNameCount(); i < tolerant.getNameCount(); i++) {
                pos = pos.resolve(tolerant.getName(i).toString());
                if (Files.isSymbolicLink(pos) || Files.isDirectory(pos)) {
                    Path real = pos.toRealPath();
                    if (!real.startsWith(realBase)) {
                        log.warn("仓库路径段经 symlink 越出仓库根: logical={}, real={}", pos, real);
                        return false;
                    }
                    pos = real;
                }
            }
        } catch (IOException e) {
            // 根目录尚不可真实解析（延迟建仓）→ 逻辑层已校验且 .. 已排除
            log.warn("toRealPath 解析失败，按逻辑 normalize 边界判定: base={}, err={}", base, e.getMessage());
        }
        return true;
    }

    public static void requireSafeSegment(String segment) {
        if (segment == null || segment.trim().isEmpty()
                || segment.contains("..") || segment.contains("/") || segment.contains("\\")
                || segment.indexOf(':') >= 0) {
            throw new BusinessException(400, "非法路径段: " + segment);
        }
    }

    static String requireSafeRoot(String root) {
        if (root == null || root.trim().isEmpty()) {
            throw new BusinessException(400, "仓库根路径配置为空");
        }
        String trimmed = root.trim();
        if (trimmed.contains("..")) {
            throw new BusinessException(400, "非法 repoRoot（包含 ..）: " + root);
        }
        Path p;
        try {
            p = Paths.get(trimmed);
        } catch (InvalidPathException e) {
            throw new BusinessException(400, "非法 repoRoot: " + root);
        }
        if (!p.isAbsolute()) {
            throw new BusinessException(400, "repoRoot 必须为绝对路径: " + root);
        }
        return p.normalize().toString();
    }

    private static String defaultRoot() {
        return Paths.get(System.getProperty("user.home"), "ecos-git-repos").toString();
    }
}
