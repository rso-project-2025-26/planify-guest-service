-- Guests table
CREATE TABLE invitations (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    user_id UUID NOT NULL,
    
    rsvp_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    responded_at TIMESTAMP,
    
    checked_in BOOLEAN DEFAULT FALSE,
    checked_in_at TIMESTAMP,
    
    invitation_received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_viewed_at TIMESTAMP,
    
    UNIQUE(event_id, user_id)
);

CREATE INDEX idx_invitations_event ON invitations(event_id);
CREATE INDEX idx_invitations_user ON invitations(user_id);
CREATE INDEX idx_invitations_rsvp_status ON invitations(rsvp_status);
CREATE INDEX idx_invitations_checked_in ON invitations(checked_in);

COMMENT ON TABLE invitations IS 'Guest invitations and RSVP tracking';
COMMENT ON COLUMN invitations.event_id IS 'Reference to event (no FK - cross-database)';
COMMENT ON COLUMN invitations.rsvp_status IS 'Guest response: PENDING, ACCEPTED, DECLINED, MAYBE';