package com.planify.guestservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invitations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invitation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "event_id", nullable = false)
    private Long eventId;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "rsvp_status", nullable = false)
    @Builder.Default
    private RsvpStatus rsvpStatus = RsvpStatus.PENDING;
    
    @Column(name = "responded_at")
    private LocalDateTime respondedAt;
    
    @Column(name = "checked_in")
    @Builder.Default
    private Boolean checkedIn = false;
    
    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;
    
    @Column(name = "invitation_received_at", nullable = false, updatable = false)
    private LocalDateTime invitationReceivedAt;
    
    @Column(name = "last_viewed_at")
    private LocalDateTime lastViewedAt;
    
    @PrePersist
    protected void onCreate() {
        invitationReceivedAt = LocalDateTime.now();
        if (rsvpStatus == null) rsvpStatus = RsvpStatus.PENDING;
        if (checkedIn == null) checkedIn = false;
    }
    
    public enum RsvpStatus {
        PENDING,
        ACCEPTED,
        DECLINED,
        MAYBE
    }
}