package com.chinacreator.gzcm.datanet.service.impl;

import com.chinacreator.gzcm.datanet.model.DataCategory;
import com.chinacreator.gzcm.datanet.repository.CategoryRepository;
import com.chinacreator.gzcm.datanet.service.CategoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 分类管理服务实现。
 */
@Service
public class CategoryServiceImpl implements CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryServiceImpl.class);

    private final CategoryRepository repository;

    public CategoryServiceImpl(CategoryRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public DataCategory create(DataCategory category) {
        if (category.getCategoryId() == null) {
            category.setCategoryId(UUID.randomUUID().toString().replace("-", ""));
        }

        // 确定 parentId（根分类为 "0"）
        String parentId = category.getParentId();
        if (parentId == null || parentId.isEmpty()) {
            parentId = "0";
        }
        category.setParentId(parentId);

        // 计算 level 和 path
        if ("0".equals(parentId)) {
            category.setLevel(1);
        } else {
            DataCategory parent = repository.findById(parentId);
            if (parent == null) {
                throw new IllegalArgumentException("父分类不存在: " + parentId);
            }
            category.setLevel(parent.getLevel() + 1);
        }

        // 自动排序号
        Integer maxSort = repository.maxSortOrder(parentId);
        category.setSortOrder(maxSort + 1);

        // 自动生成路径
        if ("0".equals(parentId)) {
            category.setPath("/" + category.getCategoryId());
        } else {
            DataCategory parent = repository.findById(parentId);
            category.setPath(parent.getPath() + "/" + category.getCategoryId());
        }

        category.setStatus("ACTIVE");
        category.setCreateTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());

        repository.insert(category);
        log.info("Created category: {} (path={})", category.getCategoryName(), category.getPath());
        return category;
    }

    @Override
    @Transactional
    public DataCategory update(DataCategory category) {
        DataCategory existing = repository.findById(category.getCategoryId());
        if (existing == null) {
            throw new IllegalArgumentException("分类不存在: " + category.getCategoryId());
        }

        category.setUpdateTime(LocalDateTime.now());
        repository.update(category);
        return repository.findById(category.getCategoryId());
    }

    @Override
    public DataCategory getById(String categoryId) {
        return repository.findById(categoryId);
    }

    @Override
    public List<DataCategory> getTree() {
        List<DataCategory> all = repository.findAll();
        if (all.isEmpty()) return Collections.emptyList();

        Map<String, List<DataCategory>> childrenMap = all.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(DataCategory::getParentId));

        // 为每个节点关联子节点
        for (DataCategory node : all) {
            node.setChildren(childrenMap.getOrDefault(node.getCategoryId(), Collections.emptyList()));
        }

        // 返回根节点
        return all.stream()
                .filter(c -> "0".equals(c.getParentId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<DataCategory> getChildren(String parentId) {
        return repository.findByParentId(parentId);
    }

    @Override
    @Transactional
    public void remove(String categoryId) {
        // 先删除子分类（递归）
        List<DataCategory> children = repository.findByParentId(categoryId);
        for (DataCategory child : children) {
            remove(child.getCategoryId());
        }
        repository.deleteById(categoryId);
        log.info("Deleted category: {}", categoryId);
    }

    @Override
    public List<DataCategory> getCategoryStats() {
        List<DataCategory> all = repository.findAll();
        for (DataCategory cat : all) {
            int count = repository.countResources(cat.getPath() + "/%");
            cat.setResourceCount(count);
        }
        return all;
    }
}
