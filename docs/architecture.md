# Decisiones de la base técnica

- La API se organiza inicialmente por responsabilidad: `controller` → `service`; la futura persistencia del dominio añadirá repositorios y entidades sin adelantar su modelo.
- Flyway es la única vía para evolucionar el esquema. La primera migración inicializa el esquema técnico, sin introducir entidades del dominio antes de definir el modelo UML.
- La ruta de salud es una excepción deliberada al dominio: permite validar infraestructura durante esta fase.
- `ai-local` no se invoca desde la API. Cuando exista, se integrará mediante un puerto/interfaz para conservar la independencia del proveedor y el funcionamiento offline.
- `generator` conservará la secuencia del proyecto: modelo UML validado → backend → frontend basado en el contrato del backend.

