# Project Memory

## 1. Project Identity
- **Nombre**: VisorDocsKATA
- **Objetivo**: Construir una aplicación web para cargar documentos técnicos (TXT, PDF, Markdown) con metadatos, procesarlos e indexarlos de forma asíncrona, buscarlos con Full-Text Search de alto rendimiento, visualizarlos sin descargar el archivo, y actualizar el estado en tiempo real sin hacer polling.
- **Estado**: IN PROGRESS

## 2. Current Stack
- **Backend**: Quarkus (Java) 3.x LTS
- **Frontend**: Angular 17+
- **API**: REST (JAX-RS / RESTEasy Reactive)
- **Base de datos**: PostgreSQL 16
- **Search Engine**: Elasticsearch 8.x
- **Comunicación Real Time**: Server-Sent Events (SSE) — Quarkus Reactive Streams
- **Procesamiento asíncrono**: Quarkus EventBus + workers @Blocking
- **ORM**: Hibernate ORM con Panache
- **Containers**: Docker + Docker Compose
- **Architecture**: Clean Architecture (dominio / aplicación / infraestructura / interfaces)

## 3. Current Project State
Estado de las fases:
- F1: DONE (Esqueleto, init Quarkus, init Angular)
- F2: IN PROGRESS (Persistencia, POST /documents, validaciones)
- F3: PENDING (Procesamiento asíncrono)
- F4: PENDING (Motor de búsqueda)
- F5: PENDING (Frontend)
- F6: PENDING (Tiempo real)
- F7: PENDING (Calidad)
- F8: PENDING (Documentación)

## 4. Architecture Decisions
*(Ninguna decisión técnica registrada fuera de `AGENTS.md` hasta ahora)*

## 5. Domain Knowledge
- **Estados del documento**: PROCESSING → INDEXED, o PROCESSING → ERROR.
- **Procesamiento**: El POST responde inmediatamente, la indexación es asíncrona.
- **Formatos permitidos**: TXT, PDF, MD.
- **Actualizaciones en tiempo real**: Vía SSE basado en el documentId.

## 6. API Contracts

### POST /api/documents
Status: IMPLEMENTED / IN PROGRESS
Request: `multipart/form-data` (file, title, author, category, tags, version)
Response: 202 Accepted `{ "documentId": "uuid", "status": "PROCESSING" }`

### GET /api/documents/{id}
Status: PENDING
Response: 200 OK con metadatos y contenido extraído.

### GET /api/documents/search?q={query}&page={0}&pageSize={20}
Status: IMPLEMENTED / IN PROGRESS
Response: 200 OK con `items` que incluyen `highlight`.

### GET /api/events?documentId={id}
Status: IMPLEMENTED / IN PROGRESS
Response: SSE stream `{ "documentId": "uuid", "status": "..." }`

## 7. Elasticsearch Memory
Status: PENDING CONFIGURATION
- Índice: `documents`
- Mapping inicial requerido para: title, author, category, tags, version, content.
- Búsqueda Full-Text sin SQL `LIKE`.
- Se requiere highlighting en title y content.

## 8. Async Processing
Status: PENDING
- **Mecanismo**: Quarkus EventBus + workers @Blocking
- **Flujo**: Extraer texto -> Indexar en ES -> Actualizar status -> Emitir evento SSE.

## 9. SSE / Real Time
Status: PENDING / IN PROGRESS
- **Endpoint**: `/api/events?documentId={id}`
- **Formato**: Eventos JSON `{ "documentId": "uuid", "status": "INDEXED" }`
- **Flujo**: El backend notifica al cliente los cambios de estado (INDEXED o ERROR) sin polling.

## 10. Testing Memory
- **Frameworks**: JUnit 5, Mockito, QuarkusTest, Testcontainers, k6.
- **Unitarias**: NOT RUN (Cobertura objetivo >= 80%)
- **Integración**: NOT RUN
- **Rendimiento**: NOT RUN

## 11. Performance Memory
- **Objetivo Búsqueda**: p95 < 1000 ms en GET /api/documents/search.
- Estado: UNKNOWN (Sin métricas aún).

## 12. Known Issues
### ISSUE-001 — Herramientas de entorno no instaladas (Docker, Maven)
Status: OPEN
Severity: CRITICAL
Description: El entorno local en Windows no tiene instalado ni `mvn`, ni `docker`, ni `docker-compose` en el PATH.
Impact: No se puede compilar el proyecto (falta `mvn` o `mvnw`) ni levantar la infraestructura local de dependencias como PostgreSQL o Elasticsearch.
Possible solution: Instalar Docker Desktop (con esto podremos generar `mvnw` y correr dependencias) o instalar Maven/Postgres/ES nativamente en Windows.
Last update: 2026-09-29

## 13. Failed Approaches
*(Ninguno registrado)*

## 14. Important Lessons
*(Ninguna registrada)*

## 15. Current TODO
- [ ] P0 — Revisar e implementar lógica restante de la fase 2 (persistencia y subida de archivos).
- [ ] P1 — Resolver problema de compilación en el entorno local para validar avances (Falta Maven/mvnw).
- [ ] P1 — Implementar workers asíncronos y conexión a Elasticsearch (F3/F4).

## 16. Last Session Summary
- Se creó este archivo `memory.md` como base operativa persistente.
- Se inspeccionaron controladores REST (`DocumentResource`, `SearchResource`, `SseResource`, `GlobalExceptionMapper`) confirmando avance en la definición de APIs.
- Problema pendiente: El entorno local no reconoce `mvn` en PATH, impidiendo validar la compilación localmente con ese comando.
