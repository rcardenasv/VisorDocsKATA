# Documentos Técnicos - Arquitectura del Sistema

> **Ubicación:** `docs/architecture.md` | **Finalidad:** Diagrama + justificaciones tecnológicas.
> **Referencia obligatoria:** `AGENTS.md§10`, `AGENTS.md§15`, `AGENTS.md§17`

---

## 1. Visión General

**VisorDocsKATA** es una aplicación web full-stack diseñada para cargar, indexar y buscar documentos técnicos (TXT, PDF, Markdown) con metadatos. La arquitectura sigue los principios de **Clean Architecture** con separación clara entre dominio, aplicación, infraestructura e interfaz.

El sistema permite:
1. Cargar documentos con metadatos (título, autor, categoría, etiquetas, versión)
2. Procesar e indexar documentos de forma asíncrona
3. Buscar utilizando Full-Text Search en Elasticsearch 8
4. Visualizar contenido sin descargar el archivo
5. Actualizar estado en tiempo real usando Server-Sent Events (SSE)

---

## 2. Diagrama de Arquitectura General

```mermaid
graph TD
    subgraph FE [Capa de Presentación - Angular 17+]
        UI[Componentes UI] -->|HTTP REST| API_GW[API Gateway / Nginx]
        UI -->|EventSource| SSE_C[SSE Client]
    end

    subgraph BE [Capa de Backend - Quarkus 3.x]
        API_GW -->|Request| REST[REST Resources]
        
        subgraph APP [Capa de Aplicación]
            REST -->|Invokes| UC[Use Cases / Services]
            UC -->|Publishes| EB[Quarkus EventBus]
        end

        subgraph INF [Capa de Infraestructura]
            UC -->|Panache| PG[(PostgreSQL 16)]
            UC -->|Java Client| ES[(Elasticsearch 8)]
            
            EB -->|Async Event| JOB[DocumentProcessingJob]
            JOB -->|Extract Text| EXT[Extractors: PDF/MD/TXT]
            EXT -->|Index| ES
            EXT -->|Update Status| PG
            JOB -->|Emit Event| SSE_S[SSE Stream Server]
        end
    end

    SSE_S -->|Push Event| SSE_C
    
    style FE fill:#e3f2fd,stroke:#1565c0,color:#000
    style BE fill:#f1f8e9,stroke:#2e7d32,color:#000
    style APP fill:#fffde7,stroke:#fbc02d,color:#000
    style INF fill:#fce4ec,stroke:#c2185b,color:#000
```

---

## 3. Estructura Detallada del Backend (Quarkus)

### 3.1 Diagrama de Clases - Capas Clean Architecture

