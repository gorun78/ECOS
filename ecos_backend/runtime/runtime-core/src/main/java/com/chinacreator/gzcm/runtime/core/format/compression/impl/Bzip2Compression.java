package com.chinacreator.gzcm.runtime.core.format.compression.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

import com.chinacreator.gzcm.runtime.core.format.compression.Compression;
import com.chinacreator.gzcm.runtime.core.format.compression.CompressionException;

/**
 * BZIP2压缩实现
 * 
 * @author CDRC Runtime Team
 */
public class Bzip2Compression implements Compression {
    
    private int compressionLevel = 9; // BZIP2支持1-9的压缩级别
    
    @Override
    public byte[] compress(byte[] data) throws CompressionException {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (BZip2CompressorOutputStream bzos = new BZip2CompressorOutputStream(baos)) {
                bzos.write(data);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new CompressionException("Failed to compress data with BZIP2", e);
        }
    }
    
    @Override
    public byte[] decompress(byte[] compressedData) throws CompressionException {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
            try (BZip2CompressorInputStream bzis = new BZip2CompressorInputStream(bais);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = bzis.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                return baos.toByteArray();
            }
        } catch (IOException e) {
            throw new CompressionException("Failed to decompress BZIP2 data", e);
        }
    }
    
    @Override
    public InputStream compressStream(InputStream input) throws CompressionException {
        // BZIP2压缩流需要输出流，这里返回一个包装的输入流
        // 实际使用中应该使用compressToStream方法
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            compressToStream(input, baos);
            return new ByteArrayInputStream(baos.toByteArray());
        } catch (Exception e) {
            throw new CompressionException("Failed to create BZIP2 compression stream", e);
        }
    }
    
    @Override
    public InputStream decompressStream(InputStream compressedInput) throws CompressionException {
        try {
            return new BZip2CompressorInputStream(compressedInput);
        } catch (IOException e) {
            throw new CompressionException("Failed to create BZIP2 decompression stream", e);
        }
    }
    
    @Override
    public void compressToStream(InputStream input, OutputStream output) throws CompressionException {
        try (BZip2CompressorOutputStream bzos = new BZip2CompressorOutputStream(output)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) != -1) {
                bzos.write(buffer, 0, len);
            }
        } catch (IOException e) {
            throw new CompressionException("Failed to compress stream with BZIP2", e);
        }
    }
    
    @Override
    public void decompressToStream(InputStream compressedInput, OutputStream output) throws CompressionException {
        try (BZip2CompressorInputStream bzis = new BZip2CompressorInputStream(compressedInput)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = bzis.read(buffer)) != -1) {
                output.write(buffer, 0, len);
            }
        } catch (IOException e) {
            throw new CompressionException("Failed to decompress BZIP2 stream", e);
        }
    }
    
    @Override
    public String getAlgorithmName() {
        return "BZIP2";
    }
    
    @Override
    public int getCompressionLevel() {
        return compressionLevel;
    }
    
    @Override
    public void setCompressionLevel(int level) {
        if (level < 1 || level > 9) {
            throw new IllegalArgumentException("BZIP2 compression level must be between 1 and 9");
        }
        this.compressionLevel = level;
    }
}

