package com.chinacreator.gzcm.runtime.core.format;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import com.chinacreator.gzcm.runtime.core.format.model.FormatContext;
import com.chinacreator.gzcm.runtime.core.format.model.FormatMetadata;

/**
 * 鏍煎紡杞崲鍣ㄦ帴鍙?
 * 鎻愪緵缁熶竴鐨勬暟鎹牸寮忚浆鎹㈣兘鍔?
 * 
 * @author CDRC Runtime Team
 */
public interface FormatConverter {
    
    /**
     * 璇诲彇鏁版嵁
     * 
     * @param <T> 鏁版嵁绫诲瀷
     * @param input 杈撳叆娴?
     * @param sourceFormat 婧愭牸寮?
     * @param clazz 鐩爣绫诲瀷
     * @param context 鏍煎紡涓婁笅鏂?
     * @return 鏁版嵁鍒楄〃
     * @throws FormatException
     */
    <T> List<T> read(InputStream input, Format sourceFormat, Class<T> clazz, FormatContext context) 
            throws FormatException;
    
    /**
     * 鍐欏叆鏁版嵁
     * 
     * @param data 鏁版嵁鍒楄〃
     * @param output 杈撳嚭娴?
     * @param targetFormat 鐩爣鏍煎紡
     * @param context 鏍煎紡涓婁笅鏂?
     * @throws FormatException
     */
    void write(List<?> data, OutputStream output, Format targetFormat, FormatContext context) 
            throws FormatException;
    
    /**
     * 鏍煎紡杞崲
     * 
     * @param input 杈撳叆娴?
     * @param sourceFormat 婧愭牸寮?
     * @param output 杈撳嚭娴?
     * @param targetFormat 鐩爣鏍煎紡
     * @param sourceContext 婧愭牸寮忎笂涓嬫枃
     * @param targetContext 鐩爣鏍煎紡涓婁笅鏂?
     * @throws FormatException
     */
    void convert(InputStream input, Format sourceFormat, OutputStream output, Format targetFormat,
                 FormatContext sourceContext, FormatContext targetContext) throws FormatException;
    
    /**
     * 楠岃瘉鏍煎紡
     * 
     * @param input 杈撳叆娴?
     * @param format 鏍煎紡
     * @param metadata 鏍煎紡鍏冩暟鎹?
     * @return 鏄惁鏈夋晥
     * @throws FormatException
     */
    boolean validate(InputStream input, Format format, FormatMetadata metadata) throws FormatException;
    
    /**
     * 鑾峰彇鏀寔鐨勬牸寮?
     * 
     * @return 鏀寔鐨勬牸寮忓垪琛?
     */
    List<Format> getSupportedFormats();
    
    /**
     * 鍒ゆ柇鏄惁鏀寔鎸囧畾鏍煎紡
     * 
     * @param format 鏍煎紡
     * @return 鏄惁鏀寔
     */
    boolean supports(Format format);
}

