package com.chinacreator.gzcm.runtime.core.datadescription.model;

import java.util.Map;

/**
 * 鏁版嵁鍏冩暟鎹帴鍙?
 * 瀹氫箟鏁版嵁鐨勬弿杩版€т俊鎭?
 * 
 * @author CDRC Runtime Team
 */
public interface DataMetadata {
    
    /**
     * 鑾峰彇鏁版嵁鍚嶇О
     * 
     * @return 鏁版嵁鍚嶇О
     */
    String getName();
    
    /**
     * 璁剧疆鏁版嵁鍚嶇О
     * 
     * @param name 鏁版嵁鍚嶇О
     */
    void setName(String name);
    
    /**
     * 鑾峰彇鏁版嵁鎻忚堪
     * 
     * @return 鏁版嵁鎻忚堪
     */
    String getDescription();
    
    /**
     * 璁剧疆鏁版嵁鎻忚堪
     * 
     * @param description 鏁版嵁鎻忚堪
     */
    void setDescription(String description);
    
    /**
     * 鑾峰彇鏁版嵁绫诲瀷
     * 
     * @return 鏁版嵁绫诲瀷
     */
    String getType();
    
    /**
     * 璁剧疆鏁版嵁绫诲瀷
     * 
     * @param type 鏁版嵁绫诲瀷
     */
    void setType(String type);
    
    /**
     * 鑾峰彇鏁版嵁鏍煎紡
     * 
     * @return 鏁版嵁鏍煎紡锛堝锛欽SON銆丆SV銆丳arquet绛夛級
     */
    String getFormat();
    
    /**
     * 璁剧疆鏁版嵁鏍煎紡
     * 
     * @param format 鏁版嵁鏍煎紡
     */
    void setFormat(String format);
    
    /**
     * 鑾峰彇鏁版嵁澶у皬
     * 
     * @return 鏁版嵁澶у皬锛堝瓧鑺傛暟锛?
     */
    Long getSize();
    
    /**
     * 璁剧疆鏁版嵁澶у皬
     * 
     * @param size 鏁版嵁澶у皬锛堝瓧鑺傛暟锛?
     */
    void setSize(Long size);
    
    /**
     * 鑾峰彇缂栫爜鏍煎紡
     * 
     * @return 缂栫爜鏍煎紡锛堝锛歎TF-8銆丟BK绛夛級
     */
    String getEncoding();
    
    /**
     * 璁剧疆缂栫爜鏍煎紡
     * 
     * @param encoding 缂栫爜鏍煎紡
     */
    void setEncoding(String encoding);
    
    /**
     * 鑾峰彇鎵╁睍灞炴€?
     * 
     * @return 鎵╁睍灞炴€ap
     */
    Map<String, Object> getExtensions();
    
    /**
     * 璁剧疆鎵╁睍灞炴€?
     * 
     * @param extensions 鎵╁睍灞炴€ap
     */
    void setExtensions(Map<String, Object> extensions);
    
    /**
     * 娣诲姞鎵╁睍灞炴€?
     * 
     * @param key 灞炴€ч敭
     * @param value 灞炴€у€?
     */
    void addExtension(String key, Object value);
    
    /**
     * 鑾峰彇鎵╁睍灞炴€у€?
     * 
     * @param key 灞炴€ч敭
     * @return 灞炴€у€?
     */
    Object getExtension(String key);
    
    /**
     * 绉婚櫎鎵╁睍灞炴€?
     * 
     * @param key 灞炴€ч敭
     * @return 琚Щ闄ょ殑灞炴€у€?
     */
    Object removeExtension(String key);
}

