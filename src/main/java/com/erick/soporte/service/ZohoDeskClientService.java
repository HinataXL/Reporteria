package com.erick.soporte.service;

import com.erick.soporte.entity.Conversation;
import com.erick.soporte.entity.SupportClient;
import com.erick.soporte.repository.SupportClientRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
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

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ZohoDeskClientService {

    private static final int PAGE_SIZE = 100;

    private final RestTemplate restTemplate = new RestTemplate();
    private final SupportClientRepository supportClientRepository;

    @Value("${zoho.desk.base-url:}")
    private String deskBaseUrl;

    @Value("${zoho.accounts.url:}")
    private String accountsUrl;

    @Value("${zoho.desk.org-id:}")
    private String orgId;

    @Value("${zoho.client-id:}")
    private String clientId;

    @Value("${zoho.client-secret:}")
    private String clientSecret;

    @Value("${zoho.refresh-token:}")
    private String refreshToken;

    @Value("${zoho.default-department-id:}")
    private String defaultDepartmentId;

    private String cachedAccessToken;
    private long tokenExpiresAt;

    public ZohoDeskClientService(SupportClientRepository supportClientRepository) {
        this.supportClientRepository = supportClientRepository;
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("configured", isConfigured());
        status.put("baseUrl", cleanBaseUrl(deskBaseUrl));
        status.put("accountsUrl", cleanBaseUrl(accountsUrl));
        status.put("orgIdConfigured", hasText(orgId));
        status.put("defaultDepartmentConfigured", hasText(defaultDepartmentId));
        status.put("localClients", supportClientRepository.count());
        return status;
    }

    public Map<String, Object> createTicket(Conversation conversation) {
        ensureConfigured();

        if (!hasText(defaultDepartmentId)) {
            throw new IllegalStateException("Configura ZOHO_DEFAULT_DEPARTMENT_ID para crear tickets.");
        }

        if (hasText(conversation.getZohoTicketId())) {
            throw new IllegalStateException("Esta conversacion ya tiene ticket Zoho: " + conversation.getNumeroTicket());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("departmentId", defaultDepartmentId.trim());
        body.put("subject", ticketSubject(conversation));
        body.put("description", ticketDescription(conversation));
        body.put("priority", zohoPriority(conversation.getPriorityId()));
        body.put("channel", zohoChannel(conversation.getChannelId()));

        SupportClient client = findLocalClient(conversation);
        if (hasText(conversation.getZohoContactId())) {
            body.put("contactId", conversation.getZohoContactId().trim());
        } else if (client != null && hasText(client.getZohoContactId())) {
            body.put("contactId", client.getZohoContactId());
        } else {
            body.put("contact", contactPayload(conversation));
        }

        String url = UriComponentsBuilder
                .fromUriString(cleanBaseUrl(deskBaseUrl))
                .path("/api/v1/tickets")
                .toUriString();

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, jsonZohoHeaders()),
                    Map.class
            );

            Map<String, Object> result = normalizeTicketResponse(response.getBody());
            if (hasText(conversation.getZohoContactId())) {
                result.put("zohoContactId", conversation.getZohoContactId().trim());
            } else if (client != null) {
                result.put("zohoContactId", client.getZohoContactId());
            }
            return result;
        } catch (HttpStatusCodeException error) {
            throw new IllegalStateException("Zoho rechazo la creacion del ticket: " + error.getResponseBodyAsString(), error);
        }
    }

    public Map<String, Object> syncContacts(int maxPages) {
        ensureConfigured();

        int pages = Math.max(1, Math.min(maxPages, 25));
        int fetched = 0;
        int created = 0;
        int updated = 0;

        for (int page = 0; page < pages; page++) {
            List<Map<String, Object>> contacts = fetchContacts(page * PAGE_SIZE);

            if (contacts.isEmpty()) {
                break;
            }

            for (Map<String, Object> item : contacts) {
                String zohoContactId = stringValue(item.get("id"));
                if (!hasText(zohoContactId)) {
                    continue;
                }

                SupportClient client = supportClientRepository.findByZohoContactId(zohoContactId)
                        .orElseGet(SupportClient::new);
                boolean isNew = client.getId() == null;

                mapContact(item, client);
                supportClientRepository.save(client);

                if (isNew) {
                    created++;
                } else {
                    updated++;
                }
            }

            fetched += contacts.size();

            if (contacts.size() < PAGE_SIZE) {
                break;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fetched", fetched);
        result.put("created", created);
        result.put("updated", updated);
        result.put("localClients", supportClientRepository.count());
        result.put("syncedAt", LocalDateTime.now());
        return result;
    }

    private List<Map<String, Object>> fetchContacts(int from) {
        String url = UriComponentsBuilder
                .fromUriString(cleanBaseUrl(deskBaseUrl))
                .path("/api/v1/contacts")
                .queryParam("from", from)
                .queryParam("limit", PAGE_SIZE)
                .queryParam("sortBy", "modifiedTime")
                .queryParam("include", "accounts")
                .toUriString();

        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(zohoHeaders()),
                Map.class
        );

        Object data = response.getBody() != null ? response.getBody().get("data") : null;
        if (data instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }

        return List.of();
    }

    private void mapContact(Map<String, Object> item, SupportClient client) {
        String firstName = firstNonBlank(item, "firstName", "first_name");
        String lastName = firstNonBlank(item, "lastName", "last_name");
        String fullName = firstNonBlank(item, "fullName", "name", "contactName");
        String accountName = firstNonBlank(item, "accountName", "account");
        String accountId = firstNonBlank(item, "accountId");

        Object account = item.get("account");
        if (account instanceof Map<?, ?> accountMap) {
            accountName = firstNonBlank(accountMap, "accountName", "name");
            accountId = firstNonBlank(accountMap, "id");
        }

        if (!hasText(fullName)) {
            fullName = (safe(firstName) + " " + safe(lastName)).trim();
        }

        if (!hasText(fullName)) {
            fullName = firstNonBlank(item, "email", "phone", "mobile");
        }

        client.setZohoContactId(stringValue(item.get("id")));
        client.setZohoAccountId(accountId);
        client.setFirstName(firstName);
        client.setLastName(lastName);
        client.setFullName(fullName);
        client.setAccountName(accountName);
        client.setEmail(firstNonBlank(item, "email", "emailId"));
        client.setPhone(firstNonBlank(item, "phone"));
        client.setMobile(firstNonBlank(item, "mobile"));
        client.setActive(true);
        client.setZohoCreatedTime(parseDate(firstNonBlank(item, "createdTime", "created_time")));
        client.setZohoModifiedTime(parseDate(firstNonBlank(item, "modifiedTime", "modified_time")));
        client.setLastSyncedAt(LocalDateTime.now());
    }

    private HttpHeaders zohoHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Zoho-oauthtoken " + accessToken());
        headers.set("orgId", orgId.trim());
        return headers;
    }

    private HttpHeaders jsonZohoHeaders() {
        HttpHeaders headers = zohoHeaders();
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
            throw new IllegalStateException("Zoho rechazo la solicitud de token: " + error.getResponseBodyAsString(), error);
        }

        if (response == null || !response.containsKey("access_token")) {
            throw new IllegalStateException("Zoho no devolvio access_token. Respuesta: " + response);
        }

        cachedAccessToken = String.valueOf(response.get("access_token"));
        long expiresIn = longValue(response.get("expires_in"), 3600);
        tokenExpiresAt = now + Math.max(60, expiresIn - 60) * 1000;
        return cachedAccessToken;
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Configura variables ZOHO antes de sincronizar clientes.");
        }
    }

    private boolean isConfigured() {
        return hasText(deskBaseUrl)
                && hasText(accountsUrl)
                && hasText(orgId)
                && hasText(clientId)
                && hasText(clientSecret)
                && hasText(refreshToken);
    }

    private SupportClient findLocalClient(Conversation conversation) {
        if (hasText(conversation.getZohoContactId())) {
            SupportClient byZohoId = supportClientRepository
                    .findByZohoContactId(conversation.getZohoContactId().trim())
                    .orElse(null);
            if (byZohoId != null) {
                return byZohoId;
            }
        }

        if (hasText(conversation.getClienteCorreo())) {
            SupportClient byEmail = supportClientRepository
                    .findFirstByActiveTrueAndEmailIgnoreCase(conversation.getClienteCorreo().trim())
                    .orElse(null);
            if (byEmail != null) {
                return byEmail;
            }
        }

        if (hasText(conversation.getClienteTelefono())) {
            return supportClientRepository
                    .findByPhoneOrMobile(conversation.getClienteTelefono().trim(), PageRequest.of(0, 1))
                    .stream()
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    private Map<String, Object> contactPayload(Conversation conversation) {
        Map<String, Object> contact = new LinkedHashMap<>();
        contact.put("lastName", hasText(conversation.getClienteNombre()) ? conversation.getClienteNombre().trim() : "Cliente Reporteria");

        if (hasText(conversation.getClienteCorreo())) {
            contact.put("email", conversation.getClienteCorreo().trim());
        }

        if (hasText(conversation.getClienteTelefono())) {
            contact.put("phone", conversation.getClienteTelefono().trim());
        }

        return contact;
    }

    private String ticketSubject(Conversation conversation) {
        String subject = firstText(conversation.getAsunto(), conversation.getCodigo(), "Ticket Reporteria");
        return subject.length() > 255 ? subject.substring(0, 255) : subject;
    }

    private String ticketDescription(Conversation conversation) {
        return """
                Conversacion: %s
                Cliente: %s
                Telefono: %s
                Correo: %s
                Agente: %s
                Tiempo de gestion: %s minutos

                Descripcion:
                %s

                Observaciones:
                %s
                """.formatted(
                firstText(conversation.getCodigo(), "Sin codigo"),
                firstText(conversation.getClienteNombre(), "Sin cliente"),
                firstText(conversation.getClienteTelefono(), "Sin telefono"),
                firstText(conversation.getClienteCorreo(), "Sin correo"),
                firstText(conversation.getAgenteNombre(), "Sin agente"),
                conversation.getTiempoGestionMinutos() != null ? conversation.getTiempoGestionMinutos() : 0,
                firstText(conversation.getDescripcion(), "Sin descripcion"),
                firstText(conversation.getObservaciones(), "Sin observaciones")
        );
    }

    private String zohoPriority(Long priorityId) {
        if (priorityId == null) {
            return "Low";
        }

        return switch (priorityId.intValue()) {
            case 3, 4 -> "High";
            case 2 -> "Medium";
            default -> "Low";
        };
    }

    private String zohoChannel(Long channelId) {
        if (channelId == null) {
            return "Web";
        }

        return switch (channelId.intValue()) {
            case 1 -> "WhatsApp";
            case 2 -> "Facebook";
            case 3 -> "Instagram";
            case 5 -> "Email";
            default -> "Web";
        };
    }

    private Map<String, Object> normalizeTicketResponse(Map response) {
        if (response == null) {
            throw new IllegalStateException("Zoho no devolvio informacion del ticket creado.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", stringValue(response.get("id")));
        result.put("ticketNumber", firstText(stringValue(response.get("ticketNumber")), stringValue(response.get("ticket_number"))));
        result.put("webUrl", firstText(stringValue(response.get("webUrl")), stringValue(response.get("web_url"))));
        result.put("raw", response);

        if (!hasText((String) result.get("id"))) {
            throw new IllegalStateException("Zoho creo una respuesta sin id de ticket: " + response);
        }

        return result;
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

    private String firstNonBlank(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            String value = stringValue(map.get(key));
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private LocalDateTime parseDate(String value) {
        if (!hasText(value)) {
            return null;
        }

        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (Exception ignoredAgain) {
                return null;
            }
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

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
