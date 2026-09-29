# AGENTS.md — Buscador y Visor de Documentos Técnicos

> Archivo de instrucciones operativas para el agente LLM de desarrollo.
> Basado en: `KATA_29_sep_requerimientos_LLM.md`

---

## 1. Rol del agente

Actúa como **Senior Full Stack Engineer y Software Architect** especializado en el stack definido en este documento.

Tu objetivo es construir, paso a paso y de forma incremental, una aplicación web que permita:

1. Cargar documentos técnicos (TXT, PDF, Markdown) con metadatos.
2. Procesarlos e indexarlos de forma asíncrona.
3. Buscarlos con Full-Text Search de alto rendimiento.
4. Visualizarlos sin descargar el archivo.
5. Actualizar el estado de procesamiento en tiempo real sin polling.

---

## 2. Stack tecnológico decidido

> Estas decisiones son **definitivas**. No las cambies sin documentar el motivo en `docs/architecture.md`.

| Capa | Tecnología | Versión objetivo |
|---|---|---|
| **Backend** | Quarkus (Java) | 3.x LTS |
| **Frontend** | Angular | 17+ |
| **API** | REST (JAX-RS / RESTEasy Reactive) | — |
| **Tiempo real** | Server-Sent Events (SSE) — Quarkus Reactive Streams | — |
| **Base de datos** | PostgreSQL | 16 |
| **Motor de búsqueda** | Elasticsearch | 8.x |
| **Cola asíncrona** | Quarkus EventBus + @Blocking workers | — |
| **ORM** | Hibernate ORM with Panache | — |
| **Contenedores** | Docker + Docker Compose | — |
| **Arquitectura** | Clean Architecture (domain / application / infrastructure / interface) | — |

### Justificación resumida (detalle en `docs/architecture.md`)

- **Quarkus**: arranque ultrarrápido, reactive nativo, integración Hibernate Panache simplifica el repositorio, soporte SSE con `@RestStreamElementType`.
- **Angular**: framework opinionado, TypeScript nativo, RxJS para SSE y reactive state.
- **Elasticsearch 8**: Full-Text Search maduro, highlighting nativo, multi-field query, cliente Java oficial `elasticsearch-java`.
- **SSE**: el patrón de comunicación es unidireccional (servidor → cliente), SSE es suficiente y más liviano que WebSocket para este caso.
- **Docker Compose**: despliegue local reproducible con todos los servicios en contenedores.

---

## 3. Restricciones obligatorias

> Las siguientes restricciones vienen del documento de la prueba y NO pueden violarse.

1. **NO usar SQL `LIKE`** ni estrategias equivalentes no indexadas para búsqueda.
2. **Búsqueda Full-Text obligatoria** mediante Elasticsearch 8.
3. **Tiempo de respuesta de búsqueda: 400 ms – 1000 ms** (objetivo medido).
4. **Respuesta inmediata en carga**: el endpoint `POST /documents` debe responder con `documentId + status: PROCESSING` antes de que termine el procesamiento.
5. **Sin polling** en el frontend para saber el estado; usar SSE.
6. **Sin secretos hardcodeados**: toda configuración sensible mediante variables de entorno.
7. **Cobertura de pruebas >= 80%** en componentes críticos del backend.
8. **Sin lógica de negocio en controladores/resources**: usar servicios/use-cases.

---

## 4. Estructura del repositorio

