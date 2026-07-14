package com.chinacreator.gzcm.runtime.core.format.compression.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import net.jpountz.lz4.LZ4Compressor;

import com.chinacreator.gzcm.runtime.core.format.compression.Compression;
import com.chinacreator.gzcm.runtime.core.format.compression.CompressionException;

/**
 * LZ4压缩实现
 * 
 * @author CDRC Runtime Team
 */
public class Lz4Compression implements Compression {
    
    private final LZ4Factory factory = LZ4Factory.fastestInstance();
    private int compressionLevel = -1; // LZ4不支持设置压缩级别
    
    @Override
    public byte[] compress(byte[] data) throws CompressionException {
        try {
            LZ4Compressor compressor = factory.fastCompressor();
            int maxCompressedLength = compressor.maxCompressedLength(data.length);
            byte[] compressed = new byte[maxCompressedLength];
            int compressedLength = compressor.compress(data, 0, data.length, compressed, 0, maxCompressedLength);
            
            byte[] result = new byte[compressedLength];
            System.arraycopy(compressed, 0, result, 0, compressedLength);
            return result;
        } catch (Exception e) {
            throw new CompressionException("Failed to compress data with LZ4", e);
        }
    }
    
    @Override
    public byte[] decompress(byte[] compressedData) throws CompressionException {
        try {
            LZ4FastDecompressor decompressor = factory.fastDecompressor();
            // 需要知道原始数据长度，这里使用估算值（实际应该存储原始长度）
            int originalLength = compressedData.length * 4; // 估算
            byte[] decompressed = new byte[originalLength];
            int decompressedLength = decompressor.decompress(compressedData, 0, decompressed, 0, originalLength);
            
            byte[] result = new byte[decompressedLength];
            System.arraycopy(decompressed, 0, result, 0, decompressedLength);
            return result;
        } catch (Exception e) {
            throw new CompressionException("Failed to decompress LZ4 data", e);
        }
    }
    
    @Override
    public InputStream compressStream(InputStream input) throws CompressionException {
        try {
            byte[] data = readAllBytes(input);
            byte[] compressed = compress(data);
            return new ByteArrayInputStream(compressed);
        } catch (Exception e) {
            throw new CompressionException("Failed to create LZ4 compression stream", e);
        }
    }
    
    @Override
    public InputStream decompressStream(InputStream compressedInput) throws CompressionException {
        try {
            byte[] compressed = readAllBytes(compressedInput);
            byte[] decompressed = decompress(compressed);
            return new ByteArrayInputStream(decompressed);
        } catch (Exception e) {
            throw new CompressionException("Failed to create LZ4 decompression stream", e);
        }
    }
    
    @Override
    public void compressToStream(InputStream input, OutputStream output) throws CompressionException {
        try {
            byte[] data = readAllBytes(input);
            byte[] compressed = compress(data);
            output.write(compressed);
        } catch (IOException e) {
            throw new CompressionException("Failed to compress stream with LZ4", e);
        }
    }
    
    @Override
    public void decompressToStream(InputStream compressedInput, OutputStream output) throws CompressionException {
        try {
            byte[] compressed = readAllBytes(compressedInput);
            byte[] decompressed = decompress(compressed);
            output.write(decompressed);
        } catch (IOException e) {
            throw new CompressionException("Failed to decompress LZ4 stream", e);
        }
    }
    
    @Override
    public String getAlgorithmName() {
        return "LZ4";
    }
    
    @Override
    public int getCompressionLevel() {
        return compressionLevel;
    }
    
    @Override
    public void setCompressionLevel(int level) {
        // LZ4不支持设置压缩级别
        this.compressionLevel = -1;
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