```mermaid
classDiagram
    %% ==========================================
    %% DOMAIN LAYER
    %% ==========================================
    namespace DOMAIN {
        class Document {
            +String id
            +String title
            +DocumentStatus status
            +String content
            +onPrePersist()
            +onPreUpdate()
        }

        class DocumentStatus {
            <<enumeration>>
            PROCESSING
            INDEXED
            ERROR
        }

        class AppException {
            -String code
            -int httpStatus
            +validationError(String)$ AppException
            +documentNotFound(String)$ AppException
            +indexingError(String, Throwable)$ AppException
        }
    }

    %% ==========================================
    %% APPLICATION LAYER
    %% ==========================================
    namespace APPLICATION {
        class DocumentProcessingJob {
            -DocumentRepository documentRepository
            -TextExtractorService textExtractor
            -ElasticsearchService elasticsearchService
            -SseService sseService
            +processDocument(String documentId) void
        }
    }

    %% ==========================================
    %% INFRASTRUCTURE LAYER
    %% ==========================================
    namespace INFRASTRUCTURE {
        class DocumentRepository {
            <<interface>>
            +persist(Document) void
            +findById(String) Document
            +listAll() List~Document~
        }

        class PanacheRepositoryBase {
            <<framework>>
        }

        class ElasticsearchService {
            +indexDocument(...) void
            +search(String, int, int) SearchResponse
        }

        class TextExtractorService {
            +extract(Path, String) String
        }

        class SseService {
            +emitEvent(DocumentStatusEvent) void
            +subscribe(String) Multi~DocumentStatusEvent~
        }
    }

    %% ==========================================
    %% REST / PRESENTATION LAYER (RESOURCES & DTOs)
    %% ==========================================
    namespace REST_API {
        class DocumentResource {
            +uploadDocument(...) Response
            +getDocument(String) DocumentDetailResponse
        }

        class SearchResource {
            +search(String, int, int) SearchResponse
        }

        class SseResource {
            +streamEvents(String) Multi~DocumentStatusEvent~
        }

        class GlobalExceptionMapper {
            +toResponse(AppException) Response
        }

        class DTOs_and_Events {
            <<Data Transfer Objects>>
            UploadResponse
            DocumentDetailResponse
            SearchResponse
            DocumentStatusEvent
        }
    }

    %% ==========================================
    %% RELATIONSHIPS
    %% ==========================================
    %% Domain relations
    Document --> DocumentStatus : status

    %% Infrastructure implementation
    DocumentRepository ..|> PanacheRepositoryBase : implements

    %% REST to Infrastructure / Application
    DocumentResource ..> DocumentRepository : uses
    DocumentResource ..> Document : creates
    SearchResource ..> ElasticsearchService : uses
    SseResource ..> SseService : uses
    GlobalExceptionMapper ..> AppException : maps

    %% Application / Job Orchestration
    DocumentProcessingJob ..> DocumentRepository : uses
    DocumentProcessingJob ..> TextExtractorService : uses
    DocumentProcessingJob ..> ElasticsearchService : uses
    DocumentProcessingJob ..> SseService : uses

    %% Exception throws
    ElasticsearchService ..> AppException : throws
    TextExtractorService ..> AppException : throws
```

### 3.2 Diagrama de Secuencia - Carga y Procesamiento

```mermaid
sequenceDiagram
    autonumber
    participant Client as Cliente (Angular)
    participant DocRes as DocumentResource
    participant PG as PostgreSQL
    participant Disco as Almacenamiento Local
    participant EventBus as Quarkus EventBus
    participant ProcJob as DocumentProcessingJob
    participant Extractor as TextExtractorService
    participant ES as Elasticsearch
    participant SSE as SseService / SseResource
    
    Client->>DocRes: POST /api/documents (multipart)
    DocRes->>DocRes: Validar archivo (ext, MIME, tamaño)
    DocRes->>PG: Persistir Document{status=PROCESSING}
    DocRes->>PG: flush()
    DocRes->>Disco: Guardar archivo en uploads/{id}
    DocRes->>EventBus: publish("document.process", docId)
    DocRes-->>Client: 202 Accepted {documentId, status: PROCESSING}
    
    EventBus->>ProcJob: @ConsumeEvent("document.process")
    ProcJob->>Extractor: extract(filePath, fileType)
    Extractor-->>ProcJob: contenido extraído (texto plano)
    ProcJob->>ProcJob: Sanitizar null bytes (\u0000)
    
    alt Procesamiento Exitoso
        ProcJob->>PG: UPDATE Document SET content=..., updatedAt=now()
        ProcJob->>ES: ensureIndexExists() (idempotente)
        ProcJob->>ES: indexDocument(docId, title, author, category, tags, version, content)
        ProcJob->>PG: UPDATE status=INDEXED
        ProcJob->>SSE: emitEvent(DocumentStatusEvent.indexed(docId))
    else Error en Procesamiento
        ProcJob->>PG: UPDATE status=ERROR, errorMessage=sanitized
        ProcJob->>SSE: emitEvent(DocumentStatusEvent.error(docId, msg))
    end
    
    SSE-->>Client: EventSource recibe JSON event
    Client->>Client: Toast + navegación a /documents/{id}
```

