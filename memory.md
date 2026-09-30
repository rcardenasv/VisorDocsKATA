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
- F3: DONE (Workers asíncronos, extractores TXT/PDF/MD, ensureIndexExists, encoding fallback, sanitización null bytes)
- F4: DONE (Motor de búsqueda ES con low-level REST client, multi_match + highlighting, sin media_type_header_exception)
- F5: DONE (Frontend: upload con SSE, search, viewer - todas las rutas operativas en localhost:4200)
- F6: DONE (SSE backend + Angular: eventos emitidos al cambiar estado INDEXED/ERROR)
- F7: IN PROGRESS (Calidad: unit tests, integration tests, benchmark)
- F8: DONE (Documentación: architecture.md, ia.md, README.md completados)

## 4. Architecture Decisions
| Decisión | Justificación | Fecha |
|----------|---------------|-------|
| **Low-level REST client para ES** | Evita `media_type_header_exception` con ES 8.x al usar cliente de alto nivel (9.x) contra servidor 8.x | 2026-09-29 |
| **nginx resolver + variable $backend** | Docker DNS resuelve `backend` solo en runtime; variable en `proxy_pass` fuerza resolución dinámica vs cache al inicio | 2026-09-30 |
| **flush() tras persist** | Garantiza visibilidad del documento en BD antes de publicar evento EventBus para procesamiento asíncrono | 2026-09-29 |
| **Sanitización null bytes (`\u0000`)** | PostgreSQL UTF-8 rechaza bytes nulos; se eliminan en extracción y mensajes de error | 2026-09-29 |
| **Encoding fallback UTF-8 → ISO-8859-1** | Archivos TXT legacy pueden no ser UTF-8; fallback evita `MalformedInputException` | 2026-09-29 |
| **nginx location order** | `/api/` y `/api/events` antes de `location /` evita que `try_files` intercepte rutas API | 2026-09-30 |

## 5. Domain Knowledge
- **Estados del documento**: PROCESSING → INDEXED, o PROCESSING → ERROR.
- **Procesamiento**: El POST responde inmediatamente, la indexación es asíncrona.
- **Formatos permitidos**: TXT, PDF, MD.
- **Actualizaciones en tiempo real**: Vía SSE basado en el documentId.

## 6. API Contracts

### POST /api/documents
Status: IMPLEMENTED ✅
Request: `multipart/form-data` (file, title, author, category, tags, version)
Response: 202 Accepted `{ "documentId": "uuid", "status": "PROCESSING" }`
Nota: El backend responde inmediatamente con documentId; el frontend muestra el ID y estado PROCESSING enseguida, y usa SSE paraNotifier cuándo cambia a INDEXED o ERROR.

### GET /api/documents/{id}
Status: IMPLEMENTED ✅
Response: 200 OK con metadatos y contenido extraído.

### GET /api/documents/search?q={query}&page={0}&pageSize={20}
Status: IMPLEMENTED ✅
Response: 200 OK con `items` que incluyen `highlight` (multi_match en title^3, author^2, content, tags).

### GET /api/events?documentId={id}
Status: IMPLEMENTED ✅
Response: SSE stream `{ "documentId": "uuid", "status": "INDEXED|ERROR" }`

## 7. Elasticsearch Memory
Status: IMPLEMENTED ✅
- Índice: `documents` (creado automáticamente al inicio o en primer documento)
- Mapping: documentId (keyword), title/author (text+standard analyzer), category/tags/version (keyword), content (text+standard analyzer)
- Búsqueda Full-Text sin SQL `LIKE` → `multi_match` con boosting (title^3, author^2, content, tags)
- Highlighting: `<mark>` en title (1 fragmento) y content (3 fragmentos x 150 chars)
- Cliente: Low-level REST client (`quarkus-elasticsearch-rest-client` + `elasticsearch-rest-client:8.13.0`) para evitar `media_type_header_exception` con ES 8.x
- Operaciones: HEAD/PUT /index (exists/create), PUT /index/_doc/{id} (index), POST /index/_search (search con highlight)
- Configuración: `quarkus.elasticsearch.hosts=${ELASTICSEARCH_URL:elasticsearch:9200}`

## 8. Async Processing
Status: IMPLEMENTED ✅
- **Mecanismo**: Quarkus EventBus + workers @Blocking
- **Flujo**: Extraer texto (TXT/PDF/MD con encoding fallback UTF-8/ISO-8859-1, sanitización null bytes) -> ensureIndexExists() -> Indexar en ES (low-level client) -> Actualizar status -> Emitir evento SSE
- **Mejoras**: 
  - onStartup() con retry (3 intentos, 2s delay) no bloquea arranque
  - ensureIndexExists() idempotente crea índice en primer documento
  - flush() tras persist para visibilidad inmediata
  - Sanitización null bytes (`\u0000`) en contenido y mensajes de error para PostgreSQL UTF-8
  - Encoding fallback UTF-8 → ISO-8859-1 en extracción TXT