```
VisorDocsKATA/
├── backend/                        # Quarkus (Java)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/visordocs/
│   │   │   │   ├── domain/         # Entidades, enums, value objects
│   │   │   │   │   ├── Document.java
│   │   │   │   │   └── DocumentStatus.java
│   │   │   │   ├── application/    # Use-cases, servicios de aplicacion
│   │   │   │   │   ├── UploadDocumentUseCase.java
│   │   │   │   │   ├── ProcessDocumentUseCase.java
│   │   │   │   │   └── SearchDocumentsUseCase.java
│   │   │   │   ├── infrastructure/ # Implementaciones: ES, Panache repos, SSE, extractores
│   │   │   │   │   ├── persistence/
│   │   │   │   │   ├── search/
│   │   │   │   │   ├── extraction/ # PDF, TXT, MD
│   │   │   │   │   └── sse/
│   │   │   │   └── interfaces/     # REST resources, DTOs, mappers
│   │   │   │       ├── DocumentResource.java
│   │   │   │       └── SearchResource.java
│   │   │   └── resources/
│   │   │       └── application.properties
│   │   └── test/                   # Unit + Integration tests
│   ├── src/main/docker/
│   │   └── Dockerfile.jvm
│   └── pom.xml
│
├── frontend/                       # Angular 17+
│   ├── src/
│   │   ├── app/
│   │   │   ├── features/
│   │   │   │   ├── upload/         # Componente de carga
│   │   │   │   ├── search/         # Componente de busqueda
│   │   │   │   └── viewer/         # Visor de documentos
│   │   │   ├── core/
│   │   │   │   ├── services/       # DocumentService, SearchService, SseService
│   │   │   │   └── models/         # Interfaces TypeScript (Document, SearchResult)
│   │   │   └── shared/             # Componentes reutilizables (highlight, pagination)
│   │   └── environments/
│   └── angular.json
│
├── docs/
│   ├── architecture.md             # Diagrama + justificaciones (OBLIGATORIO)
│   └── ia.md                       # Uso de IA (OBLIGATORIO)
│
├── docker-compose.yml              # Levanta TODO el entorno
├── AGENTS.md                       # Este archivo
└── README.md
```

---

## 5. Modelo de dominio

### Entidad `Document`

```java
@Entity
@Table(name = "documents")
public class Document extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public String id;

    @Column(nullable = false)
    public String title;

    @Column(nullable = false)
    public String author;

    @Column(nullable = false)
    public String category;

    @Column(name = "tags", columnDefinition = "text[]")
    public String[] tags;

    @Column(nullable = false)
    public String version;

    @Column(name = "original_file_name")
    public String originalFileName;

    @Column(name = "file_type")
    public String fileType;   // "txt" | "pdf" | "md"

    @Column(name = "file_size")
    public Long fileSize;

    @Column(columnDefinition = "text")
    public String content;   // texto extraido

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    public DocumentStatus status = DocumentStatus.PROCESSING;

    @Column(name = "error_message")
    public String errorMessage;

    @Column(name = "created_at")
    public Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    public Instant updatedAt = Instant.now();
}
```

### Estados del documento

```
PROCESSING → INDEXED
PROCESSING → ERROR
```

---

## 6. Contratos de API REST

### POST /api/documents

- **Content-Type**: `multipart/form-data`
- **Partes**: `file`, `title`, `author`, `category`, `tags` (comma-separated), `version`
- **Respuesta inmediata** (HTTP 202):

```json
{
  "documentId": "uuid",
  "status": "PROCESSING"
}
```

### GET /api/documents/{id}

- **Respuesta** (HTTP 200):

```json
{
  "documentId": "uuid",
  "status": "INDEXED",
  "metadata": {
    "title": "...",
    "author": "...",
    "category": "...",
    "tags": ["..."],
    "version": "..."
  },
  "content": "Contenido extraido...",
  "originalFileName": "...",
  "fileType": "pdf",
  "createdAt": "...",
  "updatedAt": "..."
}
```

### GET /api/documents/search?q={query}&page={0}&pageSize={20}

- **Respuesta** (HTTP 200):

```json
{
  "items": [
    {
      "documentId": "uuid",
      "title": "...",
      "metadata": { "author": "...", "category": "...", "tags": [], "version": "..." },
      "highlight": ["...fragmento <mark>coincidente</mark>..."]
    }
  ],
  "page": 0,
  "pageSize": 20,
  "total": 150
}
```

### GET /api/events?documentId={id} — SSE

Eventos emitidos al cambiar estado:

```json
{ "documentId": "uuid", "status": "INDEXED" }
{ "documentId": "uuid", "status": "ERROR", "errorMessage": "..." }
```

### Respuesta de error unificada

```json
{
  "error": {
    "code": "UNSUPPORTED_FILE_TYPE",
    "message": "File type '.xyz' is not supported",
    "correlationId": "uuid"
  }
}
```

---

## 7. Variables de entorno

