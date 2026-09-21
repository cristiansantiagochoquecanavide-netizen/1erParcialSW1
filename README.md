# Proyecto CASE colaborativo

Base técnica del monorepo para una herramienta CASE centrada en diagramas de clases UML. Incluye acceso JWT y gestión del espacio de trabajo (proyectos y colaboradores); no incorpora todavía el editor UML, WebSocket, IA, versionado ni generación de software.

## Estructura

- `frontend/`: cliente React + TypeScript + Vite.
- `backend/`: API Spring Boot con Flyway y PostgreSQL.
- `ai-local/`: futuro adaptador de IA local/offline, aislado del backend.
- `generator/`: futuro generador UML → backend → frontend.
- `docs/`: decisiones y documentación técnica.

## Requisitos

- Docker Desktop (para PostgreSQL).
- JDK 21 o superior.
- Maven 3.9 o superior.
- Node.js 20 o superior.

## Inicio rápido

1. Desde esta carpeta, copie `.env.example` a `.env` y ajuste valores si lo necesita.
2. Inicie PostgreSQL: `docker compose up -d postgres`.
3. En otra terminal, inicie la API: `cd backend; mvn spring-boot:run`.
4. En otra terminal, inicie el cliente: `cd frontend; npm install; npm run dev`.
5. Abra la URL indicada por Vite, normalmente `http://localhost:5173`.

La vista principal permite registro/inicio de sesión, conserva el JWT en `localStorage` y lo envía como `Authorization: Bearer <token>`. También permite crear y administrar proyectos, colaboradores y sus roles. La ruta `GET http://localhost:8080/api/health` confirma el backend y comprueba PostgreSQL mediante `SELECT 1`.

## Perfiles y configuración

El perfil predeterminado es `dev`, configurable con `SPRING_PROFILES_ACTIVE`. La API toma `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` y `JWT_EXPIRATION_MINUTES`; sus valores predeterminados coinciden con `compose.yaml`. Para otro entorno, defina esas variables sin guardar secretos en Git. Cambie obligatoriamente `JWT_SECRET` fuera de desarrollo.

## Pruebas

- Backend: `cd backend; mvn test`
- Frontend: `cd frontend; npm run build`
