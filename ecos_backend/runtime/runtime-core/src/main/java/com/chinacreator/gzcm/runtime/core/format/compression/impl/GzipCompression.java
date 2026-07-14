package com.chinacreator.gzcm.runtime.core.format.compression.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.chinacreator.gzcm.runtime.core.format.compression.Compression;
import com.chinacreator.gzcm.runtime.core.format.compression.CompressionException;

/**
 * GZIP压缩实现
 * 
 * @author CDRC Runtime Team
 */
public class GzipCompression implements Compression {
    
    private int compressionLevel = -1; // GZIP不支持设置压缩级别
    
    @Override
    public byte[] compress(byte[] data) throws CompressionException {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
                gzos.write(data);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new CompressionException("Failed to compress data with GZIP", e);
        }
    }
    
    @Override
    public byte[] decompress(byte[] compressedData) throws CompressionException {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
            try (GZIPInputStream gzis = new GZIPInputStream(bais);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = gzis.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                return baos.toByteArray();
            }
        } catch (IOException e) {
            throw new CompressionException("Failed to decompress GZIP data", e);
        }
    }
    
    @Override
    public InputStream compressStream(InputStream input) throws CompressionException {
        try {
            return new GZIPInputStream(input);
        } catch (IOException e) {
            throw new CompressionException("Failed to create GZIP compression stream", e);
        }
    }
    
    @Override
    public InputStream decompressStream(InputStream compressedInput) throws CompressionException {
        try {
            return new GZIPInputStream(compressedInput);
        } catch (IOException e) {
            throw new CompressionException("Failed to create GZIP decompression stream", e);
        }
    }
    
    @Override
    public void compressToStream(InputStream input, OutputStream output) throws CompressionException {
        try (GZIPOutputStream gzos = new GZIPOutputStream(output)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) != -1) {
                gzos.write(buffer, 0, len);
            }
        } catch (IOException e) {
            throw new CompressionException("Failed to compress stream with GZIP", e);
        }
    }
    
    @Override
    public void decompressToStream(InputStream compressedInput, OutputStream output) throws CompressionException {
        try (GZIPInputStream gzis = new GZIPInputStream(compressedInput)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = gzis.read(buffer)) != -1) {
                output.write(buffer, 0, len);
            }
        } catch (IOException e) {
            throw new CompressionException("Failed to decompress GZIP stream", e);
        }
    }
    
    @Override
    public String getAlgorithmName() {
        return "GZIP";
    }
    
    @Override
    public int getCompressionLevel() {
        return compressionLevel;
    }
    
    @Override
    public void setCompressionLevel(int level) {
        // GZIP不支持设置压缩级别
        this.compressionLevel = -1;
    }
}

