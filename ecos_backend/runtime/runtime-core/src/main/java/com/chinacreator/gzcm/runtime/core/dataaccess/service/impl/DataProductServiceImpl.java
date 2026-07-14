package com.chinacreator.gzcm.runtime.core.dataaccess.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chinacreator.gzcm.runtime.core.dataaccess.service.IDataProductService;

/**
 * 数据产品服务实现
 * 提供数据产品的管理和访问功能
 * 支持数据产品的注册、查询、更新、删除和版本管理
 * 
 * @author CDRC Runtime Team
 */
public class DataProductServiceImpl implements IDataProductService {

    private static final Logger logger = LoggerFactory.getLogger(DataProductServiceImpl.class);

    // 内存存储：productId -> DataProductInfo
    private final ConcurrentMap<String, DataProductInfo> products = new ConcurrentHashMap<>();
    
    // 版本管理：productId -> version -> DataProductInfo
    private final ConcurrentMap<String, ConcurrentMap<String, DataProductInfo>> productVersions = new ConcurrentHashMap<>();

    @Override
    public DataProductInfo getDataProduct(String dataProductId) throws Exception {
        if (dataProductId == null || dataProductId.trim().isEmpty()) {
            throw new IllegalArgumentException("DataProduct ID cannot be null or empty");
        }

        DataProductInfo product = products.get(dataProductId);
        if (product == null) {
            throw new IllegalArgumentException("Data product with ID " + dataProductId + " not found");
        }

        return product;
    }
    
    /**
     * 创建数据产品
     * 
     * @param productInfo 产品信息
     * @return 产品ID
     * @throws Exception
     */
    public String createDataProduct(DataProductInfo productInfo) throws Exception {
        if (productInfo == null) {
            throw new IllegalArgumentException("Product info cannot be null");
        }
        
        if (productInfo.getProductId() == null || productInfo.getProductId().trim().isEmpty()) {
            productInfo.setProductId(UUID.randomUUID().toString());
        }
        
        if (products.containsKey(productInfo.getProductId())) {
            throw new IllegalArgumentException("Data product with ID " + productInfo.getProductId() + " already exists");
        }
        
        // 初始化版本管理
        ConcurrentMap<String, DataProductInfo> versions = new ConcurrentHashMap<>();
        String version = "1.0.0";
        versions.put(version, productInfo);
        productVersions.put(productInfo.getProductId(), versions);
        
        products.put(productInfo.getProductId(), productInfo);
        logger.info("Data product created: {}", productInfo.getProductId());
        return productInfo.getProductId();
    }
    
    /**
     * 更新数据产品
     * 
     * @param productInfo 产品信息
     * @throws Exception
     */
    public void updateDataProduct(DataProductInfo productInfo) throws Exception {
        if (productInfo == null || productInfo.getProductId() == null) {
            throw new IllegalArgumentException("Product info and product ID cannot be null");
        }
        
        if (!products.containsKey(productInfo.getProductId())) {
            throw new IllegalArgumentException("Data product with ID " + productInfo.getProductId() + " not found");
        }
        
        // 更新当前版本
        products.put(productInfo.getProductId(), productInfo);
        
        // 更新版本管理中的最新版本
        ConcurrentMap<String, DataProductInfo> versions = productVersions.get(productInfo.getProductId());
        if (versions != null) {
            // 获取当前最新版本号并递增
            String latestVersion = getLatestVersion(productInfo.getProductId());
            String newVersion = incrementVersion(latestVersion);
            versions.put(newVersion, productInfo);
        }
        
        logger.info("Data product updated: {}", productInfo.getProductId());
    }
    
