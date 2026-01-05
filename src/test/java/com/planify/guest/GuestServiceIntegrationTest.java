package com.planify.guest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planify.guest.model.Invitation;
import com.planify.guest.repository.InvitationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GuestServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private UUID userId;
    private UUID eventId;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        invitationRepository.deleteAll();
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        organizationId = UUID.randomUUID();
    }

    @Test
    @WithMockUser(roles = "UPORABNIK")
    void fullRSVPWorkflow_AcceptInvitation() throws Exception {
        Invitation invitation = Invitation.builder()
                .eventId(eventId)
                .userId(userId)
                .organizationId(organizationId)
                .rsvpStatus(Invitation.RsvpStatus.PENDING)
                .build();
        invitationRepository.save(invitation);

        mockMvc.perform(get("/api/guests/my-invitations")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].rsvpStatus", is("PENDING")));

        mockMvc.perform(put("/api/guests/my-invitations/{eventId}/accept", eventId)
                        .param("userId", userId.toString())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rsvpStatus", is("ACCEPTED")))
                .andExpect(jsonPath("$.respondedAt").exists());

        mockMvc.perform(get("/api/guests/my-events")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].rsvpStatus", is("ACCEPTED")));

        Invitation updated = invitationRepository.findByEventIdAndUserId(eventId, userId).orElseThrow();
        assertThat(updated.getRsvpStatus()).isEqualTo(Invitation.RsvpStatus.ACCEPTED);
        assertThat(updated.getRespondedAt()).isNotNull();
    }

    @Test
    @WithMockUser(roles = "UPORABNIK")
    void fullRSVPWorkflow_DeclineInvitation() throws Exception {
        Invitation invitation = Invitation.builder()
                .eventId(eventId)
                .userId(userId)
                .organizationId(organizationId)
                .rsvpStatus(Invitation.RsvpStatus.PENDING)
                .build();
        invitationRepository.save(invitation);

        mockMvc.perform(put("/api/guests/my-invitations/{eventId}/decline", eventId)
                        .param("userId", userId.toString())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rsvpStatus", is("DECLINED")))
                .andExpect(jsonPath("$.respondedAt").exists());

        mockMvc.perform(get("/api/guests/my-events")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        Invitation updated = invitationRepository.findByEventIdAndUserId(eventId, userId).orElseThrow();
        assertThat(updated.getRsvpStatus()).isEqualTo(Invitation.RsvpStatus.DECLINED);
        assertThat(updated.getRespondedAt()).isNotNull();
    }

    @Test
    @WithMockUser(roles = "UPORABNIK")
    void changeRSVPFromAcceptedToDeclined() throws Exception {
        Invitation invitation = Invitation.builder()
                .eventId(eventId)
                .userId(userId)
                .organizationId(organizationId)
                .rsvpStatus(Invitation.RsvpStatus.ACCEPTED)
                .build();
        invitationRepository.save(invitation);

        mockMvc.perform(get("/api/guests/my-events")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(put("/api/guests/my-invitations/{eventId}/decline", eventId)
                        .param("userId", userId.toString())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rsvpStatus", is("DECLINED")));

        mockMvc.perform(get("/api/guests/my-events")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(roles = "UPORABNIK")
    void getMyInvitations_WithMultipleEvents() throws Exception {
        UUID event2 = UUID.randomUUID();
        UUID event3 = UUID.randomUUID();

        invitationRepository.save(Invitation.builder()
                .eventId(eventId)
                .userId(userId)
                .organizationId(organizationId)
                .rsvpStatus(Invitation.RsvpStatus.PENDING)
                .build());

        invitationRepository.save(Invitation.builder()
                .eventId(event2)
                .userId(userId)
                .organizationId(organizationId)
                .rsvpStatus(Invitation.RsvpStatus.ACCEPTED)
                .build());

        invitationRepository.save(Invitation.builder()
                .eventId(event3)
                .userId(userId)
                .organizationId(organizationId)
                .rsvpStatus(Invitation.RsvpStatus.DECLINED)
                .build());

        mockMvc.perform(get("/api/guests/my-invitations")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        mockMvc.perform(get("/api/guests/my-events")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].eventId", is(event2.toString())));
    }

    @Test
    void internalAPI_GetEventInvitations() throws Exception {
        UUID user2 = UUID.randomUUID();
        
        invitationRepository.save(Invitation.builder()
                .eventId(eventId)
                .userId(userId)
                .organizationId(organizationId)
                .rsvpStatus(Invitation.RsvpStatus.ACCEPTED)
                .build());

        invitationRepository.save(Invitation.builder()
                .eventId(eventId)
                .userId(user2)
                .organizationId(organizationId)
                .rsvpStatus(Invitation.RsvpStatus.PENDING)
                .build());

        mockMvc.perform(get("/api/guests/internal/events/{eventId}/invitations", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].eventId", everyItem(is(eventId.toString()))));
    }

    @Test
    @WithMockUser(roles = "UPORABNIK")
    void acceptInvitation_WhenNotFound_ShouldReturnError() throws Exception {
        UUID nonExistentEvent = UUID.randomUUID();

        mockMvc.perform(put("/api/guests/my-invitations/{eventId}/accept", nonExistentEvent)
                        .param("userId", userId.toString())
                        .with(csrf()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(roles = "UPORABNIK")
    void declineInvitation_WhenNotFound_ShouldReturnError() throws Exception {
        UUID nonExistentEvent = UUID.randomUUID();

        mockMvc.perform(put("/api/guests/my-invitations/{eventId}/decline", nonExistentEvent)
                        .param("userId", userId.toString())
                        .with(csrf()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(roles = "UPORABNIK")
    void isolationBetweenUsers_UsersCanOnlySeeTheirOwnInvitations() throws Exception {
        UUID user2 = UUID.randomUUID();

        invitationRepository.save(Invitation.builder()
                .eventId(eventId)
                .userId(userId)
                .organizationId(organizationId)
                .rsvpStatus(Invitation.RsvpStatus.PENDING)
                .build());

        invitationRepository.save(Invitation.builder()
                .eventId(eventId)
                .userId(user2)
                .organizationId(organizationId)
                .rsvpStatus(Invitation.RsvpStatus.ACCEPTED)
                .build());

        mockMvc.perform(get("/api/guests/my-invitations")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId", is(userId.toString())));

        mockMvc.perform(get("/api/guests/my-invitations")
                        .param("userId", user2.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId", is(user2.toString())));
    }

    @Test
    @WithMockUser(roles = "UPORABNIK")
    void getMyInvitations_WhenNoInvitations_ShouldReturnEmptyList() throws Exception {
        mockMvc.perform(get("/api/guests/my-invitations")
                        .param("userId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(roles = "UPORABNIK")
    void getMyAcceptedEvents_WhenNoAcceptedEvents_ShouldReturnEmptyList() throws Exception {
        invitationRepository.save(Invitation.builder()
                .eventId(eventId)
                .userId(userId)
                .organizationId(organizationId)
                .rsvpStatus(Invitation.RsvpStatus.PENDING)
                .build());

        mockMvc.perform(get("/api/guests/my-events")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void internalAPI_GetEventInvitations_WhenNoInvitations_ShouldReturnEmptyList() throws Exception {
        mockMvc.perform(get("/api/guests/internal/events/{eventId}/invitations", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