### 3.3 Diagrama de Secuencia - Búsqueda Full-Text

```mermaid
sequenceDiagram
    autonumber
    participant Client as Cliente (Angular)
    participant SearchRes as SearchResource
    participant ESSvc as ElasticsearchService
    participant ES as Elasticsearch 8
    
    Client->>SearchRes: GET /api/documents/search?q=arquitectura&page=0&pageSize=20
    SearchRes->>ESSvc: search("arquitectura", 0, 20)
    
    ESSvc->>ESSvc: Construir query multi_match + highlighting
    Note right of ESSvc: Query DSL:<br/>multi_match: "arquitectura"<br/>fields: ["title^3", "author^2", "content", "tags"]<br/>highlight: title, content<br/>from: 0, size: 20
    
    ESSvc->>ES: POST /documents/_search (low-level REST client)
    ES-->>ESSvc: JSON response con hits + highlights
    ESSvc->>ESSvc: parseSearchResponse() -> SearchResponse
    ESSvc-->>SearchRes: SearchResponse
    SearchRes-->>Client: 200 OK {items[], page, pageSize, total}
```

### 3.4 Diagrama de Flujo SSE - Tiempo Real

```mermaid
sequenceDiagram
    autonumber
    participant FE as Frontend (Angular)
    participant SSE_Res as SseResource
    participant SSE_Svc as SseService (BroadcastProcessor)
    participant ProcJob as DocumentProcessingJob
    
    FE->>SSE_Res: GET /api/events?documentId=uuid (EventSource)
    SSE_Res->>SSE_Svc: subscribe("uuid")
    SSE_Svc-->>SSE_Res: Multi<DocumentStatusEvent> (filtered)
    SSE_Res-->>FE: text/event-stream (conexión abierta)
    
    Note over ProcJob: Procesamiento termina...
    ProcJob->>SSE_Svc: emitEvent(DocumentStatusEvent.indexed("uuid"))
    SSE_Svc->>SSE_Svc: BroadcastProcessor.onNext(event)
    SSE_Svc-->>SSE_Res: Emite a suscriptores filtrados
    SSE_Res-->>FE: data: {"documentId":"uuid","status":"INDEXED"}\n\n
    
    FE->>FE: Actualizar UI reactivamente (RxJS)
    FE->>FE: Toast success + router.navigate(['/documents', id])
```

---

## 4. Estructura Detallada del Frontend (Angular 17+)

### 4.1 Diagrama de Arquitectura - Capas y Módulos

```mermaid
graph TB
    subgraph CONFIG [Configuración Principal]
        direction TB
        Enrutador["Enrutador Principal<br/>(Router)"]
        ClienteHttp["Cliente HTTP<br/>(HttpClient)"]
    end

    subgraph VISTAS [Vistas y Páginas]
        direction TB
        VistaBusqueda["Vista de Búsqueda<br/>(Inicio)"]
        VistaCarga["Vista de Carga<br/>(Subir documento)"]
        VistaVisor["Visor de Documentos<br/>(Detalle)"]
    end

    subgraph SERVICIOS [Servicios Generales]
        direction TB
        SvcDocumento["Servicio de Documentos"]
        SvcBusqueda["Servicio de Búsqueda"]
        SvcEventos["Servicio de Eventos (SSE)"]
        SvcNotificaciones["Servicio de Notificaciones"]
    end

    subgraph COMPARTIDOS [Componentes Comunes]
        direction TB
        CompBarra["Barra de Navegación"]
        CompToast["Notificaciones Emergentes"]
    end

    subgraph MODELOS [Modelos de Datos]
        DocModelos["Interfaces y DTOs<br/>(Documentos, Búsqueda, Eventos)"]
    end

    %% Navegación
    Enrutador --> VistaBusqueda
    Enrutador --> VistaCarga
    Enrutador --> VistaVisor
    Enrutador --> CompBarra

    %% Relaciones Vistas -> Servicios
    VistaBusqueda --> SvcBusqueda
    VistaBusqueda --> SvcDocumento

    VistaCarga --> SvcDocumento
    VistaCarga --> SvcEventos

    VistaVisor --> SvcDocumento

    %% Servicios -> Modelos
    SERVICIOS --> DocModelos
    COMPARTIDOS --> SvcNotificaciones

    %% Estilos por bloque
    style CONFIG fill:#e8f5e9,stroke:#2e7d32,color:#000
    style VISTAS fill:#e3f2fd,stroke:#1565c0,color:#000
    style SERVICIOS fill:#fff3e0,stroke:#ef6c00,color:#000
    style COMPARTIDOS fill:#f3e5f5,stroke:#7b1fa2,color:#000
    style MODELOS fill:#fce4ec,stroke:#c2185b,color:#000
```

