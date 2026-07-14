package com.chinacreator.gzcm.runtime.core.datadescription.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.runtime.core.datadescription.enums.DataType;
import com.chinacreator.gzcm.runtime.core.datadescription.model.DataDescription;
import com.chinacreator.gzcm.runtime.core.datadescription.model.DataMetadata;
import com.chinacreator.gzcm.runtime.core.datadescription.model.DataSchema;
import com.chinacreator.gzcm.runtime.core.datadescription.service.IDataDescriptionService;

/**
 * 数据描述服务实现
 * 提供数据描述的CRUD操作和查询功能
 * 
 * @author CDRC Runtime Team
 */
public class DataDescriptionServiceImpl implements IDataDescriptionService {

    private final Map<String, DataDescription> descriptions = new HashMap<>();

    @Override
    public DataDescription createDataDescription(DataDescription description) throws Exception {
        if (description == null) {
            throw new IllegalArgumentException("DataDescription cannot be null");
        }

        // 生成ID
        String id = UUID.randomUUID().toString();
        if (description instanceof DataDescriptionImpl) {
            ((DataDescriptionImpl) description).setId(id);
        }

        // 验证
        if (!description.validate()) {
            throw new DataDescription.ValidationException("DataDescription validation failed");
        }

        descriptions.put(id, description);
        return description;
    }

    @Override
    public DataDescription updateDataDescription(String id, DataDescription description) throws Exception {
        if (id == null || description == null) {
            throw new IllegalArgumentException("ID and DataDescription cannot be null");
        }

        if (!descriptions.containsKey(id)) {
            throw new IllegalArgumentException("DataDescription with ID '" + id + "' not found");
        }

        // 验证
        if (!description.validate()) {
            throw new DataDescription.ValidationException("DataDescription validation failed");
        }

        if (description instanceof DataDescriptionImpl) {
            ((DataDescriptionImpl) description).setId(id);
        }

        descriptions.put(id, description);
        return description;
    }

    @Override
    public DataDescription getDataDescription(String id) throws Exception {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }

        return descriptions.get(id);
    }

    @Override
    public void deleteDataDescription(String id) throws Exception {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }

        descriptions.remove(id);
    }

    @Override
    public List<DataDescription> listDataDescriptions(QueryCondition condition) throws Exception {
        List<DataDescription> result = new ArrayList<>(descriptions.values());

        if (condition != null) {
            // 按数据类型过滤
            if (condition.getDataType() != null) {
                result = result.stream()
                    .filter(d -> d.getDataType() != null && 
                        d.getDataType().name().equalsIgnoreCase(condition.getDataType()))
                    .collect(Collectors.toList());
            }

            // 按名称过滤
            if (condition.getName() != null) {
                String namePattern = condition.getName().toLowerCase();
                result = result.stream()
                    .filter(d -> {
                        DataMetadata metadata = d.getMetadata();
                        if (metadata != null && metadata.getName() != null) {
                            return metadata.getName().toLowerCase().contains(namePattern);
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
            }

            // 按格式过滤
            if (condition.getFormat() != null) {
                result = result.stream()
                    .filter(d -> {
                        DataMetadata metadata = d.getMetadata();
                        if (metadata != null && condition.getFormat().equalsIgnoreCase(metadata.getFormat())) {
                            return true;
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
            }

            // 排序
            if (condition.getSortField() != null) {
                String sortField = condition.getSortField();
                boolean ascending = "asc".equalsIgnoreCase(condition.getSortOrder());
                
                result.sort((a, b) -> {
                    DataMetadata metaA = a.getMetadata();
                    DataMetadata metaB = b.getMetadata();
                    
                    if (metaA == null || metaB == null) {
                        return 0;
                    }

                    Object valA = getFieldValue(metaA, sortField);
                    Object valB = getFieldValue(metaB, sortField);
                    
                    if (valA == null && valB == null) {
                        return 0;
                    }
                    if (valA == null) {
                        return ascending ? -1 : 1;
                    }
                    if (valB == null) {
                        return ascending ? 1 : -1;
                    }

                    int compare = compareValues(valA, valB);
                    return ascending ? compare : -compare;
                });
            }

            // 分页
            if (condition.getPage() != null && condition.getPageSize() != null) {
                int page = condition.getPage();
                int pageSize = condition.getPageSize();
                int offset = (page - 1) * pageSize;
                
                if (offset >= 0 && offset < result.size()) {
                    int end = Math.min(offset + pageSize, result.size());
                    result = result.subList(offset, end);
                } else {
                    result = new ArrayList<>();
                }
            } else if (condition.getOffset() != null && condition.getPageSize() != null) {
                int offset = condition.getOffset();
                int pageSize = condition.getPageSize();
                
                if (offset >= 0 && offset < result.size()) {
                    int end = Math.min(offset + pageSize, result.size());
                    result = result.subList(offset, end);
                } else {
                    result = new ArrayList<>();
                }
            }
        }

        return result;
    }

    @Override
    public List<DataDescription> listDataDescriptionsByType(String dataType) throws Exception {
        if (dataType == null) {
            return new ArrayList<>(descriptions.values());
        }

        return descriptions.values().stream()
            .filter(d -> d.getDataType() != null && 
                d.getDataType().name().equalsIgnoreCase(dataType))
            .collect(Collectors.toList());
    }

    private Object getFieldValue(DataMetadata metadata, String field) {
        switch (field.toLowerCase()) {
            case "name":
                return metadata.getName();
            case "type":
                return metadata.getType();
            case "format":
                return metadata.getFormat();
            case "size":
                return metadata.getSize();
            case "encoding":
                return metadata.getEncoding();
            default:
                return metadata.getExtension(field);
        }
    }

    @SuppressWarnings("unchecked")
    private int compareValues(Object a, Object b) {
        if (a instanceof Comparable && b instanceof Comparable) {
            try {
                return ((Comparable<Object>) a).compareTo(b);
            } catch (ClassCastException e) {
                // 类型不兼容，转换为字符串比较
                return a.toString().compareTo(b.toString());
            }
        }
        return a.toString().compareTo(b.toString());
    }

    /**
     * 数据描述实现类（用于存储ID）
     */
    private static class DataDescriptionImpl implements DataDescription {
        private String id;
        private DataType dataType;
        private DataSchema schema;
        private DataMetadata metadata;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @Override
        public DataType getDataType() {
            return dataType;
        }

        public void setDataType(DataType dataType) {
            this.dataType = dataType;
        }

        @Override
        public DataSchema getSchema() {
            return schema;
        }

        @Override
        public void setSchema(DataSchema schema) {
            this.schema = schema;
        }

        @Override
        public DataMetadata getMetadata() {
            return metadata;
        }

        @Override
        public void setMetadata(DataMetadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public boolean validate() throws ValidationException {
            if (dataType == null) {
                throw new ValidationException("DataType is required");
            }
            if (metadata == null) {
                throw new ValidationException("DataMetadata is required");
            }
            if (metadata.getName() == null || metadata.getName().trim().isEmpty()) {
                throw new ValidationException("DataMetadata name is required");
            }
            return true;
        }

        @Override
        public boolean validateData(Object data) throws ValidationException {
            if (schema != null) {
                try {
                    return schema.validate(data);
                } catch (DataSchema.ValidationException e) {
                    throw new ValidationException("Schema validation failed: " + e.getMessage(), e);
                }
            }
            return true;
        }
    }
}

