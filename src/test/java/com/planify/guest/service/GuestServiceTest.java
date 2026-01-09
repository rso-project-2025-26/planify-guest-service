package com.planify.guest.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planify.guest.event.KafkaProducer;
import com.planify.guest.model.Invitation;
import com.planify.guest.repository.InvitationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GuestServiceTest {

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private KafkaProducer kafkaProducer;

    @InjectMocks
    private GuestService guestService;

    private UUID userId;
    private UUID eventId;
    private UUID organizationId;
    private Invitation invitation;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        organizationId = UUID.randomUUID();

        invitation = Invitation.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .userId(userId)
                .organizationId(organizationId)
                .rsvpStatus(Invitation.RsvpStatus.PENDING)
                .invitationReceivedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getMyInvitations_ShouldReturnUserInvitations() {
        // Given
        List<Invitation> invitations = List.of(invitation);
        when(invitationRepository.findByUserId(userId)).thenReturn(invitations);

        // When
        List<Invitation> result = guestService.getMyInvitations(userId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(invitation);
        verify(invitationRepository).findByUserId(userId);
    }

    @Test
    void getMyInvitation_WhenInvitationExists_ShouldReturnInvitation() {
        // Given
        when(invitationRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.of(invitation));

        // When
        Invitation result = guestService.getMyInvitation(eventId, userId);

        // Then
        assertThat(result).isEqualTo(invitation);
        verify(invitationRepository).findByEventIdAndUserId(eventId, userId);
    }

    @Test
    void getMyInvitation_WhenInvitationNotFound_ShouldThrowException() {
        // Given
        when(invitationRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> guestService.getMyInvitation(eventId, userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invitation not found for event");
        
        verify(invitationRepository).findByEventIdAndUserId(eventId, userId);
    }

    @Test
    void getMyAcceptedEvents_ShouldReturnAcceptedInvitations() {
        // Given
        invitation.setRsvpStatus(Invitation.RsvpStatus.ACCEPTED);
        List<Invitation> acceptedInvitations = List.of(invitation);
        when(invitationRepository.findByUserIdAndRsvpStatus(userId, Invitation.RsvpStatus.ACCEPTED))
                .thenReturn(acceptedInvitations);

        // When
        List<Invitation> result = guestService.getMyAcceptedEvents(userId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRsvpStatus()).isEqualTo(Invitation.RsvpStatus.ACCEPTED);
        verify(invitationRepository).findByUserIdAndRsvpStatus(userId, Invitation.RsvpStatus.ACCEPTED);
    }

    @Test
    void acceptInvitation_ShouldUpdateStatusAndPublishKafkaEvent() {
        // Given
        when(invitationRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any(Invitation.class))).thenReturn(invitation);

        // When
        Invitation result = guestService.acceptInvitation(eventId, userId);

        // Then
        assertThat(result.getRsvpStatus()).isEqualTo(Invitation.RsvpStatus.ACCEPTED);
        assertThat(result.getRespondedAt()).isNotNull();
        
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaProducer).sendMessage(topicCaptor.capture(), messageCaptor.capture());
        
        assertThat(topicCaptor.getValue()).isEqualTo("rsvp-accepted");
        assertThat(messageCaptor.getValue()).contains(eventId.toString());
        assertThat(messageCaptor.getValue()).contains(userId.toString());
        verify(invitationRepository).save(any(Invitation.class));
    }

    @Test
    void acceptInvitation_WhenAlreadyAccepted_ShouldStillUpdateAndPublish() {
        // Given
        invitation.setRsvpStatus(Invitation.RsvpStatus.ACCEPTED);
        when(invitationRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any(Invitation.class))).thenReturn(invitation);

        // When
        Invitation result = guestService.acceptInvitation(eventId, userId);

        // Then
        assertThat(result.getRsvpStatus()).isEqualTo(Invitation.RsvpStatus.ACCEPTED);
        verify(kafkaProducer).sendMessage(eq("rsvp-accepted"), anyString());
    }

    @Test
    void declineInvitation_ShouldUpdateStatusAndPublishKafkaEvent() {
        // Given
        when(invitationRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any(Invitation.class))).thenReturn(invitation);

        // When
        Invitation result = guestService.declineInvitation(eventId, userId);

        // Then
        assertThat(result.getRsvpStatus()).isEqualTo(Invitation.RsvpStatus.DECLINED);
        assertThat(result.getRespondedAt()).isNotNull();
        
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaProducer).sendMessage(topicCaptor.capture(), messageCaptor.capture());
        
        assertThat(topicCaptor.getValue()).isEqualTo("rsvp-declined");
        assertThat(messageCaptor.getValue()).contains(eventId.toString());
        assertThat(messageCaptor.getValue()).contains(userId.toString());
        verify(invitationRepository).save(any(Invitation.class));
    }

    @Test
    void maybeInvitation_ShouldUpdateStatusToMaybe() {
        // Given
        when(invitationRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any(Invitation.class))).thenReturn(invitation);

        // When
        Invitation result = guestService.maybeInvitation(eventId, userId);

        // Then
        assertThat(result.getRsvpStatus()).isEqualTo(Invitation.RsvpStatus.MAYBE);
        assertThat(result.getRespondedAt()).isNotNull();
        verify(invitationRepository).save(any(Invitation.class));
        verifyNoInteractions(kafkaProducer);
    }

    @Test
    void getEventInvitations_ShouldReturnAllInvitationsForEvent() {
        // Given
        List<Invitation> invitations = List.of(invitation);
        when(invitationRepository.findByEventId(eventId)).thenReturn(invitations);

        // When
        List<Invitation> result = guestService.getEventInvitations(eventId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(invitation);
        verify(invitationRepository).findByEventId(eventId);
    }

    @Test
    void getInvitationsByOrganization_ShouldReturnOrganizationInvitations() {
        // Given
        List<Invitation> invitations = List.of(invitation);
        when(invitationRepository.findByOrganizationId(organizationId)).thenReturn(invitations);

        // When
        List<Invitation> result = guestService.getInvitationsByOrganization(organizationId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(invitation);
        verify(invitationRepository).findByOrganizationId(organizationId);
    }

    @Test
    void handleGuestInvited_WhenInvitationDoesNotExist_ShouldCreateInvitation() {
        // Given
        when(invitationRepository.existsByEventIdAndUserId(eventId, userId)).thenReturn(false);
        when(invitationRepository.save(any(Invitation.class))).thenReturn(invitation);

        // When
        guestService.handleGuestInvited(eventId, userId, organizationId);

        // Then
        ArgumentCaptor<Invitation> invitationCaptor = ArgumentCaptor.forClass(Invitation.class);
        verify(invitationRepository).save(invitationCaptor.capture());
        
        Invitation saved = invitationCaptor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getOrganizationId()).isEqualTo(organizationId);
        assertThat(saved.getRsvpStatus()).isEqualTo(Invitation.RsvpStatus.PENDING);
    }

    @Test
    void handleGuestInvited_WhenInvitationAlreadyExists_ShouldNotCreateDuplicate() {
        // Given
        when(invitationRepository.existsByEventIdAndUserId(eventId, userId)).thenReturn(true);

        // When
        guestService.handleGuestInvited(eventId, userId, organizationId);

        // Then
        verify(invitationRepository, never()).save(any(Invitation.class));
    }

    @Test
    void handleGuestRemoved_WhenInvitationExists_ShouldDeleteInvitation() {
        // Given
        when(invitationRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.of(invitation));

        // When
        guestService.handleGuestRemoved(eventId, userId);

        // Then
        verify(invitationRepository).delete(invitation);
    }

    @Test
    void handleGuestRemoved_WhenInvitationWasAccepted_ShouldPublishRsvpDeclinedEvent() {
        // Given
        invitation.setRsvpStatus(Invitation.RsvpStatus.ACCEPTED);
        when(invitationRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.of(invitation));

        // When
        guestService.handleGuestRemoved(eventId, userId);

        // Then
        verify(invitationRepository).delete(invitation);
        
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaProducer).sendMessage(topicCaptor.capture(), messageCaptor.capture());
        
        assertThat(topicCaptor.getValue()).isEqualTo("rsvp-declined");
        assertThat(messageCaptor.getValue()).contains("\"wasAccepted\":true");
    }

    @Test
    void handleGuestRemoved_WhenInvitationWasPending_ShouldNotPublishKafkaEvent() {
        // Given
        invitation.setRsvpStatus(Invitation.RsvpStatus.PENDING);
        when(invitationRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.of(invitation));

        // When
        guestService.handleGuestRemoved(eventId, userId);

        // Then
        verify(invitationRepository).delete(invitation);
        verifyNoInteractions(kafkaProducer);
    }

    @Test
    void handleGuestRemoved_WhenInvitationNotFound_ShouldDoNothing() {
        // Given
        when(invitationRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.empty());

        // When
        guestService.handleGuestRemoved(eventId, userId);

        // Then
        verify(invitationRepository, never()).delete(any(Invitation.class));
        verifyNoInteractions(kafkaProducer);
    }

    @Test
    void handleEventDeleted_ShouldDeleteAllInvitationsForEvent() {
        // Act
        guestService.handleEventDeleted(eventId);

        // Then
        verify(invitationRepository).deleteByEventId(eventId);
    }
}