### 4.2 Diagrama de Clases - Servicios y Modelos (TypeScript)

```mermaid
classDiagram
    %% ==========================================
    %% CAPA DE MODELOS / DATOS
    %% ==========================================
    namespace MODELOS {
        class MetadatosDocumento {
            +titulo: string
            +autor: string
            +categoria: string
            +etiquetas: string[]
        }

        class DetalleDocumento {
            +idDocumento: string
            +estado: string
            +contenido: string
            +metadatos: MetadatosDocumento
        }

        class EventoEstado {
            +idDocumento: string
            +estado: string
            +mensajeError: string
        }

        class RespuestaBusqueda {
            +elementos: ItemBusqueda[]
            +total: number
            +pagina: number
        }
    }

    %% ==========================================
    %% CAPA DE SERVICIOS
    %% ==========================================
    namespace SERVICIOS {
        class ServicioDocumentos {
            +subirDocumento(archivo): Observable
            +obtenerDocumento(id): Observable
        }

        class ServicioBusqueda {
            +buscar(termino, pagina): Observable
        }

        class ServicioEventosSSE {
            +escucharEventos(id): Observable
        }

        class ServicioNotificaciones {
            +exito(mensaje)
            +error(mensaje)
        }
    }

    %% ==========================================
    %% CAPA DE COMPONENTES (VISTAS)
    %% ==========================================
    namespace COMPONENTES {
        class ComponenteCarga {
            +formularioCarga: FormGroup
            +subir(): void
        }

        class ComponenteBusqueda {
            +controlBusqueda: FormControl
            +buscar(): void
            +verDocumento(id): void
        }

        class ComponenteVisor {
            +documento: Signal
            +regresar(): void
        }

        class ComponenteNotificaciones {
            +notificaciones: Signal
        }
    }

    %% ==========================================
    %% RELACIONES
    %% ==========================================
    %% Relaciones entre modelos
    DetalleDocumento --> MetadatosDocumento : contiene

    %% Componentes usan Servicios
    ComponenteCarga ..> ServicioDocumentos : usa
    ComponenteCarga ..> ServicioEventosSSE : escucha
    ComponenteCarga ..> ServicioNotificaciones : notifica

    ComponenteBusqueda ..> ServicioBusqueda : usa

    ComponenteVisor ..> ServicioDocumentos : usa

    ComponenteNotificaciones ..> ServicioNotificaciones : consume

    %% Servicios retornan Modelos
    ServicioDocumentos ..> DetalleDocumento : retorna
    ServicioBusqueda ..> RespuestaBusqueda : retorna
    ServicioEventosSSE ..> EventoEstado : emite
```

### 4.3 Diagrama de Secuencia - Flujo de Carga (Upload)

