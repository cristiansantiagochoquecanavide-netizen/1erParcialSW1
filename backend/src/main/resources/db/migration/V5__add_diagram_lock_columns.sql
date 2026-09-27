-- Flyway migration V5: Adicionar columnas para bloqueo colaborativo de diagramas durante importación/exportación EA
ALTER TABLE case_tool.diagrama_clase
  ADD COLUMN IF NOT EXISTS bloqueado BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS bloqueado_por VARCHAR(255),
  ADD COLUMN IF NOT EXISTS fecha_bloqueo TIMESTAMP WITH TIME ZONE;
