package com.chinacreator.gzcm.runtime.core.common.fileupload.dao;

import java.util.List;
import com.chinacreator.gzcm.runtime.core.common.fileupload.bean.FileUploadHistory;

/**
 * 文件上传DAO接口
 */
public interface IFileUploadDao {
    
    /**
     * 根据ID获取文件上传历史记录
     * @param fileUpId 文件上传ID
     * @return 文件上传历史记录
     * @throws Exception 获取失败时抛出异常
     */
    FileUploadHistory getFileUploadHistoryById(String fileUpId) throws Exception;
    
    /**
     * 根据ID获取文件上传历史记录（别名方法，兼容旧代码）
     * @param fileUpId 文件上传ID
     * @return 文件上传历史记录
     * @throws Exception 获取失败时抛出异常
     */
    default FileUploadHistory selectFileUploadHistoryById(String fileUpId) throws Exception {
        return getFileUploadHistoryById(fileUpId);
    }
    
    /**
     * 根据条件查询文件上传历史记录列表
     * @param condition 查询条件
     * @return 文件上传历史记录列表
     * @throws Exception 查询失败时抛出异常
     */
    List<FileUploadHistory> getFileUploadHistoryList(Object condition) throws Exception;
    
    /**
     * 保存文件上传历史记录
     * @param history 文件上传历史记录
     * @throws Exception 保存失败时抛出异常
     */
    void saveFileUploadHistory(FileUploadHistory history) throws Exception;
    
    /**
     * 删除文件上传历史记录
     * @param fileUpId 文件上传ID
     * @throws Exception 删除失败时抛出异常
     */
    void deleteFileUploadHistory(String fileUpId) throws Exception;
}
