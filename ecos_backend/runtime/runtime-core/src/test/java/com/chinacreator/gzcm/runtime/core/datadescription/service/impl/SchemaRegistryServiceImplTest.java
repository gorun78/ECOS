package com.chinacreator.gzcm.runtime.core.datadescription.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.chinacreator.gzcm.runtime.core.datadescription.entity.SchemaRegistry;
import com.chinacreator.gzcm.runtime.core.datadescription.model.DataSchema;
import com.chinacreator.gzcm.runtime.core.datadescription.model.DataSchema.SchemaType;
import com.chinacreator.gzcm.runtime.core.datadescription.model.impl.DataSchemaImpl;

/**
 * SchemaRegistryServiceImpl 单元测试
 */
@DisplayName("Schema注册服务测试")
class SchemaRegistryServiceImplTest {

    private SchemaRegistryServiceImpl schemaRegistryService;

    @BeforeEach
    void setUp() {
        schemaRegistryService = new SchemaRegistryServiceImpl();
    }

    @Test
    @DisplayName("注册Schema")
    void testRegisterSchema() throws Exception {
        DataSchema schema = createTestSchema();
        String subject = "test-subject-001";

        SchemaRegistry registered = schemaRegistryService.registerSchema(subject, schema);
        assertNotNull(registered);
        assertEquals(subject, registered.getSubject());
        assertEquals(Integer.valueOf(1), registered.getVersion());
    }

    @Test
    @DisplayName("注册Schema（不检查兼容性）")
    void testRegisterSchemaWithoutCompatibilityCheck() throws Exception {
        DataSchema schema = createTestSchema();
        String subject = "test-subject-002";

        SchemaRegistry registered = schemaRegistryService.registerSchema(subject, schema, false);
        assertNotNull(registered);
    }

    @Test
    @DisplayName("根据subject和version获取Schema")
    void testGetSchema() throws Exception {
        DataSchema schema = createTestSchema();
        String subject = "test-subject-003";
        schemaRegistryService.registerSchema(subject, schema);

        SchemaRegistry found = schemaRegistryService.getSchema(subject, 1);
        assertNotNull(found);
        assertEquals(subject, found.getSubject());
        assertEquals(Integer.valueOf(1), found.getVersion());
    }

    @Test
    @DisplayName("获取最新版本的Schema")
    void testGetLatestSchema() throws Exception {
        DataSchema schema1 = createTestSchema();
        String subject = "test-subject-004";
        schemaRegistryService.registerSchema(subject, schema1);
        
        DataSchema schema2 = createTestSchema();
        schemaRegistryService.registerSchema(subject, schema2);

        SchemaRegistry latest = schemaRegistryService.getLatestSchema(subject);
        assertNotNull(latest);
        assertEquals(Integer.valueOf(2), latest.getVersion());
    }

    @Test
    @DisplayName("获取Schema的所有版本")
    void testListVersions() throws Exception {
        DataSchema schema1 = createTestSchema();
        String subject = "test-subject-005";
        schemaRegistryService.registerSchema(subject, schema1);
        
        DataSchema schema2 = createTestSchema();
        schemaRegistryService.registerSchema(subject, schema2);

        List<Integer> versions = schemaRegistryService.listVersions(subject);
        assertNotNull(versions);
        assertEquals(2, versions.size());
        assertTrue(versions.contains(1));
        assertTrue(versions.contains(2));
    }

    @Test
    @DisplayName("列出指定subject的所有Schema")
    void testListSchemas() throws Exception {
        DataSchema schema1 = createTestSchema();
        String subject = "test-subject-006";
        schemaRegistryService.registerSchema(subject, schema1);
        
        DataSchema schema2 = createTestSchema();
        schemaRegistryService.registerSchema(subject, schema2);

        List<SchemaRegistry> schemas = schemaRegistryService.listSchemas(subject);
        assertNotNull(schemas);
        assertEquals(2, schemas.size());
    }

    @Test
    @DisplayName("删除指定版本的Schema")
    void testDeleteSchema() throws Exception {
        DataSchema schema = createTestSchema();
        String subject = "test-subject-007";
        schemaRegistryService.registerSchema(subject, schema);

        schemaRegistryService.deleteSchema(subject, 1);

        SchemaRegistry found = schemaRegistryService.getSchema(subject, 1);
        assertNull(found);
    }

    @Test
    @DisplayName("删除所有版本的Schema")
    void testDeleteAllSchemas() throws Exception {
        DataSchema schema1 = createTestSchema();
        String subject = "test-subject-008";
        schemaRegistryService.registerSchema(subject, schema1);
        
        DataSchema schema2 = createTestSchema();
        schemaRegistryService.registerSchema(subject, schema2);

        schemaRegistryService.deleteSchema(subject, null);

        List<SchemaRegistry> schemas = schemaRegistryService.listSchemas(subject);
        assertTrue(schemas == null || schemas.isEmpty());
    }

    private DataSchema createTestSchema() {
        DataSchemaImpl schema = new DataSchemaImpl();
        schema.setSchemaType(SchemaType.JSON_SCHEMA);
        schema.setSchemaContent("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}");
        return schema;
    }
}

