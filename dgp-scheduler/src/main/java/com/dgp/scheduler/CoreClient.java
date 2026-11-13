package com.dgp.scheduler;

import com.dgp.common.GatewayHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 调用 dgp-core 内部调度 API。 */
@Component
public class CoreClient {

    private final String baseUrl;
    private final String token;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public CoreClient(
            @Value("${dgp.core-base-url:http://127.0.0.1:8082}") String baseUrl,
            @Value("${dgp.scheduler-token:dev-scheduler-token-change-me}") String token) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
    }

    public List<Map<String, Object>> dueJobs(int limit) {
        return list(get("/api/v1/internal/scheduler/due/jobs?limit=" + limit));
    }

    public List<Map<String, Object>> dueCollects(int limit) {
        return list(get("/api/v1/internal/scheduler/due/collects?limit=" + limit));
    }

    public List<Map<String, Object>> dueChecks(int limit) {
        return list(get("/api/v1/internal/scheduler/due/checks?limit=" + limit));
    }

    public List<Map<String, Object>> publishedJobs() {
        return list(get("/api/v1/internal/scheduler/jobs/published"));
    }

    public JsonNode runJob(long id) {
        return post("/api/v1/internal/scheduler/jobs/" + id + "/run");
    }

    public JsonNode runWorkflow(long id) {
        return post("/api/v1/internal/scheduler/workflows/" + id + "/run");
    }

    public JsonNode runCollect(long id) {
        return post("/api/v1/internal/scheduler/collects/" + id + "/run");
    }

    public JsonNode runCheck(long id) {
        return post("/api/v1/internal/scheduler/checks/" + id + "/run");
    }

    public JsonNode refreshAggregations() {
        return post("/api/v1/internal/scheduler/aggregations/refresh-due");
    }

    public JsonNode markOverdue() {
        return post("/api/v1/internal/scheduler/issues/mark-overdue");
    }

    public JsonNode resetDailyQuota() {
        return post("/api/v1/internal/scheduler/quota/reset-daily");
    }

    private JsonNode get(String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(120))
                    .header(GatewayHeaders.SCHEDULER_TOKEN, token)
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new IllegalStateException("core GET " + path + " -> " + resp.statusCode() + " " + resp.body());
            }
            return mapper.readTree(resp.body());
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private JsonNode post(String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofMinutes(10))
                    .header(GatewayHeaders.SCHEDULER_TOKEN, token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new IllegalStateException("core POST " + path + " -> " + resp.statusCode() + " " + resp.body());
            }
            return mapper.readTree(resp.body());
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(JsonNode root) {
        JsonNode data = root.path("data").path("records");
        if (!data.isArray()) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode n : data) {
            out.add(mapper.convertValue(n, Map.class));
        }
        return out;
    }
}
