package com.erick.soporte.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class ZohoCrmTaskMetricsService {

    private static final int PAGE_SIZE = 200;
    private static final ZoneId APP_ZONE = ZoneId.of("America/Guatemala");
    private static final DateTimeFormatter CRM_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final long CACHE_TTL_MILLIS = 15 * 60 * 1000;

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, TaskCacheEntry> taskCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Value("${zoho.crm.base-url:}")
    private String crmBaseUrl;

    @Value("${zoho.crm.accounts-url:}")
    private String accountsUrl;

    @Value("${zoho.crm.client-id:}")
    private String clientId;

    @Value("${zoho.crm.client-secret:}")
    private String clientSecret;

    @Value("${zoho.crm.refresh-token:}")
    private String refreshToken;

    @Value("${zoho.crm.tasks-module:Tasks}")
    private String tasksModule;

    @Value("${zoho.crm.completed-statuses:Completed,Completada,Cerrada,Finalizada}")
    private String completedStatuses;

    private String cachedAccessToken;
    private long tokenExpiresAt;

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("configured", isConfigured());
        status.put("baseUrl", cleanBaseUrl(crmBaseUrl));
        status.put("accountsUrl", cleanBaseUrl(accountsUrl));
        status.put("tasksModule", hasText(tasksModule) ? tasksModule.trim() : "Tasks");
        status.put("completedStatuses", completedStatusSet());
        return status;
    }

    public TaskMetrics metrics(LocalDate from, LocalDate to) {
        return metrics(from, to, new TaskFilter(null, null, null, null, PAGE_SIZE, true));
    }

    public TaskMetrics metrics(LocalDate from, LocalDate to, TaskFilter filter) {
        return buildMetrics(from, to, filter, true);
    }

    public TaskMetrics cachedMetrics(LocalDate from, LocalDate to, TaskFilter filter) {
        return buildMetrics(from, to, filter, false);
    }

    public boolean hasCachedTasks(LocalDate from, LocalDate to) {
        TaskCacheEntry entry = taskCache.get(cacheKey(from, to));
        return entry != null && !entry.isExpired();
    }

    private TaskMetrics buildMetrics(LocalDate from, LocalDate to, TaskFilter filter, boolean allowFetch) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Selecciona fecha desde y fecha hasta.");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("La fecha hasta no puede ser menor que la fecha desde.");
        }

        TaskFilter effectiveFilter = filter != null ? filter : new TaskFilter(null, null, null, null, PAGE_SIZE, allowFetch);
        List<CrmTask> rawTasks = fetchTasks(from, to, allowFetch || effectiveFilter.refreshCrm());
        List<CrmTask> tasks = rawTasks.stream()
                .filter(task -> matches(task.taskType(), effectiveFilter.taskType()))
                .filter(task -> matches(task.operationCountry(), effectiveFilter.operationCountry()))
                .filter(task -> matches(task.ownerName(), effectiveFilter.owner()))
                .filter(task -> matches(task.status(), effectiveFilter.status()))
                .toList();
        Set<String> completed = completedStatusSet().stream()
                .map(this::normalize)
                .collect(Collectors.toSet());

        Map<String, AgentTaskMetric> grouped = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (CrmTask task : tasks) {
            String owner = hasText(task.ownerName()) ? task.ownerName() : "Sin propietario";
            AgentTaskMetric metric = grouped.computeIfAbsent(owner, AgentTaskMetric::new);
            metric.assigned++;
            if (completed.contains(normalize(task.status()))) {
                metric.completed++;
            }
            metric.statuses.merge(hasText(task.status()) ? task.status() : "Sin estado", 1L, Long::sum);
        }

        List<AgentTaskMetric> agents = grouped.values()
                .stream()
                .peek(AgentTaskMetric::calculate)
                .sorted(Comparator.comparing(AgentTaskMetric::completed).reversed()
                        .thenComparing(AgentTaskMetric::assigned).reversed()
                        .thenComparing(AgentTaskMetric::owner))
                .toList();

        int assigned = agents.stream().mapToInt(AgentTaskMetric::assigned).sum();
        int completedCount = agents.stream().mapToInt(AgentTaskMetric::completed).sum();
        int pending = Math.max(0, assigned - completedCount);
        double compliance = assigned == 0 ? 0 : (completedCount * 100.0) / assigned;

        return new TaskMetrics(
                from,
                to,
                assigned,
                completedCount,
                pending,
                compliance,
                agents,
                tasks,
                groupBy(tasks, CrmTask::taskType, "Sin tipo"),
                groupBy(tasks, CrmTask::operationCountry, "Sin pais"),
                groupBy(tasks, CrmTask::status, "Sin estado"),
                options(rawTasks, CrmTask::taskType),
                options(rawTasks, CrmTask::operationCountry),
                options(rawTasks, CrmTask::ownerName),
                options(rawTasks, CrmTask::status)
        );
    }

    private List<CrmTask> fetchTasks(LocalDate from, LocalDate to, boolean forceRefresh) {
        ensureConfigured();

        String cacheKey = cacheKey(from, to);
        TaskCacheEntry cached = taskCache.get(cacheKey);
        if (!forceRefresh && cached != null && !cached.isExpired()) {
            return cached.tasks();
        }
        if (!forceRefresh) {
            throw new IllegalStateException("Sin datos CRM cacheados para este rango. Presiona Actualizar CRM para consultar Zoho.");
        }

        List<CrmTask> tasks = new ArrayList<>();
        int offset = 0;

        while (true) {
            Map<String, Object> response = executeCoql(taskQuery(from, to, offset));
            Object data = response.get("data");
            if (!(data instanceof List<?> list) || list.isEmpty()) {
                break;
            }

            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    tasks.add(mapTask(map));
                }
            }

            Object info = response.get("info");
            boolean moreRecords = info instanceof Map<?, ?> infoMap
                    && Boolean.TRUE.equals(infoMap.get("more_records"));
            if (!moreRecords || list.size() < PAGE_SIZE) {
                break;
            }
            offset += PAGE_SIZE;
        }

        taskCache.put(cacheKey, new TaskCacheEntry(tasks, System.currentTimeMillis()));
        return tasks;
    }

    private String cacheKey(LocalDate from, LocalDate to) {
        return from + "|" + to;
    }

    private String taskQuery(LocalDate from, LocalDate to, int offset) {
        String start = from.atStartOfDay(APP_ZONE).format(CRM_DATE_TIME);
        String end = to.atTime(23, 59, 59).atZone(APP_ZONE).format(CRM_DATE_TIME);
        String module = hasText(tasksModule) ? tasksModule.trim() : "Tasks";

        return "select id, Subject, Status, Owner, Tipo_Tarea, Pa_s_Operaci_n, Due_Date, Created_Time, Modified_Time from "
                + module
                + " where ((Created_Time >= '" + start + "') and (Created_Time <= '" + end + "'))"
                + " order by Created_Time desc limit " + offset + ", " + PAGE_SIZE;
    }

    private Map<String, Object> executeCoql(String query) {
        String url = UriComponentsBuilder
                .fromUriString(cleanBaseUrl(crmBaseUrl))
                .path("/crm/v8/coql")
                .toUriString();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("select_query", query);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, jsonHeaders()),
                    Map.class
            );

            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (HttpStatusCodeException error) {
            throw new IllegalStateException("Zoho CRM rechazo la consulta de tareas: " + error.getResponseBodyAsString(), error);
        }
    }

    private CrmTask mapTask(Map<?, ?> map) {
        Map<?, ?> owner = map.get("Owner") instanceof Map<?, ?> ownerMap ? ownerMap : Map.of();
        return new CrmTask(
                stringValue(map.get("id")),
                stringValue(map.get("Subject")),
                stringValue(map.get("Status")),
                firstText(stringValue(owner.get("name")), stringValue(owner.get("email")), stringValue(map.get("Owner"))),
                stringValue(owner.get("id")),
                stringValue(map.get("Tipo_Tarea")),
                stringValue(map.get("Pa_s_Operaci_n")),
                parseDate(stringValue(map.get("Due_Date"))),
                parseDate(stringValue(map.get("Created_Time"))),
                parseDate(stringValue(map.get("Modified_Time")))
        );
    }

    private boolean matches(String actual, String expected) {
        return !hasText(expected) || normalize(actual).equals(normalize(expected));
    }

    private Map<String, Long> groupBy(List<CrmTask> tasks, java.util.function.Function<CrmTask, String> mapper, String fallback) {
        return tasks.stream()
                .collect(Collectors.groupingBy(
                        task -> hasText(mapper.apply(task)) ? mapper.apply(task).trim() : fallback,
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private List<String> options(List<CrmTask> tasks, java.util.function.Function<CrmTask, String> mapper) {
        return tasks.stream()
                .map(mapper)
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String accessToken() {
        long now = System.currentTimeMillis();
        if (hasText(cachedAccessToken) && now < tokenExpiresAt) {
            return cachedAccessToken;
        }

        String url = UriComponentsBuilder
                .fromUriString(cleanBaseUrl(accountsUrl))
                .path("/oauth/v2/token")
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", refreshToken);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "refresh_token");

        Map response;
        try {
            response = restTemplate.postForObject(url, new HttpEntity<>(form, headers), Map.class);
        } catch (HttpStatusCodeException error) {
            throw new IllegalStateException("Zoho CRM rechazo la solicitud de token: " + error.getResponseBodyAsString(), error);
        }

        if (response == null || !response.containsKey("access_token")) {
            throw new IllegalStateException("Zoho CRM no devolvio access_token. Respuesta: " + response);
        }

        cachedAccessToken = String.valueOf(response.get("access_token"));
        long expiresIn = longValue(response.get("expires_in"), 3600);
        tokenExpiresAt = now + Math.max(60, expiresIn - 60) * 1000;
        return cachedAccessToken;
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Configura variables ZOHO_CRM antes de consultar tareas.");
        }
    }

    private boolean isConfigured() {
        return hasText(crmBaseUrl)
                && hasText(accountsUrl)
                && hasText(clientId)
                && hasText(clientSecret)
                && hasText(refreshToken);
    }

    private Set<String> completedStatusSet() {
        return Arrays.stream(safe(completedStatuses).split(","))
                .map(String::trim)
                .filter(this::hasText)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private OffsetDateTime parseDate(String value) {
        if (!hasText(value)) {
            return null;
        }

        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String normalize(String value) {
        return safe(value)
                .toLowerCase(Locale.ROOT)
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u");
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String cleanBaseUrl(String value) {
        String cleaned = safe(value);
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public record TaskMetrics(
            LocalDate from,
            LocalDate to,
            int assigned,
            int completed,
            int pending,
            double compliance,
            List<AgentTaskMetric> agents,
            List<CrmTask> tasks,
            Map<String, Long> byType,
            Map<String, Long> byCountry,
            Map<String, Long> byStatus,
            List<String> taskTypeOptions,
            List<String> countryOptions,
            List<String> ownerOptions,
            List<String> statusOptions
    ) {
        public LocalDate getFrom() {
            return from;
        }

        public LocalDate getTo() {
            return to;
        }

        public int getAssigned() {
            return assigned;
        }

        public int getCompleted() {
            return completed;
        }

        public int getPending() {
            return pending;
        }

        public double getCompliance() {
            return compliance;
        }

        public List<AgentTaskMetric> getAgents() {
            return agents;
        }

        public List<CrmTask> getTasks() {
            return tasks;
        }

        public Map<String, Long> getByType() {
            return byType;
        }

        public Map<String, Long> getByCountry() {
            return byCountry;
        }

        public Map<String, Long> getByStatus() {
            return byStatus;
        }

        public List<String> getTaskTypeOptions() {
            return taskTypeOptions;
        }

        public List<String> getCountryOptions() {
            return countryOptions;
        }

        public List<String> getOwnerOptions() {
            return ownerOptions;
        }

        public List<String> getStatusOptions() {
            return statusOptions;
        }
    }

    public static class AgentTaskMetric {
        private final String owner;
        private final Map<String, Long> statuses = new LinkedHashMap<>();
        private int assigned;
        private int completed;
        private int pending;
        private double compliance;

        public AgentTaskMetric(String owner) {
            this.owner = owner;
        }

        private void calculate() {
            pending = Math.max(0, assigned - completed);
            compliance = assigned == 0 ? 0 : (completed * 100.0) / assigned;
        }

        public String owner() {
            return owner;
        }

        public String getOwner() {
            return owner;
        }

        public int assigned() {
            return assigned;
        }

        public int getAssigned() {
            return assigned;
        }

        public int completed() {
            return completed;
        }

        public int getCompleted() {
            return completed;
        }

        public int pending() {
            return pending;
        }

        public int getPending() {
            return pending;
        }

        public double compliance() {
            return compliance;
        }

        public double getCompliance() {
            return compliance;
        }

        public Map<String, Long> statuses() {
            return statuses;
        }

        public Map<String, Long> getStatuses() {
            return statuses;
        }
    }

    public record CrmTask(
            String id,
            String subject,
            String status,
            String ownerName,
            String ownerId,
            String taskType,
            String operationCountry,
            OffsetDateTime dueDate,
            OffsetDateTime createdTime,
            OffsetDateTime modifiedTime
    ) {
    }

    public record TaskFilter(
            String taskType,
            String operationCountry,
            String owner,
            String status,
            int size,
            boolean refreshCrm
    ) {
    }

    private record TaskCacheEntry(List<CrmTask> tasks, long storedAt) {
        private boolean isExpired() {
            return System.currentTimeMillis() - storedAt > CACHE_TTL_MILLIS;
        }
    }
}
