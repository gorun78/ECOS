package com.chinacreator.gzcm.engine.ai.replay;

import com.chinacreator.gzcm.common.trace.AgentLoopTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Agent Loop 结构化日志文件读取端（详细设计-06 F06-08 要点：回放只依赖日志与审计事件）。
 *
 * <p>目录单一来源 = Spring Boot 标准属性 {@code logging.file.path}（logback 侧同名映射为
 * {@code LOG_PATH}，二者不各写一份），缺省 {@code logs}；文件名前缀固定 {@code agent-loop}，
 * 与 logback appender 落地文件（当前 {@code agent-loop.log} + 滚动 {@code agent-loop.<日期>.<序号>.log}）
 * 同约定 —— 滚动策略必须保持<b>纯文本</b>（禁 .gz），否则历史段不可回放。</p>
 *
 * <p>失败可见性：目录不存在/不可读不抛错、不静默 —— 返回 {@link ScanResult} 里的
 * {@code sourceAvailable=false} 让回放端显式对外表达"通道未就绪"（禁伪 200 空数组）。</p>
 */
@Component
public class AgentLoopLogSource {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopLogSource.class);

    /** Spring Boot 标准日志目录属性（logback 内以 {@code ${LOG_PATH}} 引用同名值）。 */
    static final String LOG_PATH_PROPERTY = "logging.file.path";

    /** 缺省日志目录（未配 {@code logging.file.path} 时 logback 与本类同用此相对目录）。 */
    static final String DEFAULT_LOG_DIR = "logs";

    /** Agent Loop 通道文件名前缀（与 logback appender 约定同源）。 */
    static final String FILE_PREFIX = "agent-loop";

    /** 单次回放最多扫描的行数 —— 有界扫描，防大日志面把请求拖成 OOM。 */
    static final int MAX_SCANNED_LINES = 200_000;

    private final Environment environment;

    public AgentLoopLogSource(Environment environment) {
        this.environment = environment;
    }

    /** 扫描结果：结构化行 + 通道可用性 + 是否触顶截断。 */
    public record ScanResult(boolean sourceAvailable, boolean truncated, List<AgentLoopTrace.Line> lines) {

        static ScanResult unavailable() {
            return new ScanResult(false, false, List.of());
        }
    }

    /** 日志目录（只读解析，不外泄绝对路径到响应面）。 */
    Path logDirectory() {
        String configured = environment == null ? null : environment.getProperty(LOG_PATH_PROPERTY);
        return Paths.get(configured == null || configured.isBlank() ? DEFAULT_LOG_DIR : configured.trim());
    }

    /**
     * 读取全部 Agent Loop 结构化行（按文件名字典序 = 滚动时间序，行内按出现序赋 seq）。
     *
     * <p>单文件读失败只影响该文件（ERROR 留痕），其余文件照常参与重建。</p>
     */
    public ScanResult scan() {
        Path dir = logDirectory();
        List<Path> files = listChannelFiles(dir);
        if (files.isEmpty()) {
            if (Files.isDirectory(dir)) {
                log.warn("[F06-08] 回放日志通道无文件（appender 未挂载或该目录非本进程日志面）: "
                        + "dir={} prefix={}", dir.getFileName(), FILE_PREFIX);
            } else {
                log.warn("[F06-08] 回放日志目录不存在，运行回放不可用（禁伪 200）: dir={}", dir.getFileName());
            }
            return ScanResult.unavailable();
        }
        List<AgentLoopTrace.Line> lines = new ArrayList<>();
        long seq = 0;
        long scanned = 0;
        boolean truncated = false;
        scan:
        for (Path file : files) {
            try (Stream<String> stream = Files.lines(file, StandardCharsets.UTF_8)) {
                Iterator<String> it = stream.iterator();
                while (it.hasNext()) {
                    if (scanned >= MAX_SCANNED_LINES) {
                        truncated = true;
                        break scan;
                    }
                    scanned++;
                    AgentLoopTrace.Line line = AgentLoopTrace.parse(it.next(), seq);
                    if (line != null) {
                        lines.add(line);
                        seq++;
                    }
                }
            } catch (IOException | UncheckedIOException e) {
                log.error("[F06-08] 回放日志文件读取失败（该段步骤缺失，禁静默）: file={} err={}",
                        file.getFileName(), e.getMessage());
            }
        }
        if (truncated) {
            log.error("[F06-08] 回放日志扫描触顶 {} 行，结果不完整（需调大保留策略或按 traceId 归档）: files={}",
                    MAX_SCANNED_LINES, files.size());
        }
        return new ScanResult(true, truncated, lines);
    }

    /** 通道文件名（前缀匹配 + .log 结尾），按名称排序保证跨文件时序稳定。 */
    private List<Path> listChannelFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.startsWith(FILE_PREFIX) && name.endsWith(".log");
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException | UncheckedIOException e) {
            log.error("[F06-08] 回放日志目录列举失败，回放按不可用处理: dir={} err={}",
                    dir.getFileName(), e.getMessage());
            return List.of();
        }
    }
}
