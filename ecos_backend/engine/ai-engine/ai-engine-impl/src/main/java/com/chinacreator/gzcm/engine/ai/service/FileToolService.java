package com.chinacreator.gzcm.engine.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件工具服务 — 提供安全的文件读写、搜索和打补丁操作。
 *
 * <p>所有文件操作限制在 {@link #WORKSPACE_ROOT} 目录内，
 * 通过 {@link #resolveSafe(String)} 拒绝路径越界攻击（如 {@code ../../etc/passwd}）。
 * </p>
 */
@Service
public class FileToolService {

    private static final Logger log = LoggerFactory.getLogger(FileToolService.class);

    /**
     * 工作区根目录 — 所有文件操作限定在此目录下。
     */
    static final String WORKSPACE_ROOT = "/home/guorongxiao/ECOS/ecos-kb/agent-workspace";

    private static final int MAX_READ_LINES = 2000;
    private static final int MAX_SEARCH_RESULTS = 50;

    /**
     * 解析并安全检查路径：拒绝路径越界（{@code ../../etc/passwd} → SecurityException）。
     *
     * @param relativePath 用户传入的相对路径
     * @return 规范化后的绝对路径
     * @throws SecurityException 如果路径试图越界到工作区之外
     */
    Path resolveSafe(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new SecurityException("路径不能为空");
        }

        Path root = Paths.get(WORKSPACE_ROOT).toAbsolutePath().normalize();
        Path resolved = root.resolve(relativePath).normalize();

        if (!resolved.startsWith(root)) {
            throw new SecurityException("路径越界拒绝: " + relativePath);
        }
        return resolved;
    }

    // ─── readFile ──────────────────────────────────────────────────────

    /**
     * 读取文件指定行范围。
     *
     * @param path   相对于工作区的文件路径
     * @param offset 起始行号（1-based，默认 1）
     * @param limit  最大读取行数（默认 500）
     * @return 文件内容（带行号）和元信息
     */
    public ReadFileResult readFile(String path, int offset, int limit) {
        Path filePath;
        try {
            filePath = resolveSafe(path);
        } catch (SecurityException e) {
            return new ReadFileResult(false, e.getMessage(), "", 0, 0, 0);
        }

        if (!Files.exists(filePath)) {
            return new ReadFileResult(false, "文件不存在: " + path, "", 0, 0, 0);
        }
        if (!Files.isRegularFile(filePath)) {
            return new ReadFileResult(false, "路径不是文件: " + path, "", 0, 0, 0);
        }

        int startLine = Math.max(1, offset);
        int maxLines = (limit > 0 && limit <= MAX_READ_LINES) ? limit : 500;

        try {
            List<String> allLines = Files.readAllLines(filePath);
            int totalLines = allLines.size();

            if (startLine > totalLines) {
                return new ReadFileResult(true, null, "", totalLines, 0, totalLines);
            }

            int endLine = Math.min(startLine + maxLines - 1, totalLines);
            List<String> selected = allLines.subList(startLine - 1, endLine);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < selected.size(); i++) {
                sb.append(String.format("%6d|%s%n", startLine + i, selected.get(i)));
            }

            return new ReadFileResult(true, null, sb.toString(), totalLines,
                    endLine - startLine + 1, totalLines);
        } catch (IOException e) {
            log.error("读取文件失败: {}", filePath, e);
            return new ReadFileResult(false, "读取文件失败: " + e.getMessage(), "", 0, 0, 0);
        }
    }

    // ─── writeFile ─────────────────────────────────────────────────────

    /**
     * 写入（覆盖）文件内容。
     *
     * @param path    相对于工作区的文件路径
     * @param content 要写入的内容
     * @return 操作结果
     */
    public WriteFileResult writeFile(String path, String content) {
        Path filePath;
        try {
            filePath = resolveSafe(path);
        } catch (SecurityException e) {
            return new WriteFileResult(false, e.getMessage());
        }

        try {
            // 确保父目录存在
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(filePath, content != null ? content : "",
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("写入文件成功: {}", filePath);
            return new WriteFileResult(true, null);
        } catch (IOException e) {
            log.error("写入文件失败: {}", filePath, e);
            return new WriteFileResult(false, "写入文件失败: " + e.getMessage());
        }
    }

    // ─── searchFiles ───────────────────────────────────────────────────

    /**
     * 在工作区内搜索匹配正则模式的文件内容。
     *
     * @param pattern  正则表达式模式
     * @param fileGlob 文件名 glob 过滤（如 "*.java", "*.md"），默认 "*"
     * @return 搜索结果列表
     */
    public SearchResult searchFiles(String pattern, String fileGlob) {
        if (pattern == null || pattern.isBlank()) {
            return new SearchResult(false, "搜索模式不能为空", Collections.emptyList());
        }

        Pattern regex;
        try {
            regex = Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            return new SearchResult(false, "无效的正则表达式: " + e.getMessage(), Collections.emptyList());
        }

        String glob = (fileGlob != null && !fileGlob.isBlank()) ? fileGlob : "*";
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);

        Path root = Paths.get(WORKSPACE_ROOT).toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            return new SearchResult(true, null, Collections.emptyList());
        }

        List<SearchMatch> matches = new ArrayList<>();

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Path relative = root.relativize(file);
                    if (matcher.matches(file.getFileName())) {
                        try {
                            List<String> lines = Files.readAllLines(file);
                            for (int i = 0; i < lines.size(); i++) {
                                if (regex.matcher(lines.get(i)).find()) {
                                    matches.add(new SearchMatch(
                                            relative.toString(),
                                            i + 1,
                                            lines.get(i)
                                    ));
                                    if (matches.size() >= MAX_SEARCH_RESULTS) {
                                        return FileVisitResult.TERMINATE;
                                    }
                                }
                            }
                        } catch (IOException ignored) {
                            // 跳过无法读取的文件
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("搜索文件失败", e);
            return new SearchResult(false, "搜索失败: " + e.getMessage(), Collections.emptyList());
        }

        return new SearchResult(true, null, matches);
    }

    // ─── patch ─────────────────────────────────────────────────────────

    /**
     * 在文件中查找并替换字符串。
     *
     * @param path    相对于工作区的文件路径
     * @param oldStr  要查找的旧字符串
     * @param newStr  替换后的新字符串
     * @return 补丁结果
     */
    public PatchResult patch(String path, String oldStr, String newStr) {
        Path filePath;
        try {
            filePath = resolveSafe(path);
        } catch (SecurityException e) {
            return new PatchResult(false, e.getMessage(), null);
        }

        if (oldStr == null || oldStr.isEmpty()) {
            return new PatchResult(false, "oldStr 不能为空", null);
        }

        if (!Files.exists(filePath)) {
            return new PatchResult(false, "文件不存在: " + path, null);
        }

        try {
            String content = Files.readString(filePath);
            String newContent;

            if (content.contains(oldStr)) {
                newContent = content.replace(oldStr, newStr != null ? newStr : "");
            } else {
                return new PatchResult(false,
                        "未找到匹配的字符串", null);
            }

            Files.writeString(filePath, newContent,
                    StandardOpenOption.TRUNCATE_EXISTING);
            log.info("补丁应用成功: {}", filePath);

            // 生成简要 diff 信息
            String summary = "已将 '" + truncate(oldStr, 80) + "' 替换为 '" + truncate(newStr, 80) + "'";

            return new PatchResult(true, null, summary);
        } catch (IOException e) {
            log.error("补丁应用失败: {}", filePath, e);
            return new PatchResult(false, "补丁失败: " + e.getMessage(), null);
        }
    }

    // ─── ensure workspace ──────────────────────────────────────────────

    /**
     * 确保工作区根目录存在。
     */
    public void ensureWorkspace() {
        try {
            Path root = Paths.get(WORKSPACE_ROOT).toAbsolutePath().normalize();
            Files.createDirectories(root);
            log.info("工作区目录已确保存在: {}", root);
        } catch (IOException e) {
            log.error("创建工作区目录失败: {}", e.getMessage(), e);
        }
    }

    // ─── helper ────────────────────────────────────────────────────────

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    // ─── Result types ──────────────────────────────────────────────────

    public static class ReadFileResult {
        private final boolean success;
        private final String error;
        private final String content;
        private final int totalLines;
        private final int returnedLines;
        private final int nextOffset;

        public ReadFileResult(boolean success, String error, String content,
                              int totalLines, int returnedLines, int nextOffset) {
            this.success = success;
            this.error = error;
            this.content = content;
            this.totalLines = totalLines;
            this.returnedLines = returnedLines;
            this.nextOffset = nextOffset;
        }

        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public String getContent() { return content; }
        public int getTotalLines() { return totalLines; }
        public int getReturnedLines() { return returnedLines; }
        public int getNextOffset() { return nextOffset; }
    }

    public static class WriteFileResult {
        private final boolean success;
        private final String error;

        public WriteFileResult(boolean success, String error) {
            this.success = success;
            this.error = error;
        }

        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }

    public static class SearchMatch {
        private final String file;
        private final int line;
        private final String content;

        public SearchMatch(String file, int line, String content) {
            this.file = file;
            this.line = line;
            this.content = content;
        }

        public String getFile() { return file; }
        public int getLine() { return line; }
        public String getContent() { return content; }
    }

    public static class SearchResult {
        private final boolean success;
        private final String error;
        private final List<SearchMatch> matches;

        public SearchResult(boolean success, String error, List<SearchMatch> matches) {
            this.success = success;
            this.error = error;
            this.matches = matches;
        }

        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public List<SearchMatch> getMatches() { return matches; }
    }

    public static class PatchResult {
        private final boolean success;
        private final String error;
        private final String summary;

        public PatchResult(boolean success, String error, String summary) {
            this.success = success;
            this.error = error;
            this.summary = summary;
        }

        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public String getSummary() { return summary; }
    }
}