```properties
# backend/src/main/resources/application.properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=${DATABASE_URL:jdbc:postgresql://localhost:5432/visordocs}
quarkus.datasource.username=${DB_USER:visordocs}
quarkus.datasource.password=${DB_PASSWORD:visordocs}
quarkus.hibernate-orm.database.generation=update

quarkus.elasticsearch.hosts=${ELASTICSEARCH_URL:localhost:9200}
app.elasticsearch.index=${ELASTICSEARCH_INDEX:documents}

app.upload.dir=${UPLOAD_DIR:./uploads}
app.upload.max-size-mb=${MAX_FILE_SIZE_MB:10}

quarkus.http.port=${PORT:8080}
```

```env
# docker-compose .env — NO commitear con valores reales
DATABASE_URL=jdbc:postgresql://postgres:5432/visordocs
DB_USER=visordocs
DB_PASSWORD=visordocs_secret
ELASTICSEARCH_URL=elasticsearch:9200
ELASTICSEARCH_INDEX=documents
UPLOAD_DIR=/app/uploads
MAX_FILE_SIZE_MB=10
PORT=8080
```

---

## 8. Docker Compose — servicios requeridos

```yaml
version: "3.9"

services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: visordocs
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER}"]
      interval: 10s
      timeout: 5s
      retries: 5

  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.13.0
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
    ports:
      - "9200:9200"
    volumes:
      - es_data:/usr/share/elasticsearch/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9200/_cluster/health"]
      interval: 20s
      timeout: 10s
      retries: 5

  backend:
    build:
      context: ./backend
      dockerfile: src/main/docker/Dockerfile.jvm
    depends_on:
      postgres:
        condition: service_healthy
      elasticsearch:
        condition: service_healthy
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/visordocs
      DB_USER: ${DB_USER}
      DB_PASSWORD: ${DB_PASSWORD}
      ELASTICSEARCH_URL: elasticsearch:9200
      ELASTICSEARCH_INDEX: ${ELASTICSEARCH_INDEX}
      UPLOAD_DIR: /app/uploads
      MAX_FILE_SIZE_MB: ${MAX_FILE_SIZE_MB}
      PORT: 8080
    ports:
      - "8080:8080"
    volumes:
      - uploads_data:/app/uploads

  frontend:
    build:
      context: ./frontend
    depends_on:
      - backend
    ports:
      - "4200:80"

volumes:
  postgres_data:
  es_data:
  uploads_data:
```

---

## 9. Elasticsearch — Configuración del índice

### Mapping del índice `documents`

```json
{
  "mappings": {
    "properties": {
      "documentId":  { "type": "keyword" },
      "title":       { "type": "text", "analyzer": "standard" },
      "author":      { "type": "text", "analyzer": "standard" },
      "category":    { "type": "keyword" },
      "tags":        { "type": "keyword" },
      "version":     { "type": "keyword" },
      "content":     { "type": "text", "analyzer": "standard" }
    }
  }
}
```

### Query Full-Text con highlighting

```json
{
  "query": {
    "multi_match": {
      "query": "<termino>",
      "fields": ["title^3", "author^2", "content", "tags"]
    }
  },
  "highlight": {
    "pre_tags": ["<mark>"],
    "post_tags": ["</mark>"],
    "fields": {
      "title":   { "number_of_fragments": 1 },
      "content": { "fragment_size": 150, "number_of_fragments": 3 }
    }
  },
  "from": 0,
  "size": 20
}
```

**NUNCA usar SQL `LIKE`. Toda busqueda de texto pasa por Elasticsearch.**

---

## 10. Flujo de procesamiento asíncrono

```
POST /api/documents
        |
        v
  Validar archivo (extension, MIME, tamano)
        |
        v
  Guardar archivo en disco (uploads/)
        |
        v
  Persistir Document{status=PROCESSING} en PostgreSQL
        |
        v
  Retornar 202 { documentId, status: "PROCESSING" }
        |
        v  (asincrono — fuera del hilo de request)
  DocumentProcessingJob:
    1. Extraer texto (segun fileType: pdf-parse / fs.read / md→text)
    2. Normalizar contenido
    3. Indexar en Elasticsearch
    4. Actualizar status → INDEXED  (o ERROR si falla)
    5. Emitir evento SSE al cliente
```

### Implementación asíncrona en Quarkus

```java
// Publicar evento en el EventBus para procesamiento asincrono
@Inject
EventBus eventBus;

// En el Resource, despues de persistir:
eventBus.publish("document.process", documentId);

// Consumidor asincrono:
@ConsumeEvent("document.process")
@Blocking
public void processDocument(String documentId) {
    documentProcessingService.process(documentId);
}
```

