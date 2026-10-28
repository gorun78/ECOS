package com.chinacreator.gzcm.engine.cognitive2.guard;

import com.chinacreator.gzcm.engine.cognitive2.testscan.SourceCommentStripper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 设计-05 F05 M0 验收扫描公共工具：将源码扫描范围收窄到**本批交付的场景认知契约面**
 * （{@code service/scenario} + {@code controller/scenario} + {@code service/calc} + 两张新 DAO
 * + {@code WorldModelController}），避免把 F05-13 M1/M2 才落地的 legacy 全域收敛误判为 M0 违约。
 */
public final class CogScope {

    private CogScope() {}

    /** repo root（impl test 运行 CWD = cognitive-engine-impl；4 级上溯 = repo root）。 */
    public static final Path REPO = Path.of("..", "..", "..", "..").toAbsolutePath().normalize();

    public static final Path MAIN = REPO.resolve(
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java");

    /** 本批 M0 契约面 in-scope 判定（类级前缀白名单）。 */
    public static boolean inScope(Path p) {
        String s = p.toString().replace('\\', '/');
        return s.contains("/service/scenario/")
                || s.contains("/controller/scenario/")
                || s.contains("/service/calc/")
                || s.endsWith("/dao/MindAssetDao.java")
                || s.endsWith("/dao/ScenarioDao.java")
                || s.endsWith("/dao/BeliefScorer.java")
                || s.endsWith("/dao/HypothesisReader.java")
                || s.endsWith("/dao/BeliefReader.java")
                || s.endsWith("/controller/WorldModelController.java")
                // F05-15 X-40 收口后纳入门禁：配置写路径审计必须 fail-closed（假审计 log 已移除）
                || s.endsWith("/controller/CognitiveConfigController.java")
                // #86 REQ-COG-07 注册中心收口面（V261 应用侧待办）：DAO 表名/列名收口后纳入
                // schema 限定 + 方言 + 三层纪律门禁（::jsonb 随 features_json TEXT 化移除）
                || s.endsWith("/dao/ModelRegistryDao.java")
                || s.endsWith("/service/ModelRegistryService.java")
                || s.endsWith("/service/ModelPublishGateEvaluator.java")
                // 差距分析附录A 第 19 条（#146v）：Decision Basis 只读解析面自落地即纳入门禁
                // （schema 限定 / 方言 / 零 DML / 空集回落 / 不泄漏 message 五门逐行判定）
                || s.endsWith("/service/decision/DecisionBasisService.java")
                || s.endsWith("/controller/DecisionBasisController.java")
                // 差距分析附录A 第 17 条（#146w）：world-model 四段 501 承流面自落地即纳入同一组
                // 门禁（零 DML / 禁跨域表引 / 无 cognitive_context* / 无空集回落 / 不泄漏 message）
                || s.endsWith("/controller/CognitiveWorldModelReadController.java");
    }

    /** 遍历 in-scope 的 .java 生产源（跳过 test）。 */
    public static Stream<Path> streamInScope() throws IOException {
        return Files.walk(MAIN)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(CogScope::inScope);
    }

    /**
     * 遍历 cognitive2 <b>全量</b>主源码（F05-15 底座收口门禁面：RestTemplate/自建线程池/审计绕道）。
     *
     * <p>豁免：{@code /config/} 包——该包是共享客户端 Bean 工厂（如
     * {@code CognitiveRestClientConfig#cognitiveRestTemplate}），铁律允许其集中构造
     * RestTemplate 并下发超时策略；业务 Service 只准注入，不准自建。</p>
     */
    public static Stream<Path> streamMainAll() throws IOException {
        return Files.walk(MAIN)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.toString().replace('\\', '/').contains("/config/"));
    }

    /** 剥离块/行注释（保行数）：源码门禁只判代码，Javadoc 里的禁词示例不是违例。 */
    public static String stripComments(String src) {
        return SourceCommentStripper.strip(src);
    }

    /** 遍历某 root 下所有 .java（用于 pom / DDL 等定点扫描），跳过 test 目录（跨平台）。 */
    static Stream<Path> walkJava(Path root) throws IOException {
        return Files.walk(root)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.toString().replace('\\', '/').contains("/test/"));
    }

    public static int lineOf(String s, int off) {
        int line = 1;
        for (int i = 0; i < off && i < s.length(); i++) if (s.charAt(i) == '\n') line++;
        return line;
    }
}
