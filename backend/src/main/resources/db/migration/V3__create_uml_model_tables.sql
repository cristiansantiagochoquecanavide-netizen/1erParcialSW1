CREATE TABLE diagrama_clase (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  nombre VARCHAR(120) NOT NULL,
  descripcion VARCHAR(500),
  estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_diagrama_clase_project ON diagrama_clase(project_id);

CREATE TABLE elemento_uml (
  id UUID PRIMARY KEY,
  diagrama_id UUID NOT NULL REFERENCES diagrama_clase(id) ON DELETE CASCADE,
  tipo_elemento VARCHAR(30) NOT NULL,
  nombre VARCHAR(120) NOT NULL,
  posicion_x DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  posicion_y DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  propiedades_json TEXT NOT NULL DEFAULT '{}',
  CONSTRAINT ck_elemento_tipo CHECK (tipo_elemento IN ('CLASE', 'INTERFAZ', 'CLASE_ABSTRACTA', 'ENUMERACION'))
);

CREATE INDEX idx_elemento_diagrama ON elemento_uml(diagrama_id);

CREATE TABLE relacion_uml (
  id UUID PRIMARY KEY,
  diagrama_id UUID NOT NULL REFERENCES diagrama_clase(id) ON DELETE CASCADE,
  elemento_origen UUID NOT NULL REFERENCES elemento_uml(id) ON DELETE CASCADE,
  elemento_destino UUID NOT NULL REFERENCES elemento_uml(id) ON DELETE CASCADE,
  nombre_relacion VARCHAR(120),
  tipo_relacion VARCHAR(30) NOT NULL,
  multiplicidad_origen VARCHAR(20),
  multiplicidad_destino VARCHAR(20),
  CONSTRAINT ck_relacion_tipo CHECK (tipo_relacion IN ('ASOCIACION', 'HERENCIA', 'AGREGACION', 'COMPOSICION', 'DEPENDENCIA'))
);

CREATE INDEX idx_relacion_diagrama ON relacion_uml(diagrama_id);
CREATE INDEX idx_relacion_origen ON relacion_uml(elemento_origen);
CREATE INDEX idx_relacion_destino ON relacion_uml(elemento_destino);
