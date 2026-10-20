-- Flyway migration V6: Control de concurrencia optimista por revisión del diagrama
ALTER TABLE case_tool.diagrama_clase
  ADD COLUMN IF NOT EXISTS revision BIGINT NOT NULL DEFAULT 1;

COMMENT ON COLUMN case_tool.diagrama_clase.revision IS 'Revisión monotónica para control de concurrencia optimista';
