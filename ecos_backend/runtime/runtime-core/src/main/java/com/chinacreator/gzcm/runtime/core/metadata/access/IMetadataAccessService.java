package com.chinacreator.gzcm.runtime.core.metadata.access;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.metadata.model.DataSchema;
import com.chinacreator.gzcm.runtime.core.metadata.model.DataSourceMetadata;
import com.chinacreator.gzcm.runtime.core.metadata.model.TechnicalMetadata;

/**
 * 閸忓啯鏆熼幑顔款問闂傤喗婀囬崝鈩冨复閸?
 * 閹绘劒绶电敮锔剧处鐎涙娈戞妯烩偓褑鍏橀崗鍐╂殶閹诡喛顔栭梻顔垮厴閸?
 * 
 * @author CDRC Runtime Team
 */
public interface IMetadataAccessService {
    
    /**
     * 閼惧嘲褰囬幎鈧張顖氬帗閺佺増宓侀敍鍫濈敨缂傛挸鐡ㄩ敍?
     * 
     * @param metadataId 閹垛偓閺堫垰鍘撻弫鐗堝祦ID
     * @return 閹垛偓閺堫垰鍘撻弫鐗堝祦
     * @throws MetadataAccessException 鐠佸潡妫舵径杈Е閺冭埖濮忛崙鍝勭磽鐢?
     */
    TechnicalMetadata getTechnicalMetadata(String metadataId) throws MetadataAccessException;
    
    /**
     * 閼惧嘲褰嘢chema娣団剝浼呴敍鍫濈敨缂傛挸鐡ㄩ敍?
     * 
     * @param schemaId Schema ID
     * @return Schema娣団剝浼?
     * @throws MetadataAccessException 鐠佸潡妫舵径杈Е閺冭埖濮忛崙鍝勭磽鐢?
     */
    DataSchema getSchema(String schemaId) throws MetadataAccessException;
    
    /**
     * 閼惧嘲褰囬弫鐗堝祦濠ф劕鍘撻弫鐗堝祦閿涘牆鐢紓鎾崇摠閿?
     * 
     * @param datasourceId 閺佺増宓佸┃鎬欴
     * @return 閺佺増宓佸┃鎰帗閺佺増宓?
     * @throws MetadataAccessException 鐠佸潡妫舵径杈Е閺冭埖濮忛崙鍝勭磽鐢?
     */
    DataSourceMetadata getDataSourceMetadata(String datasourceId) throws MetadataAccessException;
    
    /**
     * 閹靛綊鍣洪懢宄板絿閹垛偓閺堫垰鍘撻弫鐗堝祦閿涘牆鐢紓鎾崇摠閿?
     * 
     * @param metadataIds 閹垛偓閺堫垰鍘撻弫鐗堝祦ID閸掓銆?
     * @return 閹垛偓閺堫垰鍘撻弫鐗堝祦Map閿涘ey娑撶皟etadataId閿涘瘉alue娑撶echnicalMetadata
     * @throws MetadataAccessException 鐠佸潡妫舵径杈Е閺冭埖濮忛崙鍝勭磽鐢?
     */
    Map<String, TechnicalMetadata> batchGetTechnicalMetadata(List<String> metadataIds) throws MetadataAccessException;
    
    /**
     * 閸掗攱鏌婄紓鎾崇摠
     * 
     * @param metadataId 閸忓啯鏆熼幑鐢€D閿涘牆顩ч弸婊€璐焠ull閿涘苯鍨崚閿嬫煀閹碘偓閺堝绱︾€涙﹫绱?
     * @throws MetadataAccessException 閸掗攱鏌婃径杈Е閺冭埖濮忛崙鍝勭磽鐢?
     */
    void refreshCache(String metadataId) throws MetadataAccessException;
    
    /**
     * 閸掗攱鏌奡chema缂傛挸鐡?
     * 
     * @param schemaId Schema ID閿涘牆顩ч弸婊€璐焠ull閿涘苯鍨崚閿嬫煀閹碘偓閺堝chema缂傛挸鐡ㄩ敍?
     * @throws MetadataAccessException 閸掗攱鏌婃径杈Е閺冭埖濮忛崙鍝勭磽鐢?
     */
    void refreshSchemaCache(String schemaId) throws MetadataAccessException;
    
    /**
     * 閸掗攱鏌婇弫鐗堝祦濠ф劕鍘撻弫鐗堝祦缂傛挸鐡?
     * 
     * @param datasourceId 閺佺増宓佸┃鎬欴閿涘牆顩ч弸婊€璐焠ull閿涘苯鍨崚閿嬫煀閹碘偓閺堝鏆熼幑顔界爱缂傛挸鐡ㄩ敍?
     * @throws MetadataAccessException 閸掗攱鏌婃径杈Е閺冭埖濮忛崙鍝勭磽鐢?
     */
    void refreshDataSourceCache(String datasourceId) throws MetadataAccessException;
    
    /**
     * 妫板嫮鍎圭紓鎾崇摠
     * 閸旂姾娴囩敮鍝ユ暏閻ㄥ嫬鍘撻弫鐗堝祦閸掓壆绱︾€涙ü鑵?
     * 
     * @param metadataIds 鐟曚線顣╅悜顓犳畱閸忓啯鏆熼幑鐢€D閸掓銆冮敍鍫濐洤閺嬫粈璐焠ull閿涘苯鍨０鍕劰閹碘偓閺堝妞跨捄鍐畱閸忓啯鏆熼幑顕嗙礆
     * @throws MetadataAccessException 妫板嫮鍎规径杈Е閺冭埖濮忛崙鍝勭磽鐢?
     */
    void warmupCache(List<String> metadataIds) throws MetadataAccessException;
    
    /**
     * 閼惧嘲褰囩紓鎾崇摠缂佺喕顓告穱鈩冧紖
     * 
     * @return 缂傛挸鐡ㄧ紒鐔活吀娣団剝浼?
     */
    CacheStatistics getCacheStatistics();
    
    /**
     * 閸忓啯鏆熼幑顔款問闂傤喖绱撶敮?
     */
    class MetadataAccessException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public MetadataAccessException(String message) {
            super(message);
        }
        
        public MetadataAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * 缂傛挸鐡ㄧ紒鐔活吀娣団剝浼?
     */
    class CacheStatistics {
        private long hitCount;  // 閸涙垝鑵戝▎鈩冩殶
        private long missCount;  // 閺堫亜鎳℃稉顓燁偧閺?
        private long totalRequests;  // 閹槒顕Ч鍌涙殶
        private double hitRate;  // 閸涙垝鑵戦悳?
        
        public long getHitCount() {
            return hitCount;
        }
        
        public void setHitCount(long hitCount) {
            this.hitCount = hitCount;
        }
        
        public long getMissCount() {
            return missCount;
        }
        
        public void setMissCount(long missCount) {
            this.missCount = missCount;
        }
        
        public long getTotalRequests() {
            return totalRequests;
        }
        
        public void setTotalRequests(long totalRequests) {
            this.totalRequests = totalRequests;
        }
        
        public double getHitRate() {
            return hitRate;
        }
        
        public void setHitRate(double hitRate) {
            this.hitRate = hitRate;
        }
    }
}

