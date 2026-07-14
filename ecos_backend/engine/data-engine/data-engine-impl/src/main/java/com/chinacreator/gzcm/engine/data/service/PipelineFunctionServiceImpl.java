package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.engine.data.PipelineFunctionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * PB 函数注册表实现 — 管理 120+ Palantir Foundry PB 函数目录。
 *
 * @author ECOS Pipeline 2.0 Team
 */
@Service
public class PipelineFunctionServiceImpl implements PipelineFunctionService {

    private static final Logger log = LoggerFactory.getLogger(PipelineFunctionServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;

    public PipelineFunctionServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        ensureSchema();
        seedBuiltinFunctions();
    }

    private void ensureSchema() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecos_pipeline_function (
                    id VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(100) NOT NULL UNIQUE,
                    category VARCHAR(50) NOT NULL,
                    signature TEXT NOT NULL,
                    return_type VARCHAR(50),
                    description TEXT,
                    example TEXT,
                    is_builtin BOOLEAN DEFAULT true,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            log.info("ecos_pipeline_function 表已就绪");
        } catch (Exception e) {
            log.warn("ecos_pipeline_function 表创建异常: {}", e.getMessage());
        }
    }

    private void seedBuiltinFunctions() {
        try {
            int count = jdbc.queryForObject("SELECT COUNT(*) FROM ecos_pipeline_function WHERE is_builtin = true", Integer.class);
            if (count >= 120) {
                log.info("PB 函数种子数据已存在 ({} 条)，跳过初始化", count);
                return;
            }

            log.info("正在初始化 PB 函数种子数据...");
            int inserted = 0;
            for (Object[] fn : ALL_FUNCTIONS) {
                try {
                    jdbc.update(
                        "INSERT INTO ecos_pipeline_function (id, name, category, signature, return_type, description, example) " +
                        "VALUES (?, ?, ?, ?::text, ?, ?, ?) ON CONFLICT (name) DO UPDATE SET " +
                        "category = EXCLUDED.category, signature = EXCLUDED.signature, example = EXCLUDED.example",
                        fn[0], fn[1], fn[2], fn[3], fn[4], fn[5], fn[6]
                    );
                    inserted++;
                } catch (Exception e) {
                    log.warn("函数 {} 插入失败: {}", fn[1], e.getMessage());
                }
            }
            log.info("PB 函数种子数据初始化完成，共 {} 条", inserted);
        } catch (Exception e) {
            log.warn("种子数据初始化异常: {}", e.getMessage());
        }
    }

    // ──────────────────────────────────────────────
    // Service 方法实现
    // ──────────────────────────────────────────────

    @Override
    public Map<String, Object> listFunctions(int page, int pageSize, String category) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ecos_pipeline_function WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (category != null && !category.isEmpty()) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        sql.append(" ORDER BY category, name");

        int offset = Math.max(0, page - 1) * pageSize;
        String countSql = sql.toString().replace("SELECT *", "SELECT COUNT(*)");
        int total = jdbc.queryForObject(countSql, Integer.class, params.toArray());

        sql.append(" LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add(offset);

        List<Map<String, Object>> list = jdbc.queryForList(sql.toString(), params.toArray());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("list", list);
        return result;
    }

    @Override
    public List<Map<String, Object>> listByCategory(String category) {
        return jdbc.queryForList(
            "SELECT * FROM ecos_pipeline_function WHERE category = ? ORDER BY name", category);
    }

    @Override
    public List<Map<String, Object>> search(String query) {
        String like = "%" + query + "%";
        return jdbc.queryForList(
            "SELECT * FROM ecos_pipeline_function WHERE name ILIKE ? OR description ILIKE ? ORDER BY category, name",
            like, like);
    }

    @Override
    public Map<String, Object> getById(String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM ecos_pipeline_function WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("函数不存在: " + id);
        }
        return rows.get(0);
    }

    @Override
    public List<Map<String, Object>> getCategories() {
        return jdbc.queryForList(
            "SELECT category, COUNT(*) as count FROM ecos_pipeline_function GROUP BY category ORDER BY category");
    }

    @Override
    public Map<String, Object> create(Map<String, Object> body) {
        String id = UUID.randomUUID().toString();
        String name = (String) body.get("name");
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("name 不能为空");

        jdbc.update(
            "INSERT INTO ecos_pipeline_function (id, name, category, signature, return_type, description, example, is_builtin) " +
            "VALUES (?, ?, ?, ?::text, ?, ?, ?, false)",
            id, name,
            body.getOrDefault("category", "custom"),
            body.getOrDefault("signature", "[]").toString(),
            body.getOrDefault("return_type", "any"),
            body.getOrDefault("description", ""),
            body.getOrDefault("example", ""));

        log.info("创建自定义函数: {} (id={})", name, id);
        return getById(id);
    }

    @Override
    public Map<String, Object> update(String id, Map<String, Object> body) {
        getById(id); // 校验存在
        jdbc.update(
            "UPDATE ecos_pipeline_function SET name = COALESCE(?, name), category = COALESCE(?, category), " +
            "signature = COALESCE(?::text, signature), return_type = COALESCE(?, return_type), " +
            "description = COALESCE(?, description), example = COALESCE(?, example) WHERE id = ?",
            body.get("name"), body.get("category"), body.get("signature"),
            body.get("return_type"), body.get("description"), body.get("example"), id);
        log.info("更新函数: id={}", id);
        return getById(id);
    }

    @Override
    public void delete(String id) {
        Map<String, Object> fn = getById(id);
        if (Boolean.TRUE.equals(fn.get("is_builtin"))) {
            throw new IllegalArgumentException("内置函数不可删除");
        }
        jdbc.update("DELETE FROM ecos_pipeline_function WHERE id = ?", id);
        log.info("删除函数: id={}", id);
    }

    // ──────────────────────────────────────────────
    // 120+ PB 内置函数种子数据
    // ──────────────────────────────────────────────
    // 格式: [id, name, category, signature_json, return_type, description, example]

    private static final Object[][] ALL_FUNCTIONS = {
        // ── 字符串函数 (25) ──
        {"fn-lower", "lower", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true}]", "string", "将字符串转为小写", "lower('HELLO') → 'hello'"},
        {"fn-upper", "upper", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true}]", "string", "将字符串转为大写", "upper('hello') → 'HELLO'"},
        {"fn-trim", "trim", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true}]", "string", "去除首尾空格", "trim(' hello ') → 'hello'"},
        {"fn-ltrim", "ltrim", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true}]", "string", "去除左侧空格", "ltrim(' hello') → 'hello'"},
        {"fn-rtrim", "rtrim", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true}]", "string", "去除右侧空格", "rtrim('hello ') → 'hello'"},
        {"fn-concat", "concat", "string", "[{\"name\":\"args\",\"type\":\"string...\",\"required\":true}]", "string", "拼接多个字符串", "concat('a','b','c') → 'abc'"},
        {"fn-substring", "substring", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true},{\"name\":\"start\",\"type\":\"int\",\"required\":true},{\"name\":\"len\",\"type\":\"int\",\"required\":false}]", "string", "截取子串", "substring('hello',2,3) → 'ell'"},
        {"fn-left", "left", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true},{\"name\":\"n\",\"type\":\"int\",\"required\":true}]", "string", "取左侧 n 个字符", "left('hello',2) → 'he'"},
        {"fn-right", "right", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true},{\"name\":\"n\",\"type\":\"int\",\"required\":true}]", "string", "取右侧 n 个字符", "right('hello',2) → 'lo'"},
        {"fn-length", "length", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true}]", "int", "字符串长度", "length('hello') → 5"},
        {"fn-replace", "replace", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true},{\"name\":\"from\",\"type\":\"string\",\"required\":true},{\"name\":\"to\",\"type\":\"string\",\"required\":true}]", "string", "字符串替换", "replace('hello','l','x') → 'hexxo'"},
        {"fn-split", "split", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true},{\"name\":\"delimiter\",\"type\":\"string\",\"required\":true}]", "array", "按分隔符分割为数组", "split('a,b,c',',') → ['a','b','c']"},
        {"fn-regex_extract", "regex_extract", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true},{\"name\":\"regex\",\"type\":\"string\",\"required\":true},{\"name\":\"group\",\"type\":\"int\",\"required\":false,\"default\":0}]", "string", "正则表达式提取", "regex_extract('abc123','\\\\d+') → '123'"},
        {"fn-regex_replace", "regex_replace", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true},{\"name\":\"regex\",\"type\":\"string\",\"required\":true},{\"name\":\"replacement\",\"type\":\"string\",\"required\":true}]", "string", "正则表达式替换", "regex_replace('abc123','\\\\d+','X') → 'abcX'"},
        {"fn-starts_with", "starts_with", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true},{\"name\":\"prefix\",\"type\":\"string\",\"required\":true}]", "boolean", "是否以指定前缀开头", "starts_with('hello','he') → true"},
        {"fn-ends_with", "ends_with", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true},{\"name\":\"suffix\",\"type\":\"string\",\"required\":true}]", "boolean", "是否以指定后缀结尾", "ends_with('hello','lo') → true"},
        {"fn-contains", "contains", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true},{\"name\":\"substring\",\"type\":\"string\",\"required\":true}]", "boolean", "是否包含子串", "contains('hello','ell') → true"},
        {"fn-initcap", "initcap", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true}]", "string", "首字母大写", "initcap('hello world') → 'Hello World'"},
        {"fn-reverse", "reverse", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true}]", "string", "字符串反转", "reverse('hello') → 'olleh'"},
        {"fn-lpad", "lpad", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true},{\"name\":\"len\",\"type\":\"int\",\"required\":true},{\"name\":\"pad\",\"type\":\"string\",\"required\":true}]", "string", "左侧填充", "lpad('42',5,'0') → '00042'"},
        {"fn-rpad", "rpad", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true},{\"name\":\"len\",\"type\":\"int\",\"required\":true},{\"name\":\"pad\",\"type\":\"string\",\"required\":true}]", "string", "右侧填充", "rpad('42',5,'0') → '42000'"},
        {"fn-repeat", "repeat", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true},{\"name\":\"n\",\"type\":\"int\",\"required\":true}]", "string", "重复 n 次", "repeat('ab',3) → 'ababab'"},
        {"fn-translate", "translate", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true},{\"name\":\"from\",\"type\":\"string\",\"required\":true},{\"name\":\"to\",\"type\":\"string\",\"required\":true}]", "string", "字符映射替换", "translate('abc','ac','12') → '1b2'"},
        {"fn-instr", "instr", "string", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true},{\"name\":\"substr\",\"type\":\"string\",\"required\":true}]", "int", "子串首次出现位置(1-based)", "instr('hello','l') → 3"},
        {"fn-locate", "locate", "string", "[{\"name\":\"substr\",\"type\":\"string\",\"required\":true},{\"name\":\"str\",\"type\":\"string\",\"required\":true},{\"name\":\"pos\",\"type\":\"int\",\"required\":false}]", "int", "从指定位置查找子串", "locate('l','hello',4) → 4"},

        // ── 数值函数 (25) ──
        {"fn-abs", "abs", "numeric", "[{\"name\":\"x\",\"type\":\"number\",\"required\":true}]", "number", "绝对值", "abs(-5) → 5"},
        {"fn-ceil", "ceil", "numeric", "[{\"name\":\"x\",\"type\":\"number\",\"required\":true}]", "int", "向上取整", "ceil(3.14) → 4"},
        {"fn-floor", "floor", "numeric", "[{\"name\":\"x\",\"type\":\"number\",\"required\":true}]", "int", "向下取整", "floor(3.14) → 3"},
        {"fn-round", "round", "numeric", "[{\"name\":\"x\",\"type\":\"number\",\"required\":true},{\"name\":\"d\",\"type\":\"int\",\"required\":false,\"default\":0}]", "number", "四舍五入到 d 位小数", "round(3.14159,2) → 3.14"},
        {"fn-power", "power", "numeric", "[{\"name\":\"base\",\"type\":\"number\",\"required\":true},{\"name\":\"exp\",\"type\":\"number\",\"required\":true}]", "number", "幂运算", "power(2,3) → 8"},
        {"fn-sqrt", "sqrt", "numeric", "[{\"name\":\"x\",\"type\":\"number\",\"required\":true}]", "number", "平方根", "sqrt(16) → 4"},
        {"fn-mod", "mod", "numeric", "[{\"name\":\"a\",\"type\":\"number\",\"required\":true},{\"name\":\"b\",\"type\":\"number\",\"required\":true}]", "number", "取模", "mod(10,3) → 1"},
        {"fn-exp", "exp", "numeric", "[{\"name\":\"x\",\"type\":\"number\",\"required\":true}]", "number", "e 的 x 次方", "exp(1) → 2.718"},
        {"fn-ln", "ln", "numeric", "[{\"name\":\"x\",\"type\":\"number\",\"required\":true}]", "number", "自然对数", "ln(e) → 1"},
        {"fn-log", "log", "numeric", "[{\"name\":\"base\",\"type\":\"number\",\"required\":true},{\"name\":\"x\",\"type\":\"number\",\"required\":true}]", "number", "以 base 为底的对数", "log(2,8) → 3"},
        {"fn-log10", "log10", "numeric", "[{\"name\":\"x\",\"type\":\"number\",\"required\":true}]", "number", "以10为底的对数", "log10(100) → 2"},
        {"fn-sign", "sign", "numeric", "[{\"name\":\"x\",\"type\":\"number\",\"required\":true}]", "int", "符号函数 (-1/0/1)", "sign(-5) → -1"},
        {"fn-greatest", "greatest", "numeric", "[{\"name\":\"args\",\"type\":\"number...\",\"required\":true}]", "number", "返回最大值", "greatest(1,5,3) → 5"},
        {"fn-least", "least", "numeric", "[{\"name\":\"args\",\"type\":\"number...\",\"required\":true}]", "number", "返回最小值", "least(1,5,3) → 1"},
        {"fn-rand", "rand", "numeric", "[]", "number", "随机数 (0-1)", "rand() → 0.723"},
        {"fn-radians", "radians", "numeric", "[{\"name\":\"deg\",\"type\":\"number\",\"required\":true}]", "number", "度转弧度", "radians(180) → 3.14159"},
        {"fn-degrees", "degrees", "numeric", "[{\"name\":\"rad\",\"type\":\"number\",\"required\":true}]", "number", "弧度转度", "degrees(3.14159) → 180"},
        {"fn-sin", "sin", "numeric", "[{\"name\":\"x\",\"type\":\"number\",\"required\":true}]", "number", "正弦", "sin(0) → 0"},
        {"fn-cos", "cos", "numeric", "[{\"name\":\"x\",\"type\":\"number\",\"required\":true}]", "number", "余弦", "cos(0) → 1"},
        {"fn-tan", "tan", "numeric", "[{\"name\":\"x\",\"type\":\"number\",\"required\":true}]", "number", "正切", "tan(0) → 0"},
        {"fn-asin", "asin", "numeric", "[{\"name\":\"x\",\"type\":\"number\",\"required\":true}]", "number", "反正弦", "asin(0) → 0"},
        {"fn-acos", "acos", "numeric", "[{\"name\":\"x\",\"type\":\"number\",\"required\":true}]", "number", "反余弦", "acos(1) → 0"},
        {"fn-atan", "atan", "numeric", "[{\"name\":\"x\",\"type\":\"number\",\"required\":true}]", "number", "反正切", "atan(0) → 0"},
        {"fn-atan2", "atan2", "numeric", "[{\"name\":\"y\",\"type\":\"number\",\"required\":true},{\"name\":\"x\",\"type\":\"number\",\"required\":true}]", "number", "双参数反正切", "atan2(1,1) → 0.7854"},
        {"fn-crc32", "crc32", "numeric", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true}]", "int", "CRC32 哈希", "crc32('hello') → 907060870"},

        // ── 日期时间函数 (25) ──
        {"fn-year", "year", "date_time", "[{\"name\":\"date\",\"type\":\"date\",\"required\":true}]", "int", "提取年份", "year('2026-07-11') → 2026"},
        {"fn-month", "month", "date_time", "[{\"name\":\"date\",\"type\":\"date\",\"required\":true}]", "int", "提取月份", "month('2026-07-11') → 7"},
        {"fn-day", "day", "date_time", "[{\"name\":\"date\",\"type\":\"date\",\"required\":true}]", "int", "提取日", "day('2026-07-11') → 11"},
        {"fn-hour", "hour", "date_time", "[{\"name\":\"ts\",\"type\":\"timestamp\",\"required\":true}]", "int", "提取小时", "hour('2026-07-11 14:30:00') → 14"},
        {"fn-minute", "minute", "date_time", "[{\"name\":\"ts\",\"type\":\"timestamp\",\"required\":true}]", "int", "提取分钟", "minute('2026-07-11 14:30:00') → 30"},
        {"fn-second", "second", "date_time", "[{\"name\":\"ts\",\"type\":\"timestamp\",\"required\":true}]", "int", "提取秒", "second('2026-07-11 14:30:45') → 45"},
        {"fn-dayofweek", "dayofweek", "date_time", "[{\"name\":\"date\",\"type\":\"date\",\"required\":true}]", "int", "星期几 (1=周日)", "dayofweek('2026-07-11') → 7"},
        {"fn-dayofyear", "dayofyear", "date_time", "[{\"name\":\"date\",\"type\":\"date\",\"required\":true}]", "int", "一年第几天", "dayofyear('2026-07-11') → 192"},
        {"fn-weekofyear", "weekofyear", "date_time", "[{\"name\":\"date\",\"type\":\"date\",\"required\":true}]", "int", "一年第几周", "weekofyear('2026-07-11') → 28"},
        {"fn-quarter", "quarter", "date_time", "[{\"name\":\"date\",\"type\":\"date\",\"required\":true}]", "int", "季度 (1-4)", "quarter('2026-07-11') → 3"},
        {"fn-date_add", "date_add", "date_time", "[{\"name\":\"date\",\"type\":\"date\",\"required\":true},{\"name\":\"days\",\"type\":\"int\",\"required\":true}]", "date", "加天数", "date_add('2026-07-11',7) → '2026-07-18'"},
        {"fn-date_sub", "date_sub", "date_time", "[{\"name\":\"date\",\"type\":\"date\",\"required\":true},{\"name\":\"days\",\"type\":\"int\",\"required\":true}]", "date", "减天数", "date_sub('2026-07-11',7) → '2026-07-04'"},
        {"fn-datediff", "datediff", "date_time", "[{\"name\":\"end\",\"type\":\"date\",\"required\":true},{\"name\":\"start\",\"type\":\"date\",\"required\":true}]", "int", "日期差(天数)", "datediff('2026-07-18','2026-07-11') → 7"},
        {"fn-date_trunc", "date_trunc", "date_time", "[{\"name\":\"date\",\"type\":\"date\",\"required\":true},{\"name\":\"unit\",\"type\":\"string\",\"required\":true}]", "date", "截断到指定粒度", "date_trunc('2026-07-11','month') → '2026-07-01'"},
        {"fn-current_date", "current_date", "date_time", "[]", "date", "当前日期", "current_date() → '2026-07-11'"},
        {"fn-current_timestamp", "current_timestamp", "date_time", "[]", "timestamp", "当前时间戳", "current_timestamp() → '2026-07-11 14:30:00'"},
        {"fn-to_date", "to_date", "date_time", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true},{\"name\":\"fmt\",\"type\":\"string\",\"required\":true}]", "date", "字符串转日期", "to_date('2026-07-11','yyyy-MM-dd')"},
        {"fn-to_timestamp", "to_timestamp", "date_time", "[{\"name\":\"str\",\"type\":\"string\",\"required\":true},{\"name\":\"fmt\",\"type\":\"string\",\"required\":true}]", "timestamp", "字符串转时间戳", "to_timestamp('2026-07-11','yyyy-MM-dd')"},
        {"fn-date_format", "date_format", "date_time", "[{\"name\":\"date\",\"type\":\"date\",\"required\":true},{\"name\":\"fmt\",\"type\":\"string\",\"required\":true}]", "string", "日期格式化", "date_format('2026-07-11','yyyy/MM') → '2026/07'"},
        {"fn-unix_timestamp", "unix_timestamp", "date_time", "[{\"name\":\"ts\",\"type\":\"timestamp\",\"required\":false}]", "long", "转Unix时间戳(秒)", "unix_timestamp('2026-07-11') → 1750000000"},
        {"fn-from_unixtime", "from_unixtime", "date_time", "[{\"name\":\"unix\",\"type\":\"long\",\"required\":true},{\"name\":\"fmt\",\"type\":\"string\",\"required\":false}]", "timestamp", "Unix时间戳转日期", "from_unixtime(1750000000)"},
        {"fn-add_months", "add_months", "date_time", "[{\"name\":\"date\",\"type\":\"date\",\"required\":true},{\"name\":\"n\",\"type\":\"int\",\"required\":true}]", "date", "加 n 个月", "add_months('2026-07-11',2) → '2026-09-11'"},
        {"fn-months_between", "months_between", "date_time", "[{\"name\":\"end\",\"type\":\"date\",\"required\":true},{\"name\":\"start\",\"type\":\"date\",\"required\":true}]", "number", "月份差", "months_between('2026-09-11','2026-07-11') → 2"},
        {"fn-last_day", "last_day", "date_time", "[{\"name\":\"date\",\"type\":\"date\",\"required\":true}]", "date", "当月最后一天", "last_day('2026-07-11') → '2026-07-31'"},
        {"fn-next_day", "next_day", "date_time", "[{\"name\":\"date\",\"type\":\"date\",\"required\":true},{\"name\":\"weekday\",\"type\":\"string\",\"required\":true}]", "date", "下一个指定星期几", "next_day('2026-07-11','MON') → '2026-07-13'"},

        // ── 条件/逻辑函数 (10) ──
        {"fn-if", "if", "conditional", "[{\"name\":\"condition\",\"type\":\"boolean\",\"required\":true},{\"name\":\"true_val\",\"type\":\"any\",\"required\":true},{\"name\":\"false_val\",\"type\":\"any\",\"required\":true}]", "any", "条件分支", "if(x>10,'high','low')"},
        {"fn-case", "case", "conditional", "[{\"name\":\"...\",\"type\":\"varargs\",\"required\":true}]", "any", "多条件分支 (cond,val 对 + default)", "case(x>10,'high',x>5,'mid','low')"},
        {"fn-coalesce", "coalesce", "conditional", "[{\"name\":\"args\",\"type\":\"any...\",\"required\":true}]", "any", "返回第一个非 NULL 值", "coalesce(null,null,'fallback') → 'fallback'"},
        {"fn-nullif", "nullif", "conditional", "[{\"name\":\"a\",\"type\":\"any\",\"required\":true},{\"name\":\"b\",\"type\":\"any\",\"required\":true}]", "any", "a==b 则返回 NULL", "nullif(0,0) → null"},
        {"fn-ifnull", "ifnull", "conditional", "[{\"name\":\"a\",\"type\":\"any\",\"required\":true},{\"name\":\"b\",\"type\":\"any\",\"required\":true}]", "any", "a=null 则返回 b", "ifnull(null,'default') → 'default'"},
        {"fn-nvl", "nvl", "conditional", "[{\"name\":\"a\",\"type\":\"any\",\"required\":true},{\"name\":\"b\",\"type\":\"any\",\"required\":true}]", "any", "NULL 替换 (同 ifnull)", "nvl(null,0) → 0"},
        {"fn-nvl2", "nvl2", "conditional", "[{\"name\":\"a\",\"type\":\"any\",\"required\":true},{\"name\":\"b\",\"type\":\"any\",\"required\":true},{\"name\":\"c\",\"type\":\"any\",\"required\":true}]", "any", "a 非NULL返回b，否则c", "nvl2(x,'has value','is null')"},
        {"fn-isnull", "isnull", "conditional", "[{\"name\":\"a\",\"type\":\"any\",\"required\":true}]", "boolean", "是否 NULL", "isnull(null) → true"},
        {"fn-isnotnull", "isnotnull", "conditional", "[{\"name\":\"a\",\"type\":\"any\",\"required\":true}]", "boolean", "是否非 NULL", "isnotnull('x') → true"},
        {"fn-decode", "decode", "conditional", "[{\"name\":\"expr\",\"type\":\"any\",\"required\":true}]", "any", "键值映射 (expr, k1,v1, ..., default)", "decode(status,1,'active',2,'inactive','unknown')"},

        // ── 数组/结构体函数 (15) ──
        {"fn-array", "array", "array", "[{\"name\":\"args\",\"type\":\"any...\",\"required\":true}]", "array", "构造数组", "array(1,2,3) → [1,2,3]"},
        {"fn-array_contains", "array_contains", "array", "[{\"name\":\"arr\",\"type\":\"array\",\"required\":true},{\"name\":\"elem\",\"type\":\"any\",\"required\":true}]", "boolean", "数组是否包含元素", "array_contains([1,2,3],2) → true"},
        {"fn-array_join", "array_join", "array", "[{\"name\":\"arr\",\"type\":\"array\",\"required\":true},{\"name\":\"delimiter\",\"type\":\"string\",\"required\":true}]", "string", "数组拼接为字符串", "array_join(['a','b'],',') → 'a,b'"},
        {"fn-array_append", "array_append", "array", "[{\"name\":\"arr\",\"type\":\"array\",\"required\":true},{\"name\":\"elem\",\"type\":\"any\",\"required\":true}]", "array", "追加元素到末尾", "array_append([1,2],3) → [1,2,3]"},
        {"fn-array_prepend", "array_prepend", "array", "[{\"name\":\"arr\",\"type\":\"array\",\"required\":true},{\"name\":\"elem\",\"type\":\"any\",\"required\":true}]", "array", "前置元素", "array_prepend([2,3],1) → [1,2,3]"},
        {"fn-explode", "explode", "array", "[{\"name\":\"arr\",\"type\":\"array\",\"required\":true}]", "rows", "展开数组为多行", "explode([1,2,3]) → 3行"},
        {"fn-size", "size", "array", "[{\"name\":\"arr\",\"type\":\"array\",\"required\":true}]", "int", "数组大小", "size([1,2,3]) → 3"},
        {"fn-cardinality", "cardinality", "array", "[{\"name\":\"arr\",\"type\":\"array\",\"required\":true}]", "int", "数组元素数 (同size)", "cardinality([1,2,3]) → 3"},
        {"fn-element_at", "element_at", "array", "[{\"name\":\"arr\",\"type\":\"array\",\"required\":true},{\"name\":\"idx\",\"type\":\"int\",\"required\":true}]", "any", "取下标元素(1-based)", "element_at([10,20,30],2) → 20"},
        {"fn-sort_array", "sort_array", "array", "[{\"name\":\"arr\",\"type\":\"array\",\"required\":true}]", "array", "数组排序", "sort_array([3,1,2]) → [1,2,3]"},
        {"fn-slice", "slice", "array", "[{\"name\":\"arr\",\"type\":\"array\",\"required\":true},{\"name\":\"start\",\"type\":\"int\",\"required\":true},{\"name\":\"len\",\"type\":\"int\",\"required\":true}]", "array", "数组切片", "slice([1,2,3,4],2,2) → [2,3]"},
        {"fn-map", "map", "array", "[{\"name\":\"...\",\"type\":\"varargs\",\"required\":true}]", "map", "构造 Map", "map('a',1,'b',2) → {'a':1,'b':2}"},
        {"fn-map_keys", "map_keys", "array", "[{\"name\":\"m\",\"type\":\"map\",\"required\":true}]", "array", "Map 的所有 key", "map_keys({'a':1,'b':2}) → ['a','b']"},
        {"fn-map_values", "map_values", "array", "[{\"name\":\"m\",\"type\":\"map\",\"required\":true}]", "array", "Map 的所有 value", "map_values({'a':1,'b':2}) → [1,2]"},
        {"fn-struct", "struct", "array", "[{\"name\":\"fields\",\"type\":\"any...\",\"required\":true}]", "struct", "构造结构体", "struct('name','Alice','age',30)"},

        // ── 窗口函数 (12) ──
        {"fn-row_number", "row_number", "window", "[]", "int", "分区内行号", "row_number() over(partition by dept order by salary desc)"},
        {"fn-rank", "rank", "window", "[]", "int", "排名(有间隔)", "rank() over(order by score desc)"},
        {"fn-dense_rank", "dense_rank", "window", "[]", "int", "排名(无间隔)", "dense_rank() over(order by score desc)"},
        {"fn-lead", "lead", "window", "[{\"name\":\"col\",\"type\":\"any\",\"required\":true},{\"name\":\"offset\",\"type\":\"int\",\"required\":false,\"default\":1},{\"name\":\"default\",\"type\":\"any\",\"required\":false}]", "any", "取后行值", "lead(amount,1,0) over(order by date)"},
        {"fn-lag", "lag", "window", "[{\"name\":\"col\",\"type\":\"any\",\"required\":true},{\"name\":\"offset\",\"type\":\"int\",\"required\":false,\"default\":1},{\"name\":\"default\",\"type\":\"any\",\"required\":false}]", "any", "取前行值", "lag(amount,1,0) over(order by date)"},
        {"fn-first_value", "first_value", "window", "[{\"name\":\"col\",\"type\":\"any\",\"required\":true}]", "any", "窗口内第一个值", "first_value(amount) over(partition by dept order by date)"},
        {"fn-last_value", "last_value", "window", "[{\"name\":\"col\",\"type\":\"any\",\"required\":true}]", "any", "窗口内最后一个值", "last_value(amount) over(partition by dept)"},
        {"fn-nth_value", "nth_value", "window", "[{\"name\":\"col\",\"type\":\"any\",\"required\":true},{\"name\":\"n\",\"type\":\"int\",\"required\":true}]", "any", "窗口内第 n 个值", "nth_value(amount,3) over(partition by dept)"},
        {"fn-percent_rank", "percent_rank", "window", "[]", "number", "百分位排名", "percent_rank() over(order by score)"},
        {"fn-cume_dist", "cume_dist", "window", "[]", "number", "累积分布", "cume_dist() over(order by score)"},
        {"fn-ntile", "ntile", "window", "[{\"name\":\"n\",\"type\":\"int\",\"required\":true}]", "int", "分桶(等频)", "ntile(4) over(order by score)"},
        {"fn-sum_over", "sum_over", "window", "[{\"name\":\"col\",\"type\":\"number\",\"required\":true}]", "number", "窗口求和", "sum(amount) over(partition by dept order by date)"},

        // ── 类型转换函数 (8) ──
        {"fn-cast", "cast", "casting", "[{\"name\":\"expr\",\"type\":\"any\",\"required\":true},{\"name\":\"type\",\"type\":\"string\",\"required\":true}]", "any", "类型转换", "cast('123' as int) → 123"},
        {"fn-to_string", "to_string", "casting", "[{\"name\":\"x\",\"type\":\"any\",\"required\":true}]", "string", "转字符串", "to_string(123) → '123'"},
        {"fn-to_int", "to_int", "casting", "[{\"name\":\"x\",\"type\":\"any\",\"required\":true}]", "int", "转整数", "to_int('123') → 123"},
        {"fn-to_long", "to_long", "casting", "[{\"name\":\"x\",\"type\":\"any\",\"required\":true}]", "long", "转长整数", "to_long('1234567890') → 1234567890L"},
        {"fn-to_double", "to_double", "casting", "[{\"name\":\"x\",\"type\":\"any\",\"required\":true}]", "double", "转双精度", "to_double('3.14') → 3.14"},
        {"fn-to_float", "to_float", "casting", "[{\"name\":\"x\",\"type\":\"any\",\"required\":true}]", "float", "转浮点数", "to_float('3.14') → 3.14f"},
        {"fn-to_decimal", "to_decimal", "casting", "[{\"name\":\"x\",\"type\":\"any\",\"required\":true},{\"name\":\"p\",\"type\":\"int\",\"required\":true},{\"name\":\"s\",\"type\":\"int\",\"required\":true}]", "decimal", "转十进制", "to_decimal('123.45',10,2)"},
        {"fn-to_boolean", "to_boolean", "casting", "[{\"name\":\"x\",\"type\":\"any\",\"required\":true}]", "boolean", "转布尔值", "to_boolean(1) → true"},
    };
}
