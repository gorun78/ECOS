package com.chinacreator.gzcm.cli;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** 标准输出统一 UTF-8（Java 17 默认跟随平台编码，Windows 控制台会乱码）。 */
final class Stdio {

    private static final PrintStream OUT =
            new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
    private static final PrintStream ERR =
            new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);

    static void print(String text) {
        OUT.print(text);
        OUT.flush();
    }

    static void println(String text) {
        OUT.println(text);
    }

    static void error(String text) {
        ERR.println(text);
    }

    private Stdio() {
    }
}