```mermaid
sequenceDiagram
    autonumber
    actor User as Usuario
    participant UploadComp as UploadComponent
    participant DocSvc as DocumentService
    participant SSE as SseService
    participant ToastSvc as ToastService
    participant Router as Angular Router
    participant Backend as Backend API
    
    User->>UploadComp: Arrastra/suelta archivo + completa metadatos
    UploadComp->>UploadComp: Validación client-side (ext, tamaño, required fields)
    
    UploadComp->>DocSvc: uploadDocument(FormData)
    DocSvc->>Backend: POST /api/documents (multipart)
    Backend-->>DocSvc: 202 {documentId, status: "PROCESSING"}
    DocSvc-->>UploadComp: UploadResponse
    
    UploadComp->>UploadComp: isUploading = false
    UploadComp->>UploadComp: uploadStatus = {type: 'processing', ...}
    UploadComp->>SSE: observeDocument(documentId)
    SSE->>Backend: GET /api/events?documentId={id} (EventSource)
    Backend-->>SSE: Conexión SSE abierta
    
    par Espera eventos SSE
        Backend->>Backend: Procesamiento async (EventBus → Job)
        Backend->>SSE: data: {"documentId":"...","status":"INDEXED"}\n\n
        SSE->>UploadComp: Observable.next(DocumentStatusEvent)
        UploadComp->>UploadComp: status = INDEXED
        UploadComp->>ToastSvc: success('¡Documento indexado!')
        UploadComp->>Router: navigate(['/documents', documentId])
    or Error
        Backend->>SSE: data: {"documentId":"...","status":"ERROR","errorMessage":"..."}\n\n
        SSE->>UploadComp: Observable.next(DocumentStatusEvent)
        UploadComp->>UploadComp: status = ERROR
        UploadComp->>ToastSvc: error(errorMessage)
    end
```

### 4.4 Diagrama de Secuencia - Flujo de Búsqueda

```mermaid
sequenceDiagram
    autonumber
    actor User as Usuario
    participant SearchComp as SearchComponent
    participant SearchSvc as SearchService
    participant Backend as Backend API
    participant Router as Angular Router
    
    User->>SearchComp: Escribe en input búsqueda
    SearchComp->>SearchComp: FormControl valueChanges
    SearchComp->>SearchComp: debounceTime(300ms)
    SearchComp->>SearchComp: distinctUntilChanged()
    SearchComp->>SearchComp: currentPage = 0
    SearchComp->>SearchSvc: searchDocuments(query, 0, 20)
    SearchSvc->>Backend: GET /api/documents/search?q=...&page=0&pageSize=20
    Backend-->>SearchSvc: SearchResponse {items[], total, page, pageSize}
    SearchSvc-->>SearchComp: Observable<SearchResponse>
    SearchComp->>SearchComp: response.set(SearchResponse)
    SearchComp->>SearchComp: isLoading = false
    
    User->>SearchComp: Click en resultado
    SearchComp->>Router: navigate(['/documents', documentId])
    
    Note over SearchComp: Paginación
    User->>SearchComp: Click "Siguiente"
    SearchComp->>SearchComp: currentPage++
    SearchComp->>SearchSvc: searchDocuments(query, page, 20)
    SearchSvc->>Backend: GET /api/documents/search?q=...&page=1&pageSize=20
```

### 4.5 Diagrama de Secuencia - Visor de Documentos

