CREATE TABLE travel_clearance_history (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL,
    previous_status VARCHAR(32),
    new_status VARCHAR(32) NOT NULL,
    changed_by VARCHAR(100) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_travel_clearance_history_request
        FOREIGN KEY (request_id) REFERENCES travel_clearance_requests (id)
);

CREATE INDEX idx_travel_clearance_history_request_changed_at
    ON travel_clearance_history (request_id, changed_at, id);
