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
- F2: DONE (Persistencia, POST /documents con respuesta inmediata documentId + PROCESSING, validaciones)
- F3: IN PROGRESS (Workers asíncronos, extractores TXT/PDF/MD, ensureIndexExists)
- F4: IN PROGRESS (Motor de búsqueda ES, fix compatibility-mode, queries FTS)
- F5: DONE (Frontend: upload con SSE, search, viewer - todas las rutas operativas)
- F6: IN PROGRESS (SSE backend + Angular: eventos emitidos al cambiar estado)
- F7: PENDING (Calidad: unit tests, integration tests, benchmark)
- F8: PENDING (Documentación: architecture.md, ia.md, README)

## 4. Architecture Decisions
*(Ninguna decisión técnica registrada fuera de `AGENTS.md` hasta ahora)*

## 5. Domain Knowledge
- **Estados del documento**: PROCESSING → INDEXED, o PROCESSING → ERROR.
- **Procesamiento**: El POST responde inmediatamente, la indexación es asíncrona.
- **Formatos permitidos**: TXT, PDF, MD.
- **Actualizaciones en tiempo real**: Vía SSE basado en el documentId.

## 6. API Contracts

### POST /api/documents
Status: IMPLEMENTED
Request: `multipart/form-data` (file, title, author, category, tags, version)
Response: 202 Accepted `{ "documentId": "uuid", "status": "PROCESSING" }`
Nota: El backend responde inmediatamente con documentId; el frontend muestra el ID y estado PROCESSING enseguida, y usa SSE paraNotifier cuándo cambia a INDEXED o ERROR.

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
Status: CONFIGURED
- Índice: `documents`
- Mapping inicial requerido para: title, author, category, tags, version, content.
- Búsqueda Full-Text sin SQL `LIKE`.
- Se requiere highlighting en title y content.
- Configuración: `quarkus.elasticsearch.hosts=${ELASTICSEARCH_URL:elasticsearch:9200}`
- Se añadió modo compatibility-mode=false para evitar error de headers HTTP.

## 8. Async Processing
Status: IN PROGRESS
- **Mecanismo**: Quarkus EventBus + workers @Blocking
- **Flujo**: Extraer texto -> Indexar en ES (con ensureIndexExists()) -> Actualizar status -> Emitir evento SSE
- **Mejoras**: onStartup() con retry (3 intentos, 2s delay) para no bloquear arranque; ensureIndexExists() idempotente para crear índice en primer documento.

## 9. SSE / Real Time
Status: PENDING / IN PROGRESS
- **Endpoint**: `/api/events?documentId={id}`
- **Formato**: Eventos JSON `{ "documentId": "uuid", "status": "INDEXED" }`
- **Flujo**: El backend notifica al cliente los cambios de estado (INDEXED o ERROR) sin polling.

## 10. Testing Memory
- **Frameworks**: JUnit 5, Mockito, QuarkusTest, Testcontainers, k6.
- **Unitarias**: Pendientes (estructura lista, cobertura objetivo >= 80%). Los tests ahora pueden conectar a ES gracias al fix en application.properties: `%test.quarkus.elasticsearch.hosts=${ELASTICSEARCH_URL:elasticsearch:9200}`
- **Integración**: Pendientes con Testcontainers (levantar PG + ES automáticamente)
- **Rendimiento**: Pendientes (benchmark k6 objetivo p95 < 1000ms)

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
- [ ] P0 — Implementar búsqueda Full-Text en Elasticsearch y highlighting (F4)
- [ ] P1 — Resolver problema de compatibilidad cliente ES (media_type_header_exception)
- [ ] P1 — Optimizar y validar workers asíncronos y conexión ES (F3/F4) — Ja completado ensureIndexExists() y onStartup con retry
- [ ] P2 — Implementar benchmark k6 y medir latencias de búsqueda (F7)
- [ ] P3 — Redactar documentación final: architecture.md, ia.md, README (F8)
- [ ] P4 — Desplegar entorno completo con Docker Compose y validar flujos end-to-end

## 16. Last Session Summary
- Se creó este archivo `memory.md` como base operativa persistente.
- Se inspeccionaron controladores REST (`DocumentResource`, `SearchResource`, `SseResource`, `GlobalExceptionMapper`) confirmando avance en la definición de APIs.
- Problema pendiente: El entorno local no reconoce `mvn` en PATH, impidiendo validar la compilación localmente con ese comando.
