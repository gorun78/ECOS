package com.chinacreator.gzcm.engine.cognitive2.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F05-13 SQL 方言合规（MC03 多库兼容，设计-05 冻结验收名）：
 * 契约面 SQL 禁 {@code ::jsonb} 强转（JSON 只 TEXT，MC02）、禁 {@code ILIKE}
 * （改 {@code lower() LIKE}）、禁无 databaseId 分支的裸 {@code RETURNING}
 * （改应用侧 UUID + 显式 select）。范围 = CogScope M0 契约面；legacy 全域收敛为 F05-13 M1 已登记项。
 */
class CognitiveSqlDialectTest {

    private static final Pattern JSONB_CAST = Pattern.compile("::\\s*jsonb", Pattern.CASE_INSENSITIVE);
    private static final Pattern ILIKE = Pattern.compile("\\bILIKE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE_RETURNING = Pattern.compile("\\bRETURNING\\s", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("契约面禁 ::jsonb / ILIKE / 裸 RETURNING（MC02/MC03）")
    void noJsonbCastNoIlNoBareReturning() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (var walk = CogScope.streamInScope()) {
            walk.forEach(p -> {
                try {
                    String src = stripComments(Files20.read(p));
                    scan(JSONB_CAST, "::jsonb", src, p, offenders);
                    scan(ILIKE, "ILIKE", src, p, offenders);
                    scan(BARE_RETURNING, "RETURNING", src, p, offenders);
                } catch (IOException ignored) {}
            });
        }
        assertTrue(offenders.isEmpty(), "方言违例（MC02/MC03）：\n" + String.join("\n", offenders));
    }

    /** 剥离块/行注释（保行数）：红线只管 SQL 字面量，Javadoc 里的禁词示例不是违例。 */
    static String stripComments(String src) {
        return CogScope.stripComments(src);
    }

    private static void scan(Pattern p, String label, String src, java.nio.file.Path f, List<String> sink) {
        var m = p.matcher(src);
        while (m.find()) {
            sink.add(label + " @ " + f.getFileName() + ":" + CogScope.lineOf(src, m.start()));
        }
    }

    /** Files.readString 薄封装（保持 lambda 内 IOException 局部化）。 */
    static final class Files20 {
        static String read(java.nio.file.Path p) throws IOException {
            return java.nio.file.Files.readString(p);
        }
    }
}
