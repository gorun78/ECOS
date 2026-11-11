package com.chinacreator.gzcm.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分页遍历（口径：limit/offset，单页上限 500，唯一列兜底排序 —— 设计册 §10 分页约定）。
 *
 * <p>两种模式：</p>
 * <ul>
 *   <li>默认：只取一页，输出带 {@code page} 元信息（不静默截断）；</li>
 *   <li>{@code --all}：按 offset 增量遍历，直到短页或达到 {@code --max-pages}。</li>
 * </ul>
 *
 * <p>本类只做「翻页编排放」，不解析业务字段：行数从 data 数组长度判断，
 * 若后端返回 {@code total} 则优先用它判定结束。</p>
 */
public final class PageWalker {

    public static final int MAX_PAGE_SIZE = 500;
    public static final int MAX_PAGES = 100;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PageWalker() {
    }

    /** 单页大小校验：0 < size <= 500。 */
    public static int pageSize(Integer requested, int fallback) {
        if (requested == null) {
            return fallback;
        }
        if (requested <= 0 || requested > MAX_PAGE_SIZE) {
            throw new CliException(ExitCodes.USAGE,
                    "分页大小须在 1~" + MAX_PAGE_SIZE + "，实得: " + requested);
        }
        return requested;
    }

    public record PageResult(List<Object> rows, int pagesFetched, int pageSize, boolean truncated,
                             Integer total) {
    }

    /**
     * 复用已取到的首页结果（命令主调用那一次），只在 {@code --all} 时续取后续页。
     *
     * <p>默认单页由此不发第二次请求；{@code all=false} 时 truncated 表示「还有下一页未取」。</p>
     */
    public static PageResult collectFromFirst(int pageSize, int maxPages, boolean all,
                                              HttpTransport.Result first,
                                              PageFetcher fetcher) {
        if (first == null || first.transportFailed()
                || first.status() < 200 || first.status() >= 300) {
            throw new PageFetchException(first);
        }
        JsonNode body = parse(first.body());
        List<Object> lastRows = rowsOf(body);
        List<Object> merged = new ArrayList<>(lastRows);
        Integer total = totalOf(body);
        int pages = 1;
        int offset = pageSize;
        boolean truncated = false;

        while (all) {
            boolean shortPage = lastRows.size() < pageSize;
            boolean reachedTotal = total != null && merged.size() >= total;
            if (shortPage || reachedTotal) {
                break;
            }
            if (pages >= maxPages) {
                truncated = true;
                break;
            }
            HttpTransport.Result result = fetcher.fetch(offset, pageSize);
            pages++;
            if (result.transportFailed() || result.status() < 200 || result.status() >= 300) {
                truncated = true;
                break;
            }
            JsonNode pageBody = parse(result.body());
            lastRows = rowsOf(pageBody);
            Integer pageTotal = totalOf(pageBody);
            if (pageTotal != null) {
                total = pageTotal;
            }
            merged.addAll(lastRows);
            offset += pageSize;
        }
        return new PageResult(merged, pages, pageSize, truncated, total);
    }

    /** 逐页拉取并合并；fetcher 接收 offset 返回 HTTP 结果。 */
    public static PageResult collect(int pageSize, int maxPages, boolean all,
                                     PageFetcher fetcher) {
        List<Object> merged = new ArrayList<>();
        Integer total = null;
        int offset = 0;
        int pages = 0;
        boolean truncated = false;

        while (true) {
            HttpTransport.Result result = fetcher.fetch(offset, pageSize);
            pages++;
            if (result.transportFailed() || result.status() < 200 || result.status() >= 300) {
                if (merged.isEmpty()) {
                    // 首页就失败：交给上层映射，不吞错
                    throw new PageFetchException(result);
                }
                truncated = true;
                break;
            }
            JsonNode body = parse(result.body());
            List<Object> rows = rowsOf(body);
            Integer pageTotal = totalOf(body);
            if (pageTotal != null) {
                total = pageTotal;
            }
            merged.addAll(rows);

            boolean shortPage = rows.size() < pageSize;
            boolean reachedTotal = total != null && merged.size() >= total;
            if (shortPage || reachedTotal) {
                break;
            }
            if (!all) {
                truncated = true;
                break;
            }
            if (pages >= maxPages) {
                truncated = true;
                break;
            }
            offset += pageSize;
        }
        return new PageResult(merged, pages, pageSize, truncated, total);
    }

    private static JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> rowsOf(JsonNode body) {
        if (body == null || body.isNull()) {
            return List.of();
        }
        JsonNode data = body.has("data") ? body.get("data") : body;
        if (data == null || data.isNull()) {
            return List.of();
        }
        // 设计册 §「分页体 {items,total,page,size}」：行在 items 里，不能只认裸数组。
        // 双重信封（data.data.{items|[...]}）也会出现，仍按同一口径下钻一层。
        JsonNode rows = data;
        if (!rows.isArray()) {
            rows = rows.get("items");
        }
        if ((rows == null || !rows.isArray()) && data.has("data")) {
            JsonNode inner = data.get("data");
            rows = inner.isArray() ? inner : inner.get("items");
        }
        if (rows == null || !rows.isArray()) {
            return List.of();
        }
        return MAPPER.convertValue(rows, List.class);
    }

    private static Integer totalOf(JsonNode body) {
        if (body == null) {
            return null;
        }
        JsonNode candidate = body.has("total") ? body.get("total")
                : body.has("data") && body.get("data").has("total") ? body.get("data").get("total") : null;
        if (candidate == null && body.has("data") && body.get("data").has("data")
                && body.get("data").get("data").has("total")) {
            candidate = body.get("data").get("data").get("total");
        }
        return candidate != null && candidate.isNumber() ? candidate.asInt() : null;
    }

    public static Map<String, Object> pageMeta(PageResult r, boolean all) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("pageSize", r.pageSize());
        meta.put("pagesFetched", r.pagesFetched());
        meta.put("rowCount", r.rows().size());
        meta.put("total", r.total());
        meta.put("truncated", r.truncated());
        meta.put("all", all);
        return meta;
    }

    @FunctionalInterface
    public interface PageFetcher {
        HttpTransport.Result fetch(int offset, int limit);
    }

    /** 首页失败：保留原始结果供上层统一映射（不在此处决定 status）。 */
    public static final class PageFetchException extends RuntimeException {
        private final transient HttpTransport.Result result;

        public PageFetchException(HttpTransport.Result result) {
            super("分页首页请求失败");
            this.result = result;
        }

        public HttpTransport.Result result() {
            return result;
        }
    }
}
