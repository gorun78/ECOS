package com.chinacreator.gzcm.services.apigateway.graphql;

import com.chinacreator.gzcm.common.event.DomainEvent;
import com.chinacreator.gzcm.common.event.EventTypes;
import com.chinacreator.gzcm.services.apigateway.event.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Controller
public class EcosGraphQLController {
    private static final Logger log = LoggerFactory.getLogger(EcosGraphQLController.class);

    private final RestTemplate restTemplate;
    private final DomainEventPublisher eventPublisher;

    private static final String OBJECT_SERVICE = "http://localhost:18084";
    private static final String ONTOLOGY_SERVICE = "http://localhost:18083";
    private static final String AGENT_SERVICE = "http://localhost:18086";
    private static final String KNOWLEDGE_SERVICE = "http://localhost:18087";
    private static final String WORKFLOW_SERVICE = "http://localhost:18085";
    private static final String CATALOG_SERVICE = "http://localhost:18082";
    private static final String MISSION_SERVICE = "http://localhost:18081";

    public EcosGraphQLController(RestTemplate restTemplate, DomainEventPublisher eventPublisher) {
        this.restTemplate = restTemplate;
        this.eventPublisher = eventPublisher;
    }

    @QueryMapping
    public Map<String, Object> object(@Argument String id) {
        return restTemplate.getForObject(OBJECT_SERVICE + "/api/v1/objects/" + id, Map.class);
    }

    @QueryMapping
    public Map<String, Object> objects(@Argument String entityType, @Argument int page, @Argument int size) {
        String url = OBJECT_SERVICE + "/api/v1/objects?page=" + page + "&size=" + size;
        if (entityType != null) url += "&entityType=" + entityType;
        return restTemplate.getForObject(url, Map.class);
    }

    @QueryMapping
    public Map<String, Object> ontology(@Argument String id) {
        return restTemplate.getForObject(ONTOLOGY_SERVICE + "/api/v1/ecos/ontologies/" + id, Map.class);
    }

    @QueryMapping
    public Map<String, Object> ontologies(@Argument String domain, @Argument int page, @Argument int size) {
        String url = ONTOLOGY_SERVICE + "/api/v1/ecos/ontologies?page=" + page + "&size=" + size;
        if (domain != null) url += "&domain=" + domain;
        return restTemplate.getForObject(url, Map.class);
    }

    @QueryMapping
    public Map<String, Object> agent(@Argument String id) {
        return restTemplate.getForObject(AGENT_SERVICE + "/api/v1/agents/" + id, Map.class);
    }

    @QueryMapping
    public Map<String, Object> agents(@Argument int page, @Argument int size) {
        return restTemplate.getForObject(AGENT_SERVICE + "/api/v1/agents?page=" + page + "&size=" + size, Map.class);
    }

    @QueryMapping
    public Map<String, Object> graphNode(@Argument String id) {
        return restTemplate.getForObject(KNOWLEDGE_SERVICE + "/api/v1/graph/nodes/" + id, Map.class);
    }

    @QueryMapping
    public Map<String, Object> graphNeighbors(@Argument String id, @Argument int depth) {
        return restTemplate.getForObject(KNOWLEDGE_SERVICE + "/api/v1/graph/nodes/" + id + "/neighbors?depth=" + depth, Map.class);
    }

    @QueryMapping
    public Map<String, Object> goal(@Argument String id) {
        return restTemplate.getForObject(MISSION_SERVICE + "/api/v1/goals/" + id, Map.class);
    }

    @QueryMapping
    public Map<String, Object> dataset(@Argument String id) {
        return restTemplate.getForObject(CATALOG_SERVICE + "/api/v1/catalog/" + id, Map.class);
    }

    @QueryMapping
    public Map<String, Object> search(@Argument String query, @Argument String type, @Argument int page, @Argument int size) {
        String url = CATALOG_SERVICE + "/api/v1/search?q=" + query + "&page=" + page + "&size=" + size;
        if (type != null) url += "&type=" + type;
        return restTemplate.getForObject(url, Map.class);
    }

    @QueryMapping
    public Map<String, Object> workflow(@Argument String id) {
        return restTemplate.getForObject(WORKFLOW_SERVICE + "/api/v1/workflows/" + id, Map.class);
    }

    @MutationMapping
    public Map<String, Object> createObject(@Argument Map<String, Object> input) {
        Map<String, Object> result = restTemplate.postForObject(OBJECT_SERVICE + "/api/v1/objects", input, Map.class);
        if (result != null) {
            eventPublisher.publish(EventTypes.Object.OBJECT_CREATED, "api-gateway", "object",
                String.valueOf(result.get("id")), result);
        }
        return result;
    }

    @MutationMapping
    public Map<String, Object> executeAgent(@Argument String id, @Argument Map<String, Object> input) {
        return restTemplate.postForObject(AGENT_SERVICE + "/api/v1/agents/" + id + "/execute", input, Map.class);
    }

    @MutationMapping
    public Map<String, Object> startWorkflow(@Argument String id, @Argument Object input) {
        return restTemplate.postForObject(WORKFLOW_SERVICE + "/api/v1/workflows/" + id + "/start", input, Map.class);
    }
}