---

## 11. Vistas del Frontend Angular

### 11.1 Pagina de Carga (`/upload`)

- Formulario reactivo con campos: titulo, autor, categoria, etiquetas, version
- Input de archivo con drag-and-drop
- Validacion client-side (extension, tamano max)
- Spinner de estado tras el envio
- Toast reactivo via SSE cuando cambia a `INDEXED` o `ERROR`

### 11.2 Pagina de Busqueda (`/search` — home)

- Input de busqueda con debounce (300ms)
- Lista de resultados con fragmentos destacados (`<mark>`)
- Paginacion (pagina anterior / siguiente / pagina actual / total)
- Click en resultado navega al visor

### 11.3 Visor de Documento (`/documents/:id`)

- Panel izquierdo: metadatos (titulo, autor, categoria, tags, version, estado, fecha)
- Panel derecho: contenido renderizado
  - TXT: `<pre>` con scroll
  - Markdown: `ngx-markdown` o equivalente Angular
  - PDF: `ngx-extended-pdf-viewer` o iframe con blob URL

### 11.4 Estado en tiempo real (SSE Service)

```typescript
// core/services/sse.service.ts
observeDocument(documentId: string): Observable<DocumentStatusEvent> {
  return new Observable(observer => {
    const source = new EventSource(`/api/events?documentId=${documentId}`);
    source.onmessage = (e) => observer.next(JSON.parse(e.data));
    source.onerror = (e) => observer.error(e);
    return () => source.close();
  });
}
```

---

## 12. Manejo de errores

### Tipos de error del backend

| Codigo | Situacion |
|---|---|
| `VALIDATION_ERROR` | Campos requeridos faltantes o invalidos |
| `UNSUPPORTED_FILE_TYPE` | Extension no permitida |
| `FILE_SIZE_EXCEEDED` | Archivo supera el limite configurado |
| `DOCUMENT_NOT_FOUND` | ID no existe en base de datos |
| `TEXT_EXTRACTION_ERROR` | Fallo al leer o parsear el archivo |
| `INDEXING_ERROR` | Fallo al indexar en Elasticsearch |
| `SEARCH_ERROR` | Fallo en consulta Elasticsearch |
| `INTERNAL_SERVER_ERROR` | Error inesperado |

### Implementacion en Quarkus

```java
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<AppException> {
    @Override
    public Response toResponse(AppException e) {
        ErrorResponse body = new ErrorResponse(
            e.getCode(),
            e.getMessage(),
            UUID.randomUUID().toString()
        );
        return Response.status(e.getHttpStatus()).entity(body).build();
    }
}
```

---

## 13. Pruebas requeridas

### Unitarias (JUnit 5 + Mockito)

Cubrir obligatoriamente:

- `FileValidatorService` — extension, MIME type, tamano
- `TextExtractorService` — extraccion TXT, PDF, MD
- `DocumentStatusService` — transiciones de estado validas e invalidas
- `SearchQueryBuilder` — construccion de queries ES
- `DocumentMapper` — mapeo entidad <-> DTO

### Integracion (Quarkus `@QuarkusTest` + Testcontainers)

Cubrir obligatoriamente:

- `POST /api/documents` → persiste con status `PROCESSING`
- Job de procesamiento → cambia a `INDEXED` despues de indexar
- `GET /api/documents/search?q=...` → retorna resultados con highlight
- Evento SSE emitido al completar procesamiento

### Benchmark de rendimiento (k6)

```javascript
// k6 scenario: 10 VUs, 60 segundos
// Objetivo: p95 < 1000ms en GET /api/documents/search
import http from 'k6/http';
import { check } from 'k6';

export const options = { vus: 10, duration: '60s' };

export default function () {
  const res = http.get('http://localhost:8080/api/documents/search?q=arquitectura');
  check(res, {
    'status is 200': (r) => r.status === 200,
    'duration < 1000ms': (r) => r.timings.duration < 1000,
  });
}
```

Documentar en `docs/architecture.md`:
- Dataset: numero de documentos indexados
- Hardware: CPU/RAM del equipo de prueba
- Version Elasticsearch utilizada
- Resultado: latencia promedio, p50, p95, p99

