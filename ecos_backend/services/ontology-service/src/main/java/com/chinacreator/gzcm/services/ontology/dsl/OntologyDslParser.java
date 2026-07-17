package com.chinacreator.gzcm.services.ontology.dsl;

import com.chinacreator.gzcm.services.ontology.model.EntityCategory;
import com.chinacreator.gzcm.services.ontology.model.EntityDefinition;
import com.chinacreator.gzcm.services.ontology.model.PropertyDefinition;
import com.chinacreator.gzcm.services.ontology.model.DataType;
import com.chinacreator.gzcm.services.ontology.model.RelationshipDefinition;
import com.chinacreator.gzcm.services.ontology.model.RelationshipType;
import com.chinacreator.gzcm.services.ontology.model.Cardinality;
import com.chinacreator.gzcm.services.ontology.model.MetricDefinition;
import com.chinacreator.gzcm.services.ontology.model.AggregationType;
import com.chinacreator.gzcm.services.ontology.model.ActionDefinition;
import com.chinacreator.gzcm.services.ontology.model.ActionType;
import com.chinacreator.gzcm.services.ontology.model.PolicyDefinition;
import com.chinacreator.gzcm.services.ontology.model.EventDefinition;
import com.chinacreator.gzcm.services.ontology.model.LifecycleDefinition;
import com.chinacreator.gzcm.services.ontology.model.TransitionDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OntologyDslParser {
    private static final Logger log = LoggerFactory.getLogger(OntologyDslParser.class);

    public List<EntityDefinition> parseEntities(List<Map<String, Object>> entityMaps) {
        List<EntityDefinition> entities = new ArrayList<>();
        for (Map<String, Object> em : entityMaps) {
            EntityDefinition entity = new EntityDefinition();
            entity.setId(UUID.randomUUID().toString());
            entity.setCode((String) em.getOrDefault("code", "Unknown"));
            entity.setName((String) em.getOrDefault("name", entity.getCode()));
            entity.setDescription((String) em.getOrDefault("description", ""));
            String cat = (String) em.getOrDefault("category", "MASTER");
            try {
                entity.setCategory(EntityCategory.valueOf(cat));
            } catch (IllegalArgumentException e) {
                entity.setCategory(EntityCategory.MASTER);
            }
            List<Map<String, Object>> props = (List<Map<String, Object>>) em.get("properties");
            if (props != null) {
                for (Map<String, Object> pm : props) {
                    PropertyDefinition prop = parseProperty(pm);
                    entity.getProperties().add(prop);
                }
            }
            entities.add(entity);
        }
        return entities;
    }

    private PropertyDefinition parseProperty(Map<String, Object> pm) {
        PropertyDefinition prop = new PropertyDefinition();
        prop.setId(UUID.randomUUID().toString());
        prop.setCode((String) pm.getOrDefault("code", "unknown"));
        prop.setName((String) pm.getOrDefault("name", prop.getCode()));
        String typeStr = (String) pm.getOrDefault("type", "STRING");
        try {
            prop.setType(DataType.valueOf(typeStr));
        } catch (IllegalArgumentException e) {
            prop.setType(DataType.STRING);
        }
        prop.setRequired(Boolean.parseBoolean(String.valueOf(pm.getOrDefault("required", false))));
        prop.setIndexed(Boolean.parseBoolean(String.valueOf(pm.getOrDefault("indexed", false))));
        return prop;
    }

    public List<RelationshipDefinition> parseRelationships(List<Map<String, Object>> relMaps) {
        List<RelationshipDefinition> rels = new ArrayList<>();
        for (Map<String, Object> rm : relMaps) {
            RelationshipDefinition rel = new RelationshipDefinition();
            rel.setId(UUID.randomUUID().toString());
            rel.setSourceEntity((String) rm.get("source"));
            rel.setTargetEntity((String) rm.get("target"));
            String typeStr = (String) rm.getOrDefault("type", "RELATED_TO");
            try {
                rel.setType(RelationshipType.valueOf(typeStr));
            } catch (IllegalArgumentException e) {
                rel.setType(RelationshipType.RELATED_TO);
            }
            rels.add(rel);
        }
        return rels;
    }

    public List<MetricDefinition> parseMetrics(List<Map<String, Object>> metricMaps) {
        List<MetricDefinition> metrics = new ArrayList<>();
        for (Map<String, Object> mm : metricMaps) {
            MetricDefinition metric = new MetricDefinition();
            metric.setId(UUID.randomUUID().toString());
            metric.setCode((String) mm.getOrDefault("code", "unknown"));
            metric.setName((String) mm.getOrDefault("name", metric.getCode()));
            metric.setExpression((String) mm.getOrDefault("expression", ""));
            String aggStr = (String) mm.getOrDefault("aggregation", "SUM");
            try {
                metric.setAggregation(AggregationType.valueOf(aggStr));
            } catch (IllegalArgumentException e) {
                metric.setAggregation(AggregationType.SUM);
            }
            metrics.add(metric);
        }
        return metrics;
    }
}