    /**
     * 获取数据产品的指定版本
     * 
     * @param productId 产品ID
     * @param version 版本号
     * @return 产品信息
     * @throws Exception
     */
    public DataProductInfo getDataProductVersion(String productId, String version) throws Exception {
        if (productId == null || productId.trim().isEmpty()) {
            throw new IllegalArgumentException("Product ID cannot be null or empty");
        }
        
        ConcurrentMap<String, DataProductInfo> versions = productVersions.get(productId);
        if (versions == null) {
            throw new IllegalArgumentException("Data product with ID " + productId + " not found");
        }
        
        if (version == null || version.trim().isEmpty()) {
            // 返回最新版本
            return getDataProduct(productId);
        }
        
        DataProductInfo product = versions.get(version);
        if (product == null) {
            throw new IllegalArgumentException("Version " + version + " not found for product " + productId);
        }
        
        return product;
    }
    
    /**
     * 获取数据产品的所有版本
     * 
     * @param productId 产品ID
     * @return 版本列表
     * @throws Exception
     */
    public List<String> getDataProductVersions(String productId) throws Exception {
        if (productId == null || productId.trim().isEmpty()) {
            throw new IllegalArgumentException("Product ID cannot be null or empty");
        }
        
        ConcurrentMap<String, DataProductInfo> versions = productVersions.get(productId);
        if (versions == null) {
            throw new IllegalArgumentException("Data product with ID " + productId + " not found");
        }
        
        return new ArrayList<>(versions.keySet());
    }
    
    /**
     * 获取最新版本号
     */
    private String getLatestVersion(String productId) {
        ConcurrentMap<String, DataProductInfo> versions = productVersions.get(productId);
        if (versions == null || versions.isEmpty()) {
            return "1.0.0";
        }
        
        // 简单实现：返回最后一个版本
        // 实际应该按版本号排序
        return versions.keySet().stream()
            .max(String::compareTo)
            .orElse("1.0.0");
    }
    
    /**
     * 递增版本号
     */
    private String incrementVersion(String version) {
        if (version == null || version.isEmpty()) {
            return "1.0.0";
        }
        
        try {
            String[] parts = version.split("\\.");
            if (parts.length >= 3) {
                int patch = Integer.parseInt(parts[2]);
                return parts[0] + "." + parts[1] + "." + (patch + 1);
            } else if (parts.length == 2) {
                int minor = Integer.parseInt(parts[1]);
                return parts[0] + "." + (minor + 1) + ".0";
            } else {
                int major = Integer.parseInt(parts[0]);
                return (major + 1) + ".0.0";
            }
        } catch (NumberFormatException e) {
            logger.warn("Failed to parse version: {}, using default", version);
            return "1.0.0";
        }
    }
    
    /**
     * 删除数据产品
     * 
     * @param productId 产品ID
     * @throws Exception
     */
    public void deleteDataProduct(String productId) throws Exception {
        if (productId == null || productId.trim().isEmpty()) {
            throw new IllegalArgumentException("Product ID cannot be null or empty");
        }
        
        DataProductInfo removed = products.remove(productId);
        if (removed == null) {
            throw new IllegalArgumentException("Data product with ID " + productId + " not found");
        }
        
        // 删除版本信息
        productVersions.remove(productId);
        logger.info("Data product deleted: {}", productId);
    }
    
    /**
     * 查询数据产品列表
     * 
     * @param productName 产品名称（可选，用于模糊查询）
     * @param storageType 存储类型（可选）
     * @return 产品列表
     * @throws Exception
     */
    public List<DataProductInfo> queryDataProducts(String productName, String storageType) throws Exception {
        List<DataProductInfo> allProducts = new ArrayList<>(products.values());
        
        return allProducts.stream()
            .filter(p -> {
                if (productName != null && !productName.trim().isEmpty()) {
                    if (p.getProductName() == null || !p.getProductName().contains(productName)) {
                        return false;
                    }
                }
                if (storageType != null && !storageType.trim().isEmpty()) {
                    if (p.getStorageType() == null || !p.getStorageType().equals(storageType)) {
                        return false;
                    }
                }
                return true;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * 检查数据产品是否存在
     * 
     * @param productId 产品ID
     * @return 是否存在
     */
    public boolean existsDataProduct(String productId) {
        return productId != null && products.containsKey(productId);
    }
}

