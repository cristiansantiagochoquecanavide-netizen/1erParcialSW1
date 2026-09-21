CREATE TABLE user_account (
  id UUID PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  registered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE project (
  id UUID PRIMARY KEY,
  creator_id UUID NOT NULL REFERENCES user_account(id),
  name VARCHAR(120) NOT NULL,
  description VARCHAR(500),
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE project_member (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
  project_role VARCHAR(20) NOT NULL,
  CONSTRAINT uk_project_member UNIQUE (project_id, user_id),
  CONSTRAINT ck_project_member_role CHECK (project_role IN ('PROPIETARIO', 'EDITOR', 'LECTOR'))
);

CREATE INDEX idx_project_member_user ON project_member(user_id);
