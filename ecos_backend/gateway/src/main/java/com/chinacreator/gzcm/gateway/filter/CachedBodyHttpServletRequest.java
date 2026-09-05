package com.chinacreator.gzcm.gateway.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * P0-3' 修复：包装 HttpServletRequest 使 body 可重读。
 *
 * <p>QuotaFilter 内部用 {@code jdbc.query} 执行原子 UPDATE 时，spring-tomcat 已将
 * request body 流化一遍。如果下游 Spring MVC {@code @RequestBody} 再次读取，会因
 * InputStream 已被消费而 EOF → 400 {@code HttpMessageNotReadableException}。
 *
 * <p>本 wrapper 在构造时一次性读全 body 到内存 {@code bodyBuf}，
 * 之后 {@link #getInputStream()} / {@link #getReader()} 均从该 buffer 返回新流。
 * 仅对带 body 的方法（POST/PUT/PATCH）启用，GET/DELETE 不包装。
 *
 * <p>仅 override 这两个方法，其它 servlet API 透传 {@code super}。
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    /** 缓存的 request body 字节，UTF-8 */
    private final byte[] bodyBuf;

    public CachedBodyHttpServletRequest(HttpServletRequest request) {
        super(request);
        InputStream rawIn = null;
        try {
            rawIn = request.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int r;
            while ((r = rawIn.read(chunk)) != -1) {
                buffer.write(chunk, 0, r);
            }
            this.bodyBuf = buffer.toByteArray();
        } catch (IOException e) {
            // servlet 读取 body 失败 → 记录空 body 并标记下游应 reject
            // 上游 spring 异常链会在 chain.doFilter 处异步补 400
            throw new UncheckedIOException("Failed to cache request body", e);
        } finally {
            if (rawIn != null) {
                try {
                    rawIn.close();
                } catch (IOException ignored) {
                    // 不抛
                }
            }
        }
    }

    /** 返回 body 字节流的 InputStream，可重复读取 */
    @Override
    public ServletInputStream getInputStream() {
        final byte[] buf = bodyBuf;
        return new ServletInputStream() {
            private final ByteArrayInputStream in = new ByteArrayInputStream(buf);
            private ReadListener readListener;

            @Override
            public boolean isFinished() {
                return in.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                this.readListener = listener;
            }

            @Override
            public int read() {
                int v = in.read();
                // 若设置了 readListener，这里可以异步回调；同步实现更安全
                return v;
            }
        };
    }

    /** 返回 body 的 Reader（UTF-8） */
    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bodyBuf), StandardCharsets.UTF_8));
    }
}
