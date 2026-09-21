-- CU09: Gestión de versiones del modelo
CREATE TABLE version_diagrama (
  id UUID PRIMARY KEY,
  diagrama_id UUID NOT NULL REFERENCES diagrama_clase(id) ON DELETE CASCADE,
  usuario_id UUID NOT NULL REFERENCES user_account(id),
  numero_version INT NOT NULL,
  descripcion VARCHAR(255),
  snapshot_json TEXT NOT NULL,
  fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_version_diagrama ON version_diagrama(diagrama_id, numero_version DESC);

-- CU14, CU15, CU16: Registro de generación de software
CREATE TABLE generacion_software (
  id UUID PRIMARY KEY,
  diagrama_id UUID NOT NULL REFERENCES diagrama_clase(id) ON DELETE CASCADE,
  usuario_id UUID NOT NULL REFERENCES user_account(id),
  tipo_generacion VARCHAR(30) NOT NULL,
  configuracion_json TEXT,
  estado VARCHAR(30) NOT NULL DEFAULT 'COMPLETADO',
  fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_generacion_tipo CHECK (tipo_generacion IN ('BACKEND', 'FRONTEND', 'FULLSTACK'))
);

CREATE INDEX idx_generacion_diagrama ON generacion_software(diagrama_id);

-- CU10, CU12: Historial de interacciones de IA (asistencia, comandos y voz)
CREATE TABLE interaccion_ia (
  id UUID PRIMARY KEY,
  diagrama_id UUID NOT NULL REFERENCES diagrama_clase(id) ON DELETE CASCADE,
  usuario_id UUID NOT NULL REFERENCES user_account(id),
  tipo_interaccion VARCHAR(50) NOT NULL,
  entrada_usuario TEXT NOT NULL,
  respuesta_agente TEXT NOT NULL,
  accion_json TEXT,
  fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_interaccion_diagrama ON interaccion_ia(diagrama_id, fecha DESC);
