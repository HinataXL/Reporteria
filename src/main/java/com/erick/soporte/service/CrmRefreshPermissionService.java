package com.erick.soporte.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CrmRefreshPermissionService {

    private final AtomicLong sequence = new AtomicLong(1);
    private final List<RefreshRequest> requests = new CopyOnWriteArrayList<>();

    public RefreshRequest request(
            String agentName,
            String agentEmail,
            LocalDate from,
            LocalDate to,
            String taskType,
            String operationCountry,
            String status,
            Integer size
    ) {
        RefreshRequest request = new RefreshRequest(
                sequence.getAndIncrement(),
                agentName,
                agentEmail,
                from,
                to,
                clean(taskType),
                clean(operationCountry),
                clean(status),
                size,
                RequestStatus.PENDING,
                LocalDateTime.now(),
                null
        );
        requests.add(request);
        return request;
    }

    public List<RefreshRequest> pendingRequests() {
        return requests.stream()
                .filter(request -> request.status() == RequestStatus.PENDING)
                .sorted(Comparator.comparing(RefreshRequest::requestedAt).reversed())
                .toList();
    }

    public boolean approve(long id) {
        return updateStatus(id, RequestStatus.APPROVED);
    }

    public boolean reject(long id) {
        return updateStatus(id, RequestStatus.REJECTED);
    }

    public boolean hasApproved(
            String agentName,
            LocalDate from,
            LocalDate to,
            String taskType,
            String operationCountry,
            String status,
            Integer size
    ) {
        return requests.stream().anyMatch(request ->
                request.status() == RequestStatus.APPROVED
                        && matches(request, agentName, from, to, taskType, operationCountry, status, size)
        );
    }

    public boolean consumeApproved(
            String agentName,
            LocalDate from,
            LocalDate to,
            String taskType,
            String operationCountry,
            String status,
            Integer size
    ) {
        for (int i = 0; i < requests.size(); i++) {
            RefreshRequest request = requests.get(i);
            if (request.status() == RequestStatus.APPROVED
                    && matches(request, agentName, from, to, taskType, operationCountry, status, size)) {
                requests.set(i, request.withStatus(RequestStatus.USED));
                return true;
            }
        }
        return false;
    }

    private boolean updateStatus(long id, RequestStatus status) {
        for (int i = 0; i < requests.size(); i++) {
            RefreshRequest request = requests.get(i);
            if (request.id() == id) {
                requests.set(i, request.withStatus(status));
                return true;
            }
        }
        return false;
    }

    private boolean matches(
            RefreshRequest request,
            String agentName,
            LocalDate from,
            LocalDate to,
            String taskType,
            String operationCountry,
            String status,
            Integer size
    ) {
        return Objects.equals(request.agentName(), agentName)
                && Objects.equals(request.from(), from)
                && Objects.equals(request.to(), to)
                && Objects.equals(request.taskType(), clean(taskType))
                && Objects.equals(request.operationCountry(), clean(operationCountry))
                && Objects.equals(request.statusFilter(), clean(status))
                && Objects.equals(request.size(), size);
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public enum RequestStatus {
        PENDING,
        APPROVED,
        REJECTED,
        USED
    }

    public record RefreshRequest(
            long id,
            String agentName,
            String agentEmail,
            LocalDate from,
            LocalDate to,
            String taskType,
            String operationCountry,
            String statusFilter,
            Integer size,
            RequestStatus status,
            LocalDateTime requestedAt,
            LocalDateTime resolvedAt
    ) {
        public RefreshRequest withStatus(RequestStatus newStatus) {
            return new RefreshRequest(
                    id,
                    agentName,
                    agentEmail,
                    from,
                    to,
                    taskType,
                    operationCountry,
                    statusFilter,
                    size,
                    newStatus,
                    requestedAt,
                    LocalDateTime.now()
            );
        }
    }
}
