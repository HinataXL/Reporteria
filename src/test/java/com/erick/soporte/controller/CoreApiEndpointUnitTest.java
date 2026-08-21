package com.erick.soporte.controller;

import com.erick.soporte.entity.Conversation;
import com.erick.soporte.entity.FrontendErrorLog;
import com.erick.soporte.entity.Role;
import com.erick.soporte.entity.User;
import com.erick.soporte.entity.WebhookEvent;
import com.erick.soporte.repository.ConversationRepository;
import com.erick.soporte.repository.FrontendErrorLogRepository;
import com.erick.soporte.repository.WebhookEventRepository;
import com.erick.soporte.security.CustomUserPrincipal;
import com.erick.soporte.service.DashboardRealtimeService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoreApiEndpointUnitTest {

    @Test
    void qpayproHealthEndpointReturnsReadyMessage() {
        QpayproWebhookController controller = new QpayproWebhookController(
                mock(WebhookEventRepository.class),
                mock(SimpMessagingTemplate.class)
        );

        ResponseEntity<String> response = controller.health();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo("QPAYPRO WEBHOOK READY");
    }

    @Test
    void qpayproWebhookPersistsPayloadAndNotifiesRealtimeTopic() {
        WebhookEventRepository repository = mock(WebhookEventRepository.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        QpayproWebhookController controller = new QpayproWebhookController(repository, messagingTemplate);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "JUnit");

        ResponseEntity<String> response = controller.receiveQpayproWebhook("{\"event\":\"paid\"}", request);

        assertThat(response.getBody()).isEqualTo("OK");
        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(repository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getSource()).isEqualTo("QPAYPRO");
        assertThat(eventCaptor.getValue().getPayload()).isEqualTo("{\"event\":\"paid\"}");
        assertThat(eventCaptor.getValue().getIp()).isEqualTo("127.0.0.1");
        verify(messagingTemplate).convertAndSend("/topic/webhook-events", eventCaptor.getValue());
    }

    @Test
    void keepAliveRefreshesSession() {
        SessionController controller = new SessionController();
        HttpSession session = new MockHttpSession();

        ResponseEntity<Map<String, Object>> response = controller.keepAlive(session);

        assertThat(response.getBody()).containsEntry("alive", true);
        assertThat(response.getBody()).containsEntry("message", "Sesión renovada");
        assertThat(session.getAttribute("lastKeepAlive")).isNotNull();
    }

    @Test
    void frontendErrorEndpointStoresSafePayloadWithAuthenticatedUser() {
        FrontendErrorLogRepository repository = mock(FrontendErrorLogRepository.class);
        FrontendErrorController controller = new FrontendErrorController(repository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("User-Agent", "Chrome");

        FrontendErrorController.FrontendErrorRequest payload = new FrontendErrorController.FrontendErrorRequest(
                "WARN",
                "browser",
                "Algo paso",
                "conversation-workspace.js",
                "stack",
                "/conversations/create",
                12,
                4
        );

        ResponseEntity<Map<String, Object>> response = controller.save(payload, authentication(7L, "Erick", "Pedroza", "ADMIN"), request);

        assertThat(response.getBody()).containsEntry("saved", true);
        ArgumentCaptor<FrontendErrorLog> logCaptor = ArgumentCaptor.forClass(FrontendErrorLog.class);
        verify(repository).save(logCaptor.capture());
        FrontendErrorLog log = logCaptor.getValue();
        assertThat(log.getUsuarioId()).isEqualTo(7L);
        assertThat(log.getUsuarioCorreo()).isEqualTo("erick.pedroza@fixss.com");
        assertThat(log.getRol()).isEqualTo("ADMIN");
        assertThat(log.getLevel()).isEqualTo("WARN");
        assertThat(log.getPageUrl()).isEqualTo("/conversations/create");
        assertThat(log.getIp()).isEqualTo("10.0.0.8");
    }

    @Test
    void conversationStatusEndpointAllowsOwnerToUpdateStatus() {
        ConversationRepository repository = mock(ConversationRepository.class);
        DashboardRealtimeService realtimeService = mock(DashboardRealtimeService.class);
        ConversationStatusApiController controller = new ConversationStatusApiController(repository, realtimeService);
        Conversation conversation = new Conversation();
        ReflectionTestUtils.setField(conversation, "id", 99L);
        conversation.setUserId(7L);
        conversation.setStatusId(1L);

        when(repository.findById(99L)).thenReturn(Optional.of(conversation));
        when(repository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> response = controller.updateStatus(99L, 5L, authentication(7L, "Erick", "Pedroza", "AGENTE"));

        assertThat(response).containsEntry("id", 99L);
        assertThat(response).containsEntry("statusId", 5L);
        assertThat(response).containsEntry("statusName", "Cerrado");
        assertThat(conversation.getFechaFinalizacion()).isNotNull();
        verify(realtimeService).publishConversationChanged("updated", conversation);
    }

    @Test
    void conversationStatusEndpointRejectsInvalidStatus() {
        ConversationRepository repository = mock(ConversationRepository.class);
        ConversationStatusApiController controller = new ConversationStatusApiController(
                repository,
                mock(DashboardRealtimeService.class)
        );
        Conversation conversation = new Conversation();
        conversation.setUserId(7L);

        when(repository.findById(99L)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> controller.updateStatus(99L, 9L, authentication(7L, "Erick", "Pedroza", "AGENTE")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Estado no valido");

        verify(repository, never()).save(any());
    }

    @Test
    void conversationStatusEndpointRejectsAgentWithoutOwnership() {
        ConversationRepository repository = mock(ConversationRepository.class);
        ConversationStatusApiController controller = new ConversationStatusApiController(
                repository,
                mock(DashboardRealtimeService.class)
        );
        Conversation conversation = new Conversation();
        conversation.setUserId(99L);
        conversation.setAgenteNombre("Otro Agente");

        when(repository.findById(50L)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> controller.updateStatus(50L, 3L, authentication(7L, "Erick", "Pedroza", "AGENTE")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No tienes permiso");

        verify(repository, never()).save(any());
    }

    private Authentication authentication(Long id, String nombre, String apellido, String roleName) {
        Role role = new Role();
        role.setNombre(roleName);

        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setNombre(nombre);
        user.setApellido(apellido);
        user.setCorreo((nombre + "." + apellido + "@fixss.com").toLowerCase());
        user.setPassword("password");
        user.setEstado(1);
        user.setRole(role);

        CustomUserPrincipal principal = new CustomUserPrincipal(
                user,
                List.of(new SimpleGrantedAuthority("ROLE_" + roleName))
        );
        return new UsernamePasswordAuthenticationToken(principal, "password", principal.getAuthorities());
    }
}
