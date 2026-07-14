package com.chinacreator.gzcm.runtime.core.kettle;

import java.io.File;
import java.io.InputStream;

import com.chinacreator.gzcm.runtime.core.kettle.model.KettleJob;
import com.chinacreator.gzcm.runtime.core.transform.TransformChain;

/**
 * Kettle瀵煎叆鍣ㄦ帴鍙?
 * 璐熻矗灏咾ettle Job杞崲涓哄唴閮ㄧ殑TransformChain
 * 
 * @author CDRC Runtime Team
 */
public interface KettleImporter {
    
    /**
     * 浠庢枃浠跺鍏ettle Job
     * 
     * @param kettleFile Kettle Job鏂囦欢
     * @return Kettle Job妯″瀷
     * @throws KettleException
     */
    KettleJob importKettleJob(File kettleFile) throws KettleException;
    
    /**
     * 浠庤緭鍏ユ祦瀵煎叆Kettle Job
     * 
     * @param input 杈撳叆娴?
     * @return Kettle Job妯″瀷
     * @throws KettleException
     */
    KettleJob importKettleJob(InputStream input) throws KettleException;
    
    /**
     * 灏咾ettle Job杞崲涓篢ransformChain
     * 
     * @param kettleJob Kettle Job妯″瀷
     * @return TransformChain
     * @throws KettleException
     */
    TransformChain convertToTransformChain(KettleJob kettleJob) throws KettleException;
}
