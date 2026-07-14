package com.chinacreator.gzcm.runtime.core.format.compression;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 鍘嬬缉鎺ュ彛
 * 鎻愪緵缁熶竴鐨勬暟鎹帇缂╁拰瑙ｅ帇鑳藉姏
 * 
 * @author CDRC Runtime Team
 */
public interface Compression {
    
    /**
     * 鍘嬬缉鏁版嵁
     * 
     * @param data 鍘熷鏁版嵁
     * @return 鍘嬬缉鍚庣殑鏁版嵁
     * @throws CompressionException
     */
    byte[] compress(byte[] data) throws CompressionException;
    
    /**
     * 瑙ｅ帇鏁版嵁
     * 
     * @param compressedData 鍘嬬缉鍚庣殑鏁版嵁
     * @return 瑙ｅ帇鍚庣殑鏁版嵁
     * @throws CompressionException
     */
    byte[] decompress(byte[] compressedData) throws CompressionException;
    
    /**
     * 鍘嬬缉娴?
     * 
     * @param input 杈撳叆娴?
     * @return 鍘嬬缉鍚庣殑杈撳叆娴?
     * @throws CompressionException
     */
    InputStream compressStream(InputStream input) throws CompressionException;
    
    /**
     * 瑙ｅ帇娴?
     * 
     * @param compressedInput 鍘嬬缉鍚庣殑杈撳叆娴?
     * @return 瑙ｅ帇鍚庣殑杈撳叆娴?
     * @throws CompressionException
     */
    InputStream decompressStream(InputStream compressedInput) throws CompressionException;
    
    /**
     * 鍘嬬缉鍒拌緭鍑烘祦
     * 
     * @param input 杈撳叆娴?
     * @param output 杈撳嚭娴?
     * @throws CompressionException
     */
    void compressToStream(InputStream input, OutputStream output) throws CompressionException;
    
    /**
     * 浠庤緭鍏ユ祦瑙ｅ帇鍒拌緭鍑烘祦
     * 
     * @param compressedInput 鍘嬬缉鍚庣殑杈撳叆娴?
     * @param output 杈撳嚭娴?
     * @throws CompressionException
     */
    void decompressToStream(InputStream compressedInput, OutputStream output) throws CompressionException;
    
    /**
     * 鑾峰彇鍘嬬缉绠楁硶鍚嶇О
     * 
     * @return 鍘嬬缉绠楁硶鍚嶇О
     */
    String getAlgorithmName();
    
    /**
     * 鑾峰彇鍘嬬缉绾у埆锛堝鏋滄敮鎸侊級
     * 
     * @return 鍘嬬缉绾у埆锛?-9锛屾垨-1琛ㄧず榛樿锛?
     */
    int getCompressionLevel();
    
    /**
     * 璁剧疆鍘嬬缉绾у埆锛堝鏋滄敮鎸侊級
     * 
     * @param level 鍘嬬缉绾у埆锛?-9锛?
     */
    void setCompressionLevel(int level);
}

