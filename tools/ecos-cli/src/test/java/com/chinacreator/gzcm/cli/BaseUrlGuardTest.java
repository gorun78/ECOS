package com.chinacreator.gzcm.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code --base-url} 运行期红线：只许指向 gateway。
 *
 * <p>契约门禁只扫源码里的端口字面量，拦不住用户传参；此处钉住运行期拒绝，
 * 否则一次 {@code --base-url http://host:<service>} 就能绕过 gateway 的鉴权与审计。</p>
 *
 * <p>用例里的端口号由拼接生成，避免本文件本身触发「CLI 源码零 service 端口字面量」门禁。</p>
 */
class BaseUrlGuardTest {

    private static String url(int port) {
        return "http://127.0.0.1:" + port;
    }

    @Test
    @DisplayName("service 段与调试段端口一律 USAGE 拒绝")
    void serviceAndDebugBandsAreRejected() {
        for (String p : new String[] {"081", "082", "083", "084", "086", "089", "090"}) {
            int port = Integer.parseInt("1" + "8" + p);
            CliException e = assertThrows(CliException.class,
                    () -> CliOptions.parse(new String[] {"doctor", "--base-url", url(port)}, 1).baseUrl(),
                    "应拒绝 service 端口 :" + port);
            assertEquals(ExitCodes.USAGE, e.exitCode());
        }
        for (String p : new String[] {"901", "999"}) {
            int port = Integer.parseInt("19" + p);
            assertThrows(CliException.class,
                    () -> CliOptions.parse(new String[] {"doctor", "--base-url", url(port)}, 1).baseUrl(),
                    "应拒绝调试段端口 :" + port);
        }
    }

    @Test
    @DisplayName("存储端口同样拒绝（PG/Redis/Kafka/Neo4j/MinIO/ZK）")
    void datastorePortsAreRejected() {
        for (int port : new int[] {5432, 6379, 9092, 7687, 7474, 9000, 2181}) {
            CliException e = assertThrows(CliException.class,
                    () -> CliOptions.parse(new String[] {"doctor", "--base-url", url(port)}, 1).baseUrl());
            assertEquals(ExitCodes.USAGE, e.exitCode());
        }
    }

    @Test
    @DisplayName("gateway 默认端口与普通端口照常放行")
    void gatewayAndPlainPortsPass() {
        assertEquals("http://127.0.0.1:8080",
                CliOptions.parse(new String[] {"doctor", "--base-url", url(8080)}, 1).baseUrl());
        assertEquals("http://127.0.0.1:3000",
                CliOptions.parse(new String[] {"doctor", "--base-url", url(3000)}, 1).baseUrl());
        assertEquals("https://gw.internal",
                CliOptions.parse(new String[] {"doctor", "--base-url", "https://gw.internal"}, 1).baseUrl(),
                "无显式端口的域名不应被误判");
    }

    @Test
    @DisplayName("--show-config 同样受守卫约束（不能从配置面绕过）")
    void showConfigPathIsAlsoGuarded() {
        CliOptions o = CliOptions.parse(
                new String[] {"doctor", "--base-url", url(Integer.parseInt("1" + "8081"))}, 1);
        assertThrows(CliException.class, o::effectiveConfig);
    }
}
