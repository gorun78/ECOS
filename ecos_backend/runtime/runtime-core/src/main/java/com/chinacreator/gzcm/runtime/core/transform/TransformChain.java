package com.chinacreator.gzcm.runtime.core.transform;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.transform.model.DataFrame;
import com.chinacreator.gzcm.runtime.core.transform.model.TransformResult;

/**
 * 杞崲閾炬帴鍙?
 * 鏀寔澶氫釜杞崲姝ラ鐨勪覆鑱旀墽琛?
 * 
 * @author GZCM Runtime Team
 */
public interface TransformChain {
    
    /**
     * 娣诲姞杞崲姝ラ
     * 
     * @param step 杞崲姝ラ
     * @return 杞崲閾捐嚜韬紙鏀寔閾惧紡璋冪敤锛?
     */
    TransformChain addStep(TransformStep step);
    
    /**
     * 娣诲姞杞崲姝ラ锛堝甫鍙傛暟锛?
     * 
     * @param step 杞崲姝ラ
     * @param params 杞崲鍙傛暟
     * @return 杞崲閾捐嚜韬紙鏀寔閾惧紡璋冪敤锛?
     */
    TransformChain addStep(TransformStep step, java.util.Map<String, Object> params);
    
    /**
     * 鑾峰彇鎵€鏈夎浆鎹㈡楠?
     * 
     * @return 杞崲姝ラ鍒楄〃
     */
    List<TransformStep> getSteps();
    
    /**
     * 鎵ц杞崲閾?
     * 
     * @param input 杈撳叆鏁版嵁妗?
     * @return 转换结果
     * @throws TransformException
     */
    TransformResult execute(DataFrame input) throws TransformException;
    
    /**
     * 娓呯┖杞崲閾?
     */
    void clear();
}

