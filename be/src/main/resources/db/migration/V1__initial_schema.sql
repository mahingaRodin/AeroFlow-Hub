CREATE TABLE permissions (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uk_permissions_name UNIQUE (name)
);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uk_roles_name UNIQUE (name)
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id),
    CONSTRAINT uk_role_permissions_role_permission UNIQUE (role_id, permission_id)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT uk_user_roles_user_role UNIQUE (user_id, role_id)
);

CREATE TABLE travel_clearance_requests (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    purpose VARCHAR(500) NOT NULL,
    destination VARCHAR(150) NOT NULL,
    travel_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    requester_id UUID NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_travel_clearance_request_requester
        FOREIGN KEY (requester_id) REFERENCES users (id)
);

CREATE TABLE message_quarantine (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    message_format VARCHAR(32) NOT NULL,
    raw_payload TEXT NOT NULL,
    submitted_by VARCHAR(100) NOT NULL,
    issue_count INTEGER NOT NULL,
    issues_truncated BOOLEAN NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE message_quarantine_issues (
    quarantine_id UUID NOT NULL,
    issue_order INTEGER NOT NULL,
    line_number INTEGER NOT NULL,
    code VARCHAR(64) NOT NULL,
    description VARCHAR(500) NOT NULL,
    raw_excerpt VARCHAR(500),
    CONSTRAINT fk_message_quarantine_issue_entry
        FOREIGN KEY (quarantine_id) REFERENCES message_quarantine (id) ON DELETE CASCADE,
    CONSTRAINT pk_message_quarantine_issues PRIMARY KEY (quarantine_id, issue_order)
);

CREATE INDEX idx_message_quarantine_created_at ON message_quarantine (created_at);
CREATE INDEX idx_message_quarantine_expires_at ON message_quarantine (expires_at);