```mermaid
sequenceDiagram
    autonumber
    actor User as Usuario
    participant ViewerComp as ViewerComponent
    participant DocSvc as DocumentService
    participant Router as Angular Router
    participant Backend as Backend API
    participant Sanitizer as DomSanitizer
    
    User->>ViewerComp: Navega a /documents/:id
    ViewerComp->>ViewerComp: route.snapshot.paramMap.get('id')
    ViewerComp->>DocSvc: getDocument(id)
    DocSvc->>Backend: GET /api/documents/{id}
    Backend-->>DocSvc: DocumentDetail
    DocSvc-->>ViewerComp: Observable<DocumentDetail>
    ViewerComp->>ViewerComp: document.set(doc), isLoading = false
    
    alt PDF
        ViewerComp->>Sanitizer: bypassSecurityTrustResourceUrl('/api/documents/{id}/content')
        ViewerComp->>ViewerComp: pdfUrl.set(SafeResourceUrl)
        ViewerComp->>ViewerComp: Renderiza <iframe [src]="pdfUrl()">
    else Markdown
        ViewerComp->>ViewerComp: renderMarkdown(content) -> HTML string
        ViewerComp->>ViewerComp: renderedMarkdown.set(html)
        ViewerComp->>ViewerComp: Renderiza <article [innerHTML]="renderedMarkdown()">
    else TXT
        ViewerComp->>ViewerComp: Renderiza <pre>{{ document().content }}</pre>
    end
    
    User->>ViewerComp: Click "← Volver"
    ViewerComp->>Router: navigate(['/search'])
```

### 4.6 Diagrama de Estado Reactivo - Signals y RxJS

```mermaid
stateDiagram-v2
    %% ==========================================
    %% COMPONENTE DE CARGA
    %% ==========================================
    state ComponenteCarga {
        [*] --> EnReposo
        
        EnReposo --> Validando : Enviar formulario
        Validando --> EnReposo : Formulario inválido
        Validando --> Subiendo : Formulario válido
        
        Subiendo --> Procesando : Respuesta HTTP 202
        Subiendo --> ErrorCarga : Error HTTP
        
        Procesando --> Indexado : Evento SSE (INDEXADO)
        Procesando --> ErrorCarga : Evento SSE (ERROR) / Fallo red
        
        Indexado --> Navegando : Esperar 1.5s
        Navegando --> [*] : Redirigir a detalle
        
        ErrorCarga --> EnReposo : Reintentar / Descartar
    }

    %% ==========================================
    %% COMPONENTE DE BÚSQUEDA
    %% ==========================================
    state ComponenteBusqueda {
        [*] --> Vacio
        
        Vacio --> Buscando : Ingresar texto
        Buscando --> Vacio : Limpiar búsqueda
        Buscando --> ErrorBusqueda : Error de red
        Buscando --> ConResultados : Respuesta recibida
        
        ConResultados --> Buscando : Cambiar texto o página
        ConResultados --> Vacio : Limpiar búsqueda
        
        ErrorBusqueda --> Buscando : Reintentar / Nueva búsqueda
    }

    %% ==========================================
    %% COMPONENTE VISOR
    %% ==========================================
    state ComponenteVisor {
        [*] --> CargandoVista
        
        CargandoVista --> EnProceso : Estado PROCESSING
        CargandoVista --> Renderizado : Estado INDEXED
        CargandoVista --> ErrorVisor : Error HTTP o ERROR
        
        EnProceso --> Renderizado : Actualización manual
        
        Renderizado --> [*] : Regresar
        ErrorVisor --> [*] : Regresar
    }

    %% ==========================================
    %% CONEXIÓN EVENTOS SSE
    %% ==========================================
    state ConexionEventos {
        [*] --> Conectando : Iniciar escucha
        Conectando --> Abierta : Conexión exitosa
        Abierta --> Recibiendo : Mensaje recibido
        Recibiendo --> Abierta : Esperar siguiente evento
        Abierta --> Cerrada : Error / Cancelar suscripción
        Cerrada --> [*] : Cerrar canal
    }
```

---

## 5. Stack Tecnológico y Justificaciones

