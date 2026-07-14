package com.chinacreator.gzcm.runtime.core.format.compression.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.github.luben.zstd.Zstd;

import com.chinacreator.gzcm.runtime.core.format.compression.Compression;
import com.chinacreator.gzcm.runtime.core.format.compression.CompressionException;

/**
 * ZStandard压缩实现
 * 
 * @author CDRC Runtime Team
 */
public class ZstandardCompression implements Compression {
    
    private int compressionLevel = 3; // ZStandard支持1-22的压缩级别，默认3
    
    @Override
    public byte[] compress(byte[] data) throws CompressionException {
        try {
            return Zstd.compress(data, compressionLevel);
        } catch (Exception e) {
            throw new CompressionException("Failed to compress data with ZStandard", e);
        }
    }
    
    @Override
    public byte[] decompress(byte[] compressedData) throws CompressionException {
        try {
            long decompressedSize = Zstd.decompressedSize(compressedData);
            if (decompressedSize > 0) {
                byte[] result = new byte[(int) decompressedSize];
                Zstd.decompress(result, compressedData);
                return result;
            } else {
                // 如果无法确定大小，使用估算值
                byte[] result = new byte[compressedData.length * 4];
                int size = (int) Zstd.decompress(result, compressedData);
                byte[] finalResult = new byte[size];
                System.arraycopy(result, 0, finalResult, 0, size);
                return finalResult;
            }
        } catch (Exception e) {
            throw new CompressionException("Failed to decompress ZStandard data", e);
        }
    }
    
    @Override
    public InputStream compressStream(InputStream input) throws CompressionException {
        try {
            byte[] data = readAllBytes(input);
            byte[] compressed = compress(data);
            return new ByteArrayInputStream(compressed);
        } catch (Exception e) {
            throw new CompressionException("Failed to create ZStandard compression stream", e);
        }
    }
    
    @Override
    public InputStream decompressStream(InputStream compressedInput) throws CompressionException {
        try {
            byte[] compressed = readAllBytes(compressedInput);
            byte[] decompressed = decompress(compressed);
            return new ByteArrayInputStream(decompressed);
        } catch (Exception e) {
            throw new CompressionException("Failed to create ZStandard decompression stream", e);
        }
    }
    
    @Override
    public void compressToStream(InputStream input, OutputStream output) throws CompressionException {
        try {
            byte[] data = readAllBytes(input);
            byte[] compressed = compress(data);
            output.write(compressed);
        } catch (IOException e) {
            throw new CompressionException("Failed to compress stream with ZStandard", e);
        }
    }
    
    @Override
    public void decompressToStream(InputStream compressedInput, OutputStream output) throws CompressionException {
        try {
            byte[] compressed = readAllBytes(compressedInput);
            byte[] decompressed = decompress(compressed);
            output.write(decompressed);
        } catch (IOException e) {
            throw new CompressionException("Failed to decompress ZStandard stream", e);
        }
    }
    
    @Override
    public String getAlgorithmName() {
        return "ZStandard";
    }
    
    @Override
    public int getCompressionLevel() {
        return compressionLevel;
    }
    
    @Override
    public void setCompressionLevel(int level) {
        if (level < 1 || level > 22) {
            throw new IllegalArgumentException("ZStandard compression level must be between 1 and 22");
        }
        this.compressionLevel = level;
    }
    
    private byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int nRead;
        while ((nRead = input.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
}

