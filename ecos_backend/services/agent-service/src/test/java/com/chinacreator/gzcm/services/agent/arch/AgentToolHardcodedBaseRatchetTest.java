package com.chinacreator.gzcm.services.agent.arch;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentToolEndpointResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * agent 工具腿<b>基址单源</b>门禁（首版 #142/B4-3e-8 登记差距 <b>D-B4-46</b> 为棘轮清单，
 * #142/B4-3e-9 把在册 8 腿全部收口 ⇒ 本批起本门是<b>零容忍门</b>而不是棘轮）。
 *
 * <p><b>为什么原来的门禁拦不住</b>：既有 {@code EndpointResolverUsageArchTest}（data-engine）
 * 的禁用字面量是 {@code http://localhost:180}，只钉 {@code :1808x} 形态；{@code :8080}
 * （monolith 端口）形态全仓无门。本域 8 支工具此前都把 {@code http://localhost:8080}
 * 整串内联在请求点上，绕开 route-manifest 两态寻址单源——切流后即断，
 * 而 gateway 与 monolith 同端口的现网形态把问题掩盖住了（D-B4-43 的正身即此族的 security 裁决腿）。</p>
 *
 * <p><b>工具面实测定数（#142/B4-3e-9 首跑变红后订正，#142/B4-3e-16、#142/B4-3e-22 族迁移后再次订正，
 * #146j/B4-3e-91 把纯 DAO 族最后 8 支只读腿全数迁入 HTTP 出腿族后第三度订正）</b>：
 * 本目录 {@code *Tool.java} 共 <b>20</b> 支，本批起<b>全部 20 支同属 HTTP 出腿族</b>
 * （判据=源码含 {@code RestTemplate}），基址一律经解析器：原 12 支之外，#146j/B4-3e-91
 * 把原纯 DAO 族 8 支只读直连腿（{@code ExecuteExpertRuleTool}／{@code FindGraphPathTool}／
 * {@code GetDataLineageTool}／{@code GetTableSchemaTool}／{@code ListDataSourcesTool}／
 * {@code RAGQueryTool}／{@code SearchCatalogTool}／{@code SearchKnowledgeGraphTool}）
 * 全部改指属主 REST 端点（kb 域属主 {@code dccheng}、data 域属主 {@code datanet}），
 * {@code AgentToolDao} 只读面同批下线（类骨架保留给
 * {@code AgentToolOwnerWriteChannelSingleSourceTest} 钉写面）。首版把 D-B4-46 记成
 * "9 支工具"是把工具面总数当成了 HTTP 出腿数。<b>纯 DAO 族直连引擎表 = 独立缺陷
 * D-B4-49</b>（绕开 security-engine 裁决与引擎写通道，铁律 §0.6/§5 第 3、5 条）；
 * 其三个子项分批收口：<b>任意 SQL 下推</b>由 #142/B4-3e-16 收口（{@code QueryPhysicalTableTool}
 * 迁入 HTTP 出腿族），<b>进程内写腿</b>由 #142/B4-3e-22 收口（另两支迁入 HTTP 出腿族，
 * 由 {@code AgentToolOwnerWriteChannelSingleSourceTest} 钉写面归零），
 * <b>只读直连腿 8 支</b>由 <b>#146j/B4-3e-91 本批收口</b>（D-B4-49 关账）。</p>
 *
 * <p><b>判据为什么锚在调用形态上</b>：请求点字面量前必然带
 * {@code getForObject("}／{@code postForObject("}／{@code exchange("} 等调用形，
 * 而 javadoc 里作为"收口前形态"提及的字面量不带调用前缀 ⇒ 锚定调用形态天然对注释安全，
 * 本门不需要引号感知剥注释器（那是 D-B4-40 的教训：朴素剥注释会把字符串内的 {@code //} 当注释吃掉）。</p>
 *
 * <p><b>三条腿缺一不可</b>：只禁字面量、不钉委托，则下一位作者可以
 * {@code private static final String BASE = "http://localhost:8080"} 再拼上去绕过调用形态判据；
 * 故同时钉①零硬编码请求点、②HTTP 出腿族逐支经 {@link AgentToolEndpointResolver} 取基址、
 * ③工具包里不得出现任何以 {@code http} 开头的字符串常量。族归属由实测推导后再与在册清单比对，
 * 新增第十三支 HTTP 腿必须先登记属主与访问器。</p>
 */
class AgentToolHardcodedBaseRatchetTest {

    /** 请求点硬编码形态：调用名 + 紧接的 {@code "http://localhost} 字面量。 */
    private static final Pattern HARDCODED_CALL_SITE = Pattern.compile(
            "\\b(?:getForObject|postForObject|getForEntity|postForEntity|exchange|execute)\\(\\s*\"http://localhost");

    /** 以 http 开头的字符串常量声明（堵住"基址挪进变量"的绕行路径）。 */
    private static final Pattern HTTP_LITERAL_CONSTANT = Pattern.compile(
            "\\bString\\s+\\w+\\s*=\\s*\"http://");

    /** HTTP 出腿族（#146j/B4-3e-91 起实测 <b>20</b> 支＝全量：D-B4-46 在册 8 支 +
     *  B4-3e-8 先收口的 CheckPermissionTool + B4-3e-16 迁入的 QueryPhysicalTableTool
     *  + B4-3e-22 迁入的两支写腿 RunQualityCheckTool／TriggerPipelineTool
     *  + B4-3e-91 从纯 DAO 族迁入的 8 支只读腿 ExecuteExpertRule/FindGraphPath/
     *  GetDataLineage/GetTableSchema/ListDataSources/RAGQuery/SearchCatalog/
     *  SearchKnowledgeGraph，D-B4-49 只读直连子项就此关账）。 */
    private static final Set<String> HTTP_EGRESS_TOOLS = Set.of(
            "AuditAccessLogTool.java",
            "CheckPermissionTool.java",
            "ExecuteActionTool.java",
            "ExecuteExpertRuleTool.java",
            "FindGraphPathTool.java",
            "GetDataLineageTool.java",
            "GetObjectPropertiesTool.java",
            "GetObjectRelationshipsTool.java",
            "GetSecurityProfileTool.java",
            "GetTableSchemaTool.java",
            "ListDataSourcesTool.java",
            "ListObjectTypesTool.java",
            "QueryPhysicalTableTool.java",
            "RAGQueryTool.java",
            "RunQualityCheckTool.java",
            "SearchCatalogTool.java",
            "SearchKnowledgeGraphTool.java",
            "SearchOntologyGraphTool.java",
            "TriggerPipelineTool.java",
            "ValidateMappingTool.java");

    /** 纯 DAO 族：<b>#146j/B4-3e-91 关账 D-B4-49 只读直连子项后归零</b>——原在册 8 支
     *  只读腿全部迁上 HTTP 出腿族，AgentToolDao 只读面同批下线（类骨架保留给
     *  AgentToolOwnerWriteChannelSingleSourceTest 钉写面）。任何新增 DAO 直连腿
     *  须先在本批口径下重新立项，不许默默回潮。 */
    private static final Set<String> DAO_ONLY_TOOLS = Set.of();

    /** 工具族 → 该族必须使用的基址访问器（属主名由解析器单源持有，见 D-B4-48）。
     *  {@code knowledgeBaseUrl()}（属主 dccheng）随 #146j/B4-3e-91 kb 域四条腿入册。 */
    private static final Set<String> RESOLVER_ACCESSORS = Set.of(
            "securityBaseUrl()", "auditBaseUrl()", "ontologyBaseUrl()", "dataBaseUrl()",
            "knowledgeBaseUrl()");

    @Test
    @Timeout(value = 60)
    @DisplayName("D-B4-46 已关账：全 20 支工具（#146j/B4-3e-91 起全属 HTTP 出腿族）零硬编码请求点、零 http 字面量常量")
    void noHardcodedBaseSitesRemainInAgentTools() throws IOException {
        List<String> callSiteHits = new ArrayList<>();
        List<String> literalHits = new ArrayList<>();
        for (Path p : toolSources()) {
            String code = Files.readString(p, StandardCharsets.UTF_8);
            String name = p.getFileName().toString();
            Matcher call = HARDCODED_CALL_SITE.matcher(code);
            while (call.find()) {
                callSiteHits.add(name + ": " + call.group());
            }
            Matcher literal = HTTP_LITERAL_CONSTANT.matcher(code);
            while (literal.find()) {
                literalHits.add(name + ": " + literal.group());
            }
        }
        assertEquals(List.of(), callSiteHits,
                "agent 工具退回内联 localhost 请求点 ⇒ D-B4-46 复发，须改走 AgentToolEndpointResolver");
        assertEquals(List.of(), literalHits,
                "agent 工具里出现了 http 基址字符串常量（把基址挪进变量仍是硬编码，同一缺陷换了外形）");
    }

    @Test
    @Timeout(value = 60)
    @DisplayName("HTTP 出腿族按实测推导后钉死清单，且逐支经解析器取基址、族访问器用对")
    void everyHttpEgressToolDelegatesToTheEndpointResolver() throws IOException {
        Set<String> derived = new TreeSet<>();
        List<String> missing = new ArrayList<>();
        for (Path p : toolSources()) {
            String name = p.getFileName().toString();
            String code = Files.readString(p, StandardCharsets.UTF_8);
            if (!code.contains("RestTemplate")) {
                continue;
            }
            derived.add(name);
            boolean injects = code.contains("AgentToolEndpointResolver endpoints");
            boolean usesAccessor = RESOLVER_ACCESSORS.stream().anyMatch(code::contains);
            if (!injects || !usesAccessor) {
                missing.add(name + "（注入解析器=" + injects + "，调用族访问器=" + usesAccessor + "）");
            }
        }
        assertEquals(new TreeSet<>(HTTP_EGRESS_TOOLS), derived,
                "HTTP 出腿工具族与在册清单不符：新增出腿工具必须先登记 route-manifest 属主与基址访问器");
        assertEquals(List.of(), missing,
                "有出腿工具未经基址单源解析器 ⇒ 该腿在切流后会打到已下线端口");
    }

    @Test
    @Timeout(value = 60)
    @DisplayName("工具面总数钉死 20 支（#146j/B4-3e-91 起 20 支全是 HTTP 出腿族、纯 DAO 族归零）：新增须先定族，删支同步删项")
    void toolInventoryMatchesRegisteredBaseline() throws IOException {
        Set<String> actual = new TreeSet<>();
        for (Path p : toolSources()) {
            actual.add(p.getFileName().toString());
        }
        Set<String> expected = new TreeSet<>(HTTP_EGRESS_TOOLS);
        expected.addAll(DAO_ONLY_TOOLS);
        assertEquals(expected, actual,
                "agent 工具清单与在册基线不符（族划分见 D-B4-46／D-B4-49 口径）："
                        + "新增工具必须先声明属于 HTTP 出腿族还是纯 DAO 族；"
                        + "纯 DAO 族自 #146j/B4-3e-91（D-B4-49 只读腿收口）起只准为空");
    }

    /** 列出工具源码（缺目录 / 空清单一律 fail-loud，不得让扫描面恒空读成过门）。 */
    private static List<Path> toolSources() throws IOException {
        Path dir = toolsDir();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.list(dir)) {
            walk.filter(f -> f.toString().endsWith("Tool.java")).forEach(files::add);
        }
        int registered = HTTP_EGRESS_TOOLS.size() + DAO_ONLY_TOOLS.size();
        assertTrue(files.size() >= registered,
                "工具源码扫描面异常（实扫 " + files.size() + " 支 < 在册 " + registered
                        + " 支），门禁不得对空扫描面放行: " + dir);
        return files;
    }

    /** 从模块工作目录上溯定位 {@code ecos_backend}，再取工具源码目录（缺则 fail-loud）。 */
    private static Path toolsDir() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("ecos_backend"))) {
            dir = dir.getParent();
        }
        assertTrue(dir != null, "上溯未找到 ecos_backend 根，无法定位工具目录");
        Path tools = dir.resolve("ecos_backend")
                .resolve("services/agent-service/src/main/java/com/chinacreator/gzcm/services/agent/runtime/toolrouter/tools");
        assertTrue(Files.isDirectory(tools), "工具源码目录不存在: " + tools);
        return tools;
    }
}
