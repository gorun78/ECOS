package com.chinacreator.gzcm.runtime.core.format.compression.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.xerial.snappy.Snappy;

import com.chinacreator.gzcm.runtime.core.format.compression.Compression;
import com.chinacreator.gzcm.runtime.core.format.compression.CompressionException;

/**
 * Snappy压缩实现
 * 
 * @author CDRC Runtime Team
 */
public class SnappyCompression implements Compression {
    
    private int compressionLevel = -1; // Snappy不支持设置压缩级别
    
    @Override
    public byte[] compress(byte[] data) throws CompressionException {
        try {
            return Snappy.compress(data);
        } catch (IOException e) {
            throw new CompressionException("Failed to compress data with Snappy", e);
        }
    }
    
    @Override
    public byte[] decompress(byte[] compressedData) throws CompressionException {
        try {
            return Snappy.uncompress(compressedData);
        } catch (IOException e) {
            throw new CompressionException("Failed to decompress Snappy data", e);
        }
    }
    
    @Override
    public InputStream compressStream(InputStream input) throws CompressionException {
        try {
            byte[] data = readAllBytes(input);
            byte[] compressed = compress(data);
            return new ByteArrayInputStream(compressed);
        } catch (Exception e) {
            throw new CompressionException("Failed to create Snappy compression stream", e);
        }
    }
    
    @Override
    public InputStream decompressStream(InputStream compressedInput) throws CompressionException {
        try {
            byte[] compressed = readAllBytes(compressedInput);
            byte[] decompressed = decompress(compressed);
            return new ByteArrayInputStream(decompressed);
        } catch (Exception e) {
            throw new CompressionException("Failed to create Snappy decompression stream", e);
        }
    }
    
    @Override
    public void compressToStream(InputStream input, OutputStream output) throws CompressionException {
        try {
            byte[] data = readAllBytes(input);
            byte[] compressed = compress(data);
            output.write(compressed);
        } catch (IOException e) {
            throw new CompressionException("Failed to compress stream with Snappy", e);
        }
    }
    
    @Override
    public void decompressToStream(InputStream compressedInput, OutputStream output) throws CompressionException {
        try {
            byte[] compressed = readAllBytes(compressedInput);
            byte[] decompressed = decompress(compressed);
            output.write(decompressed);
        } catch (IOException e) {
            throw new CompressionException("Failed to decompress Snappy stream", e);
        }
    }
    
    @Override
    public String getAlgorithmName() {
        return "Snappy";
    }
    
    @Override
    public int getCompressionLevel() {
        return compressionLevel;
    }
    
    @Override
    public void setCompressionLevel(int level) {
        // Snappy不支持设置压缩级别
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

