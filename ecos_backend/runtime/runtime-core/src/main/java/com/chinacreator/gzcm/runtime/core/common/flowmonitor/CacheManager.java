package com.chinacreator.gzcm.runtime.core.common.flowmonitor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * CacheManager - 缓存管理器占位类
 * 用于兼容旧代码中的缓存管理功能
 * 
 * 注意：此实现为占位实现，实际应使用 Spring Cache 或 Redis 等缓存方案
 */
public class CacheManager {
    
    private static final CacheManager instance = new CacheManager();
    private final Map<String, Node<?, ?>> cache = new ConcurrentHashMap<>();
    
    private CacheManager() {
    }
    
    /**
     * 获取单例实例
     * @return CacheManager 实例
     */
    public static CacheManager get() {
        return instance;
    }
    
    /**
     * 获取或创建节点
     * @param path 节点路径
     * @return 节点对象
     */
    @SuppressWarnings("unchecked")
    public <K, V> Node<K, V> getNode(String path) {
        return (Node<K, V>) cache.computeIfAbsent(path, k -> new Node<>(path));
    }
    
    /**
     * 节点类（占位实现）
     */
    public static class Node<K, V> {
        private final String path;
        private final Map<K, V> data = new ConcurrentHashMap<>();
        
        public Node(String path) {
            this.path = path;
        }
        
        public String getPath() {
            return path;
        }
        
        public void put(K key, V value) {
            data.put(key, value);
        }
        
        public V get(K key) {
            return data.get(key);
        }
        
        public void remove(K key) {
            data.remove(key);
        }
        
        public void clear() {
            data.clear();
        }
    }
}
