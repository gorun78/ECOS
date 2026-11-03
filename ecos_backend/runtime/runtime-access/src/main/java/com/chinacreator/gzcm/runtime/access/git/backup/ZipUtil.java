package com.chinacreator.gzcm.runtime.access.git.backup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 备份打包工具（#147G7）：目录 → zip 字节（供 MinIO 上传）。
 *
 * @author ECOS Runtime #147G7
 */
final class ZipUtil {

    private ZipUtil() {
    }

    static byte[] zipDirectory(Path dir) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out);
             Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.filter(Files::isRegularFile).sorted().toList()) {
                zip.putNextEntry(new ZipEntry(dir.relativize(p).toString().replace('\\', '/')));
                zip.write(Files.readAllBytes(p));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