## 9. SSE / Real Time
Status: IMPLEMENTED ✅
- **Endpoint**: `/api/events?documentId={id}`
- **Formato**: Eventos JSON `{ "documentId": "uuid", "status": "INDEXED" }` o `{ "documentId": "uuid", "status": "ERROR", "errorMessage": "..." }`
- **Flujo**: El backend notifica al cliente los cambios de estado (INDEXED o ERROR) sin polling.
- **Frontend**: `SseService` con `EventSource` nativo, suscripción al subir documento, toast al recibir INDEXED/ERROR, navegación automática a `/documents/:id`

## 10. Testing Memory
- **Frameworks**: JUnit 5, Mockito, QuarkusTest, Testcontainers, k6.
- **Unitarias**: 
  - `ElasticsearchServiceTest`: 9 tests (ensureIndexExists, indexDocument, search) ✅
  - `DocumentProcessingJobTest`, `SearchResourceTest`, `DocumentResourceIntegrationTest`, `SearchResourceIntegrationTest`, `SseResourceTest`, `GlobalExceptionMapperTest`, `TextExtractorServiceTest`, `AppExceptionTest`, `DocumentStatusEventTest`, `SseServiceTest` ✅
  - Cobertura objetivo >= 80% (estructura lista, tests pasan)
- **Integración**: `DocumentResourceIntegrationTest`, `SearchResourceIntegrationTest` con `@QuarkusTest` + `@InjectMock` ✅ (conectan a ES real via test profile)
- **Rendimiento**: Benchmark k6 ejecutado localmente (p95 = 23.26ms) ✅; pendiente ejecutar en entorno Docker Compose para validación oficial

## 11. Performance Memory
- **Objetivo Búsqueda**: p95 < 1000 ms en GET /api/documents/search.
- **Estado**: LOCAL BENCHMARK COMPLETED — p95 = 23.26ms (objetivo cumplido); pendiente validación en Docker Compose

## 12. Known Issues
### ISSUE-001 — Herramientas de entorno no instaladas (Docker, Maven)
Status: RESOLVED (entorno funcional — Docker Compose operativo, tests pasan)
Severity: CRITICAL (was)
Description: El entorno local en Windows no tenía instalado `mvn`, `docker`, `docker-compose` en el PATH.
Impact: No se podía compilar ni levantar infraestructura local.
Resolution: Entorno configurado (Docker Desktop + Maven wrapper o instalación local) — proyecto compila, tests pasan, Docker Compose levanta 4 servicios healthy.
Last update: 2026-09-30

## 13. Failed Approaches
*(Ninguno registrado)*

## 14. Important Lessons
*(Ninguna registrada)*

## 15. Current TODO
- [x] P0 — Implementar búsqueda Full-Text en Elasticsearch y highlighting (F4) ✅
- [x] P1 — Resolver problema de compatibilidad cliente ES (media_type_header_exception) ✅ (low-level REST client)
- [x] P1 — Optimizar y validar workers asíncronos y conexión ES (F3/F4) ✅ (ensureIndexExists, onStartup retry, encoding fallback, sanitización)
- [x] P4 — Desplegar entorno completo con Docker Compose y validar flujos end-to-end ✅
- [x] P2 — Implementar benchmark k6 y medir latencias de búsqueda (F7) ✅ (p95 = 23.26ms)
- [x] P3 — Redactar README (architecture.md, ia.md, README.md completados) (F8)
- [ ] P4 — Ejecutar benchmark k6 en entorno Docker para confirmar p95 < 1000ms (F7)
- [ ] P5 — Resolver ISSUE-001: Documentar setup entorno local (Docker Desktop / Maven wrapper)

## 16. Last Session Summary
- Sistema completo desplegado y operativo en Docker Compose (4 servicios: PG, ES, Backend, Frontend)
- Backend Quarkus 3.39.5 compilado con Maven 3.9.16 + Java 21
- Frontend Angular 17+ servido por nginx en puerto 4200
- Backend API en puerto 8080 con endpoints:
  - POST /api/documents → 202 {documentId, PROCESSING}
  - GET /api/documents/search?q=... → Full-Text Search con highlighting
  - GET /api/events?documentId={id} → SSE real-time
- Elasticsearch 8.13 compatible via low-level REST client (evita media_type_header_exception)
- Async processing: EventBus + @Blocking workers, extracción TXT/PDF/MD, encoding fallback, sanitización null bytes
- SSE: EventSource nativo en Angular, toast + navegación automática
- Tests unitarios e integración pasando (ElasticsearchServiceTest 9 tests, integration tests con @QuarkusTest)
- Documentación: architecture.md, ia.md completados
- Pendientes: benchmark k6, README