| Capa | Tecnología | Versión | Justificación |
|------|------------|---------|---------------|
| **Backend** | **Quarkus** | 3.x LTS | Arranque ultrarrápido, reactive nativo, Hibernate Panache simplifica repositorios, soporte nativo SSE con `@RestStreamElementType` |
| **Frontend** | **Angular** | 17+ | Framework opinionado, TypeScript nativo, RxJS para SSE y state reactivo, integración fácil con HTTP y EventSource |
| **API** | **REST (JAX-RS / RESTEasy Reactive)** | — | Contratos claros, validated DTOs, error handling unificado (RFC 7807) |
| **Base de datos** | **PostgreSQL** | 16 | Relacional confiable, Persistence con Hibernate Panache, UUIDs nativos |
| **Motor búsqueda** | **Elasticsearch 8.x** | 8.x | Full-Text Search maduro, highlighting nativo, multi-field query, cliente Java oficial |
| **Tiempo real** | **SSE (Server-Sent Events)** | — | Patrón unidireccional (servidor→cliente), más liviano que WebSocket para este caso |
| **Cola asíncrona** | **Quarkus EventBus + @Blocking workers** | — | Procesamiento fuera del hilo de request, no bloquea la API |
| **ORM** | **Hibernate ORM with Panache** | — | Active Record sencillo, menos boilerplate, transacciones automáticas |
| **Contenedores** | **Docker + Docker Compose** | — | Despliegue reproducible, todos los servicios en contenedores isolados |

## 5.1. Decisiones clave y por qué

- **Quarkus sobre Spring Boot:** Arranque más rápido (~100ms vs ~2s), native memory footprint bajo, reactive por defecto. Ideal para Docker y orquestación.
- **Elasticsearch sobre búsqueda SQL LIKE:** La restricción `AGENTS.md§53` prohíbe `LIKE` no indexado. ES 8 proporciona búsquedaFull-Text indexada, highlighting y respuestas en el rango 400-1000ms objetivo.
- **SSE sobre WebSocket:** El patrón es unidireccional (servidor→cliente notificando estado). SSE es más simple, usa estándar HTTP, y no requiere handshake de WS ni framing binario.
- **Docker Compose sobre k8s:** El objetivo es KATA/learning, no producción a escala. Docker Compose levanta PG, ES y backend en segundos para desarrollo local.
- **Clean Architecture:** Separa preocupaciones (dominio puro sin dependencias externas), facilita tests unitarios con Mockito y tests de integración con Testcontainers.
- **Low-level REST client para ES:** Evita `media_type_header_exception` con ES 8.x al usar cliente de alto nivel (9.x) contra servidor 8.x. Se usa `quarkus-elasticsearch-rest-client` + `elasticsearch-rest-client:8.13.0` para operaciones HEAD/PUT/POST directas.
- **nginx resolver + variable $backend:** Docker DNS resuelve `backend` solo en runtime; variable en `proxy_pass` fuerza resolución dinámica vs cache al inicio.
- **nginx location order:** `/api/` y `/api/events` antes de `location /` evita que `try_files` intercepte rutas API.
- **flush() tras persist:** Garantiza visibilidad del documento en BD antes de publicar evento EventBus para procesamiento asíncrono.
- **Sanitización null bytes (`\u0000`):** PostgreSQL UTF-8 rechaza bytes nulos; se eliminan en extracción y mensajes de error.
- **Encoding fallback UTF-8 → ISO-8859-1:** Archivos TXT legacy pueden no ser UTF-8; fallback evita `MalformedInputException`.

---

## 6. Flujo de Datos Completo

```mermaid
sequenceDiagram
    participant User as Usuario
    participant FE as Frontend (Angular)
    participant BE as Backend (Quarkus)
    participant PG as PostgreSQL
    participant ES as Elasticsearch
    participant SSE as SSE EventStream

    User->>FE: Cargar archivo (.txt, .pdf, .md)
    FE->>BE: POST /api/documents (multipart/form-data)
    BE->>PG: Persistir Document{status=PROCESSING}
    BE->>ES: Indexar contenido (async via EventBus)
    BE-->>FE: 202 { documentId, status: PROCESSING }
    FE->>SSE: GET /api/events?documentId={id}
    Note over BE: DocumentProcessingJob:
    BE->>ES: ensureIndexExists() + index content
    BE->>PG: UPDATE status → INDEXED / ERROR
    BE->>SSE: Publicar evento { documentId, status }
    SSE-->>FE: JSON event received
    FE->>User: Toast + navegar /documents/:id
```