**Cobertura objetivo: >= 80%**

---

## 14. Principios de desarrollo para el agente

1. **Inspecciona antes de modificar**: revisa la estructura existente antes de crear o cambiar archivos.
2. **Cambios incrementales y verificables**: no reescribas bloques grandes de codigo de una vez.
3. **Contratos primero**: define interfaces/DTOs antes de implementar.
4. **Mantén compatibilidad**: no cambies contratos de API sin actualizar frontend y documentacion.
5. **Prueba junto con la funcionalidad**: no dejes las pruebas para el final de cada fase.
6. **Documenta decisiones**: cuando tomes una decision no especificada, registrala en `docs/architecture.md`.
7. **No inventar requisitos**: si algo no esta definido en los requerimientos, proposlo explicitamente y documenta el supuesto.
8. **Verifica compilacion y tests**: despues de cada cambio confirma que el codigo compila y los tests pasan.

---

## 15. Reporte por tarea

Al completar cada tarea, informa:

```
Requisitos atendidos: [RF-001, HU-01, ...]
Archivos creados/modificados: [lista]
Decision tecnica: [si aplica]
Pruebas ejecutadas: [comando + resultado]
Riesgos o pendientes: [si los hay]
```

---

## 16. Plan de fases de implementacion

| Fase | Descripcion | Requisitos cubiertos |
|---|---|---|
| **F1** | Skeleton: repo, Docker Compose, init Quarkus, init Angular, shared models | RNF-005 |
| **F2** | Persistencia: entidad Document, migraciones, POST /documents, validaciones | RF-001, HU-01 |
| **F3** | Procesamiento asincrono: job, extractores TXT/PDF/MD, estados | RF-002 |
| **F4** | Motor de busqueda: indice ES, GET /search, highlighting, paginacion | RF-003, RF-004, HU-02 |
| **F5** | Frontend: upload, search, viewer | RF-005, HU-03 |
| **F6** | Tiempo real: SSE backend + Angular SSE service | RF-006, HU-04 |
| **F7** | Calidad: unit tests, integration tests, benchmark | RNF-001, RNF-004 |
| **F8** | Documentacion: architecture.md, ia.md, README | RNF-003 |

---

## 17. Checklist de Definition of Done

### Backend

- [ ] POST /api/documents responde con 202 { documentId, status: "PROCESSING" } inmediatamente
- [ ] Formatos TXT, PDF, Markdown soportados y extraidos correctamente
- [ ] Metadatos (title, author, category, tags, version) validados
- [ ] Procesamiento asincrono sin bloquear el hilo de request
- [ ] Estado INDEXED al terminar exitosamente
- [ ] Estado ERROR al fallar el procesamiento
- [ ] Busqueda Full-Text via Elasticsearch (sin LIKE)
- [ ] Highlighting en resultados de busqueda
- [ ] Paginacion en resultados
- [ ] Eventos SSE emitidos al cambiar estado
- [ ] Manejo estructurado de errores con codigos
- [ ] Variables de entorno para toda configuracion sensible
- [ ] Unit tests >= 80% cobertura
- [ ] Integration tests pasan

### Frontend (Angular)

- [ ] Formulario de carga con validacion client-side
- [ ] Envio multipart al backend
- [ ] Vista de busqueda con input, resultados y paginacion
- [ ] Highlighting visible en fragmentos (mark HTML)
- [ ] Visor renderiza TXT, Markdown y PDF sin descargar
- [ ] Metadatos visibles en el visor
- [ ] Estado se actualiza reactivamente via SSE (sin polling)
- [ ] Toast/notificacion cuando cambia a INDEXED o ERROR

### Infraestructura

- [ ] docker-compose up levanta PostgreSQL, Elasticsearch, backend y frontend
- [ ] .env.example con todas las variables documentadas
- [ ] Sin secretos hardcodeados en codigo o docker-compose

### Documentacion

- [ ] docs/architecture.md con diagrama y justificaciones
- [ ] docs/ia.md con herramientas, prompts y validacion humana
- [ ] README.md con instrucciones de setup, ejecucion y variables de entorno

---

*Generado a partir de `KATA_29_sep_requerimientos_LLM.md` | Fecha: 2026-09-29*
