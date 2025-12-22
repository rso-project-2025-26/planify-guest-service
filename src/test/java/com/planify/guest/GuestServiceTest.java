package com.planify.guestservice.service;

import com.planify.guestservice.event.KafkaProducer;
import com.planify.guestservice.model.Invitation;
import com.planify.guestservice.repository.InvitationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GuestServiceTest {

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private KafkaProducer kafkaProducer;

    @InjectMocks
    private GuestService guestService;

    private Invitation invitation;
    private Long eventId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        eventId = 10L;
        userId = UUID.randomUUID();

        invitation = Invitation.builder()
                .id(1L)
                .eventId(eventId)
                .userId(userId)
                .rsvpStatus(Invitation.RsvpStatus.PENDING)
                .checkedIn(false)
                .invitationReceivedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void testGetMyInvitations() {
        when(invitationRepository.findByUserId(userId))
                .thenReturn(List.of(invitation));

        var result = guestService.getMyInvitations(userId);

        assertEquals(1, result.size());
        assertEquals(userId, result.get(0).getUserId());
        verify(invitationRepository).findByUserId(userId);
    }

    @Test
    void testGetMyInvitation_Success() {
        when(invitationRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.of(invitation));

        var result = guestService.getMyInvitation(eventId, userId);

        assertEquals(invitation.getId(), result.getId());
        verify(invitationRepository).findByEventIdAndUserId(eventId, userId);
    }

    @Test
    void testGetMyInvitation_NotFound() {
        when(invitationRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> guestService.getMyInvitation(eventId, userId));
    }

    @Test
    void testGetMyAcceptedEvents() {
        when(invitationRepository.findByUserIdAndRsvpStatus(userId, Invitation.RsvpStatus.ACCEPTED))
                .thenReturn(List.of(invitation));

        var result = guestService.getMyAcceptedEvents(userId);

        assertEquals(1, result.size());
        verify(invitationRepository)
                .findByUserIdAndRsvpStatus(userId, Invitation.RsvpStatus.ACCEPTED);
    }

    @Test
    void testAcceptInvitation() {
        when(invitationRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.of(invitation));

        when(invitationRepository.save(any(Invitation.class)))
                .thenReturn(invitation);

        var result = guestService.acceptInvitation(eventId, userId);

        assertEquals(Invitation.RsvpStatus.ACCEPTED, result.getRsvpStatus());
        verify(invitationRepository).save(invitation);
        verify(kafkaProducer).sendMessage(eq("rsvp-accepted"), contains("\"eventId\": 10"));
    }

    @Test
    void testDeclineInvitation() {
        when(invitationRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.of(invitation));

        when(invitationRepository.save(any(Invitation.class)))
                .thenReturn(invitation);

        var result = guestService.declineInvitation(eventId, userId);

        assertEquals(Invitation.RsvpStatus.DECLINED, result.getRsvpStatus());
        verify(invitationRepository).save(invitation);
        verify(kafkaProducer).sendMessage(eq("rsvp-declined"), contains("\"userId\":"));
    }

    @Test
    void testMaybeInvitation() {
        when(invitationRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.of(invitation));

        when(invitationRepository.save(any(Invitation.class)))
                .thenReturn(invitation);

        var result = guestService.maybeInvitation(eventId, userId);

        assertEquals(Invitation.RsvpStatus.MAYBE, result.getRsvpStatus());
        verify(invitationRepository).save(invitation);
    }

    @Test
    void testCheckIn_Success() {
        invitation.setRsvpStatus(Invitation.RsvpStatus.ACCEPTED);

        when(invitationRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.of(invitation));

        when(invitationRepository.save(any(Invitation.class)))
                .thenReturn(invitation);

        var result = guestService.checkIn(eventId, userId);

        assertTrue(result.getCheckedIn());
        verify(invitationRepository).save(invitation);
        verify(kafkaProducer).sendMessage(eq("guest-checked-in"), contains("\"eventId\": 10"));
    }

    @Test
    void testCheckIn_NotAccepted() {
        invitation.setRsvpStatus(Invitation.RsvpStatus.PENDING);

        when(invitationRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.of(invitation));

        assertThrows(RuntimeException.class,
                () -> guestService.checkIn(eventId, userId));
    }

    @Test
    void testMarkAsViewed() {
        when(invitationRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.of(invitation));

        guestService.markAsViewed(eventId, userId);

        verify(invitationRepository).save(invitation);
    }

    @Test
    void testMarkAsViewed_NoInvitation() {
        when(invitationRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.empty());

        guestService.markAsViewed(eventId, userId);

        verify(invitationRepository, never()).save(any());
    }

    @Test
    void testGetEventInvitations() {
        when(invitationRepository.findByEventId(eventId))
                .thenReturn(List.of(invitation));

        var result = guestService.getEventInvitations(eventId);

        assertEquals(1, result.size());
        verify(invitationRepository).findByEventId(eventId);
    }

    @Test
    void testHandleGuestInvited_New() {
        when(invitationRepository.existsByEventIdAndUserId(eventId, userId))
                .thenReturn(false);

        when(invitationRepository.save(any(Invitation.class)))
                .thenReturn(invitation);

        guestService.handleGuestInvited(eventId, userId);

        verify(invitationRepository).save(any(Invitation.class));
    }

    @Test
    void testHandleGuestInvited_AlreadyExists() {
        when(invitationRepository.existsByEventIdAndUserId(eventId, userId))
                .thenReturn(true);

        guestService.handleGuestInvited(eventId, userId);

        verify(invitationRepository, never()).save(any());
    }

    @Test
    void testHandleGuestRemoved() {
        when(invitationRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.of(invitation));

        guestService.handleGuestRemoved(eventId, userId);

        verify(invitationRepository).delete(invitation);
    }

    @Test
    void testHandleGuestRemoved_NotFound() {
        when(invitationRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.empty());

        guestService.handleGuestRemoved(eventId, userId);

        verify(invitationRepository, never()).delete(any());
    }

    @Test
    void testHandleEventDeleted() {
        guestService.handleEventDeleted(eventId);

        verify(invitationRepository).deleteByEventId(eventId);
    }
}
