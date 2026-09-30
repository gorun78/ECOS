package com.chinacreator.gzcm.services.ontology.compiler;

import com.chinacreator.gzcm.services.ontology.model.EntityDefinition;
import com.chinacreator.gzcm.services.ontology.model.RelationshipDefinition;
import com.chinacreator.gzcm.services.ontology.model.MetricDefinition;
import com.chinacreator.gzcm.services.ontology.dsl.OntologyDslParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OntologyCompiler {
    private static final Logger log = LoggerFactory.getLogger(OntologyCompiler.class);

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OntologyDslParser parser;

    public CompilationResult compile(CompilationRequest request) {
        log.info("Compiling ontology: {}", request.getName());
        CompilationResult result = new CompilationResult();
        result.setName(request.getName());

        List<EntityDefinition> entities = parser.parseEntities(request.getEntities());
        result.setEntityCount(entities.size());

        List<RelationshipDefinition> rels = parser.parseRelationships(request.getRelationships());
        result.setRelationshipCount(rels.size());

        List<MetricDefinition> metrics = parser.parseMetrics(request.getMetrics());
        result.setMetricCount(metrics.size());

        if (jdbcTemplate != null) {
            try {
                for (EntityDefinition entity : entities) {
                    compileEntityToDb(entity);
                }
                result.setDbGenerated(true);
            } catch (Exception e) {
                log.warn("DB compilation skipped: {}", e.getMessage());
                result.setDbGenerated(false);
            }
        }

        result.setSuccess(true);
        return result;
    }

    private void compileEntityToDb(EntityDefinition entity) {
        String sql = "INSERT INTO ecos_ontology.entity_definition (id, code, name, description, category) VALUES (?, ?, ?, ?, ?) ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name";
        jdbcTemplate.update(sql, entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(), entity.getCategory().name());
        log.info("Compiled entity: {}", entity.getCode());
    }
}