---

## 7. Decisiones de Diseño Importantes

| Área | Decisión | Alternativa descartada | Motivo |
|------|----------|----------------------|--------|
| **Búsqueda** | ES Full-Text `multi_match` | SQL `LIKE` | Prohibido por `AGENTS.md§53`; no indexado, lento, sin highlighting |
| **Estado POST** | Responder 202 {documentId, PROCESSING} inmediatamente | Esperar procesamiento completo | `AGENTS.md§54`: "respuesta inmediata en carga antes de que termine el procesamiento" |
| **Comunicación tiempo real** | SSE | WebSocket | `AGENTS.md§44`: SSE "suficiente y más liviano que WebSocket para este caso"; unidireccional basta |
| **Persistencia** | Hibernate Panache + UUID | JPA nativo + secuencias | `AGENTS.md§127-171`: UUID nativo, menos boilerplate, migraciones automáticas |
| **Indexado ES** | `ensureIndexExists()` al iniciar | Indexar solo después de primer doc | Evita fallos en el primer documento; creación idempotente del índice |
| **Configuración sensible** | Variables de entorno (`${VAR:default}`) | Hardcodeados | `AGENTS.md§56`: "Sin secretos hardcodeados" |
| **Errores backend** | Respuesta JSON unificada con `code`/`message`/`correlationId` | Stack traces en producción | Mejor DX para consumidores, rastreo distribuido |

---

## 8. Consideraciones de Rendimiento

- **Objetivo búsqueda:** p95 < 1000 ms en `GET /api/documents/search?q=...` (RF-003)
- **Latencia objetivo:** 400 ms – 1000 ms (RF-003§3)
- **Factores que afectan:**
  - Tamaño del índice ES (shards, replicas)
  - Cantidad de documentos indexados
  - Configuración `quarkus.elasticsearch.java-client.compatibility-mode` (actualmente `false` para evitar error de headers HTTP)
  - Retry logic en `onStartup()` (3 intentos, 2s delay) para no bloquear arranque

---

## 9. Estado de Implementación (Checklist)

| Componente | Estado | Detalles |
|------------|--------|----------|
| **Backend Quarkus** | ✅ Completado | Compila, tests pasan, endpoints operativos |
| **Frontend Angular** | ✅ Completado | Servido por nginx, proxy API funcional |
| **PostgreSQL** | ✅ Completado | Persistencia con Panache, UUIDs, flush() |
| **Elasticsearch 8.x** | ✅ Completado | Low-level REST client, index auto-create, search + highlight |
| **Async Processing** | ✅ Completado | EventBus + @Blocking, TXT/PDF/MD, encoding fallback, sanitización |
| **SSE Real-time** | ✅ Completado | EventSource nativo, toast + navegación auto |
| **Docker Compose** | ✅ Completado | 4 servicios (PG, ES, Backend, Frontend) healthy |
| **Tests** | ✅ Completado | Unitarias (9 ES tests) + Integración (@QuarkusTest) |
| **Documentación** | ⚠️ Parcial | architecture.md, ia.md ✅; README pendiente |
| **Benchmark k6** | ✅ Completado | p95 = 23.26ms (Objetivo < 1000ms) |

---

## 10. Próximos Pasos de Arquitectura

- [x] Implementar benchmark k6 y medir latencias de búsqueda (objetivo p95 < 1000ms) ✅ (p95 = 23.26ms)
2. [ ] Redactar README con instrucciones de setup, ejecución y variables de entorno
3. [ ] (Opcional) Añadir métricas Prometheus/Grafana para observabilidad
4. [ ] (Opcional) Implementar rate limiting y autenticación JWT

---

*Generado a partir de `KATA_29_sep_requerimientos_LLM.md` y `AGENTS.md` | Versión: 1.0 | Fecha: 2026-09-29*