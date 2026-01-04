-- Invitations table (guest perspective)
CREATE TABLE invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL,
    user_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    
    rsvp_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    responded_at TIMESTAMP,
    
    invitation_received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(event_id, user_id)
);

CREATE INDEX idx_invitations_event ON invitations(event_id);
CREATE INDEX idx_invitations_user ON invitations(user_id);
CREATE INDEX idx_invitations_organization ON invitations(organization_id);
CREATE INDEX idx_invitations_rsvp_status ON invitations(rsvp_status);

COMMENT ON TABLE invitations IS 'Guest invitations and RSVP tracking (guest perspective)';
COMMENT ON COLUMN invitations.event_id IS 'Reference to event (no FK - cross-database)';
COMMENT ON COLUMN invitations.organization_id IS 'Organization ID for permission checks';
COMMENT ON COLUMN invitations.rsvp_status IS 'Guest response: PENDING, ACCEPTED, DECLINED, MAYBE';