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
    %% DOMAIN LAYER
    class Document {
        +String id
        +String title
        +String author
        +String category
        +String[] tags
        +String version
        +String originalFileName
        +String fileType
        +Long fileSize
        +String content
        +DocumentStatus status
        +String errorMessage
        +Instant createdAt
        +Instant updatedAt
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
        +getCode() String
        +getHttpStatus() int
        +validationError(String) AppException
        +unsupportedFileType(String) AppException
        +fileSizeExceeded(long) AppException
        +documentNotFound(String) AppException
        +textExtractionError(String, Throwable) AppException
        +indexingError(String, Throwable) AppException
        +searchError(String, Throwable) AppException
        +internalError(String, Throwable) AppException
    }
    
    Document --> DocumentStatus : status
    
    %% APPLICATION LAYER
    class DocumentProcessingJob {
        -DocumentRepository documentRepository
        -TextExtractorService textExtractor
        -ElasticsearchService elasticsearchService
        -SseService sseService
        -String uploadDir
        +processDocument(String) void
    }
    
    %% INFRASTRUCTURE - PERSISTENCE
    class DocumentRepository {
        <<interface>>
        +persist(Document) void
        +findById(String) Document
        +flush() void
        +listAll() List~Document~
    }
    
    class PanacheRepositoryBase {
        <<Panache Repository>>
    }
    
    DocumentRepository ..|> PanacheRepositoryBase : implements
    
    %% INFRASTRUCTURE - SEARCH
    class ElasticsearchService {
        -ObjectMapper objectMapper
        -String indexName
        -RestClient lowLevelClient
        -ExecutorService executor
        -ScheduledExecutorService scheduler
        +onStartup(StartupEvent) void
        +ensureIndexExists() void
        +indexDocument(String, String, String, String, String[], String, String) void
        +search(String, int, int) SearchResponse
        -ensureIndexExistsWithRetry() void
        -createIndex() void
        -indexExists() boolean
        -parseSearchResponse(JsonNode, int, int) SearchResponse
        -executeWithRetry(Supplier~T~) T
        -parseHosts(String) List~HttpHost~
        -parseHost(String) HttpHost
        +close() void
    }
    
    %% INFRASTRUCTURE - EXTRACTION
    class TextExtractorService {
        +extract(Path, String) String
        -extractTxt(Path) String
        -extractPdf(Path) String
        -extractMarkdown(Path) String
    }
    
    %% INFRASTRUCTURE - SSE
    class SseService {
        -BroadcastProcessor~DocumentStatusEvent~ processor
        +emitEvent(DocumentStatusEvent) void
        +subscribe(String) Multi~DocumentStatusEvent~
    }
    
    %% INTERFACES - DTOs
    class UploadResponse {
        +String documentId
        +String status
    }
    
    class DocumentDetailResponse {
        +String documentId
        +String status
        +DocumentMetadata metadata
        +String content
        +String originalFileName
        +String fileType
        +Instant createdAt
        +Instant updatedAt
        +String errorMessage
    }
    
    class DocumentMetadata {
        +String title
        +String author
        +String category
        +String[] tags
        +String version
    }
    
    class SearchResponse {
        +List~SearchResultItem~ items
        +int page
        +int pageSize
        +long total
    }
    
    class SearchResultItem {
        +String documentId
        +String title
        +SearchMetadata metadata
        +List~String~ highlight
    }
    
    class SearchMetadata {
        +String author
        +String category
        +String[] tags
        +String version
    }
    
    class DocumentStatusEvent {
        +String documentId
        +String status
        +String errorMessage
        +indexed(String) DocumentStatusEvent
        +error(String, String) DocumentStatusEvent
    }
    
    class ErrorResponse {
        +Error error
    }
    
    class Error {
        +String code
        +String message
        +String correlationId
    }
    
    %% INTERFACES - RESOURCES
    class DocumentResource {
        -DocumentRepository documentRepository
        -EventBus eventBus
        -String uploadDir
        -long maxSizeMb
        -List~String~ allowedExtensions
        +uploadDocument(FileUpload, String, String, String, String, String) Response
        +getDocument(String) DocumentDetailResponse
        +getDocumentContent(String) Response
    }
    
    class SearchResource {
        -ElasticsearchService elasticsearchService
        +search(String, int, int) SearchResponse
    }
    
    class SseResource {
        -SseService sseService
        +streamEvents(String) Multi~DocumentStatusEvent~
    }
    
    class GlobalExceptionMapper {
        +toResponse(AppException) Response
    }
    
    %% RELATIONS
    DocumentProcessingJob --> DocumentRepository : uses
    DocumentProcessingJob --> TextExtractorService : uses
    DocumentProcessingJob --> ElasticsearchService : uses
    DocumentProcessingJob --> SseService : uses
    
    DocumentResource --> DocumentRepository : uses
    DocumentResource --> EventBus : publishes to
    DocumentResource --> Document : creates
    
    SearchResource --> ElasticsearchService : uses
    
    SseResource --> SseService : uses
    
    GlobalExceptionMapper --> AppException : maps
    
    ElasticsearchService --> AppException : throws
    TextExtractorService --> AppException : throws
```

### 3.2 Diagrama de Secuencia - Carga y Procesamiento

```mermaid
sequenceDiagram
    autonumber
    participant Client as Cliente (Angular)
    participant DocRes as DocumentResource
    participant PG as PostgreSQL
    participant EventBus as Quarkus EventBus
    participant ProcJob as DocumentProcessingJob
    participant Extractor as TextExtractorService
    participant ES as Elasticsearch
    participant SSE as SseService / SseResource
    
    Client->>DocRes: POST /api/documents (multipart)
    DocRes->>DocRes: Validar archivo (ext, MIME, tamaño)
    DocRes->>PG: Persistir Document{status=PROCESSING}
    DocRes->>PG: flush()  %% Forzar visibilidad inmediata
    DocRes->>Disco: Guardar archivo en uploads/{id}
    DocRes->>EventBus: publish("document.process", docId)
    DocRes-->>Client: 202 Accepted {documentId, status: PROCESSING}
    
    par Procesamiento Asíncrono
        EventBus->>ProcJob: @ConsumeEvent("document.process")
        ProcJob->>Extractor: extract(filePath, fileType)
        Extractor-->>ProcJob: contenido extraído (texto plano)
        ProcJob->>ProcJob: Sanitizar null bytes (\u0000)
        ProcJob->>PG: UPDATE Document SET content=..., updatedAt=now()
        ProcJob->>ES: ensureIndexExists() (idempotente)
        ProcJob->>ES: indexDocument(docId, title, author, category, tags, version, content)
        ProcJob->>PG: UPDATE status=INDEXED
        ProcJob->>SSE: emitEvent(DocumentStatusEvent.indexed(docId))
    or Error Handling
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
    Note right of ESSvc: Query DSL:\n{\n  "multi_match": {\n    "query": "arquitectura",\n    "fields": ["title^3", "author^2", "content", "tags"]\n  },\n  "highlight": {\n    "pre_tags": ["<mark>"],\n    "post_tags": ["</mark>"],\n    "fields": {\n      "title": {"number_of_fragments": 1},\n      "content": {"fragment_size": 150, "number_of_fragments": 3}\n    }\n  },\n  "from": 0, "size": 20\n}
    
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
    subgraph APP [App Module - Standalone]
        direction TB
        Routes[Router<br/>provideRouter]
        Http[HttpClient<br/>provideHttpClient(withFetch)]
        ErrorHandler[ErrorHandler<br/>provideBrowserGlobalErrorListeners]
        
        Routes --> SearchFeature
        Routes --> UploadFeature
        Routes --> ViewerFeature
    end
    
    subgraph CORE [Core - Servicios Singleton]
        direction TB
        DocSvc[DocumentService<br/>HTTP /api/documents]
        SearchSvc[SearchService<br/>HTTP /api/documents/search]
        SseSvc[SseService<br/>EventSource /api/events]
        ToastSvc[ToastService<br/>Notificaciones globales]
    end
    
    subgraph MODELS [Core Models - Interfaces TS]
        direction TB
        UploadResp[UploadResponse]
        DocStatusEvt[DocumentStatusEvent]
        DocMeta[DocumentMetadata]
        DocDetail[DocumentDetail]
        SearchItem[SearchItem]
        SearchResp[SearchResponse]
    end
    
    subgraph FEATURES [Feature Modules - Lazy Loaded]
        direction TB
        SearchFeature[SearchComponent<br/>path: '' (home)]
        UploadFeature[UploadComponent<br/>path: 'upload']
        ViewerFeature[ViewerComponent<br/>path: 'documents/:id']
    end
    
    subgraph SHARED [Shared Components]
        direction TB
        ToastComp[ToastComponent<br/>Global notifications]
        NavbarComp[NavbarComponent<br/>Navigation header]
    end
    
    %% Service dependencies
    SearchFeature --> SearchSvc
    SearchFeature --> DocSvc
    SearchFeature --> SseSvc
    SearchFeature --> ToastSvc
    
    UploadFeature --> DocSvc
    UploadFeature --> SseSvc
    UploadFeature --> ToastSvc
    UploadFeature --> Router
    
    ViewerFeature --> DocSvc
    ViewerFeature --> Router
    ViewerFeature --> DomSanitizer
    
    %% Model usage
    DocSvc --> UploadResp
    DocSvc --> DocDetail
    SearchSvc --> SearchResp
    SseSvc --> DocStatusEvt
    UploadFeature --> UploadResp
    UploadFeature --> DocStatusEvt
    SearchFeature --> SearchResp
    SearchFeature --> SearchItem
    ViewerFeature --> DocDetail
    ViewerFeature --> DocStatusEvt
    
    %% Shared
    App --> ToastComp
    App --> NavbarComp
    
    style APP fill:#e8f5e9,stroke:#2e7d32,color:#000
    style CORE fill:#fff3e0,stroke:#ef6c00,color:#000
    style MODELS fill:#fce4ec,stroke:#c2185b,color:#000
    style FEATURES fill:#e3f2fd,stroke:#1565c0,color:#000
    style SHARED fill:#f3e5f5,stroke:#7b1fa2,color:#000
```

### 4.2 Diagrama de Clases - Servicios y Modelos (TypeScript)

```mermaid
classDiagram
    %% CORE MODELS
    class UploadResponse {
        +documentId: string
        +status: string
    }
    
    class DocumentStatusEvent {
        +documentId: string
        +status: 'PROCESSING' | 'INDEXED' | 'ERROR'
        +errorMessage?: string
    }
    
    class DocumentMetadata {
        +title: string
        +author: string
        +category: string
        +tags: string[]
        +version: string
    }
    
    class DocumentDetail {
        +documentId: string
        +status: 'PROCESSING' | 'INDEXED' | 'ERROR'
        +metadata: DocumentMetadata
        +content: string
        +originalFileName: string
        +fileType: string
        +createdAt: string
        +updatedAt: string
        +errorMessage?: string
    }
    
    class SearchItem {
        +documentId: string
        +title: string
        +metadata: DocumentMetadata
        +highlight: string[]
    }
    
    class SearchResponse {
        +items: SearchItem[]
        +page: number
        +pageSize: number
        +total: number
    }
    
    DocumentDetail --> DocumentMetadata : contains
    SearchItem --> DocumentMetadata : contains
    SearchResponse --> SearchItem : contains
    
    %% CORE SERVICES
    class DocumentService {
        -HttpClient http
        -string baseUrl = '/api/documents'
        +uploadDocument(FormData): Observable~UploadResponse~
        +getDocument(string): Observable~DocumentDetail~
    }
    
    class SearchService {
        -HttpClient http
        -string baseUrl = '/api/documents/search'
        +searchDocuments(string, number, number): Observable~SearchResponse~
    }
    
    class SseService {
        +observeDocument(string): Observable~DocumentStatusEvent~
        -EventSource source
    }
    
    class ToastService {
        -signal toasts
        +success(string) void
        +error(string) void
        +info(string) void
        +clear() void
    }
    
    %% FEATURE COMPONENTS
    class UploadComponent {
        -FormBuilder fb
        -DocumentService documentService
        -SseService sseService
        -ToastService toastService
        -Router router
        +FormGroup uploadForm
        +Signal~File~ selectedFile
        +Signal~string~ fileError
        +Signal~boolean~ isUploading
        +Signal~boolean~ isDragging
        +Signal~Status~ uploadStatus
        -Subscription sseSub
        +onDragOver(DragEvent) void
        +onDrop(DragEvent) void
        +onFileSelected(Event) void
        +setFile(File) void
        +removeFile(Event) void
        +onSubmit() void
        -subscribeToSse(string) void
        +getFileIcon(string) string
        +formatSize(number) string
    }
    
    class SearchComponent {
        -SearchService searchService
        -Router router
        -DomSanitizer sanitizer
        +FormControl searchControl
        +Signal~SearchResponse~ response
        +Signal~boolean~ isLoading
        +Signal~string~ error
        +Signal~number~ currentPage
        -Subject~void~ destroy$
        +doSearch(string) void
        +changePage(number) void
        +goToDocument(string) void
        +sanitize(string) SafeHtml
        +safeHighlight(string) SafeHtml
        +ngOnDestroy() void
    }
    
    class ViewerComponent {
        -ActivatedRoute route
        -Router router
        -DocumentService documentService
        -DomSanitizer sanitizer
        +Signal~DocumentDetail~ document
        +Signal~boolean~ isLoading
        +Signal~string~ error
        +Signal~SafeResourceUrl~ pdfUrl
        +Signal~string~ renderedMarkdown
        +ngOnInit() void
        +goBack() void
        +getStatusIcon(string) string
        -renderMarkdown(string) string
    }
    
    class ToastComponent {
        -ToastService toastService
        +Signal~Toast[]~ toasts
    }
    
    class NavbarComponent {
        -Router router
    }
    
    %% SERVICE DEPENDENCIES
    UploadComponent --> DocumentService : uses
    UploadComponent --> SseService : uses
    UploadComponent --> ToastService : uses
    UploadComponent --> Router : navigates
    
    SearchComponent --> SearchService : uses
    SearchComponent --> Router : navigates
    SearchComponent --> DomSanitizer : sanitizes highlights
    
    ViewerComponent --> DocumentService : uses
    ViewerComponent --> Router : navigates
    ViewerComponent --> DomSanitizer : bypassSecurityTrust
    
    ToastComponent --> ToastService : consumes
    
    DocumentService --> HttpClient : injects
    SearchService --> HttpClient : injects
    SseService --> EventSource : native browser API
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
    [*] --> Idle
    
    state UploadComponent {
        Idle --> Validating : onSubmit()
        Validating --> Uploading : form valid + file selected
        Validating --> Idle : form invalid
        
        Uploading --> Processing : 202 response received
        Uploading --> Error : HTTP error
        
        Processing --> Indexed : SSE event status=INDEXED
        Processing --> Error : SSE event status=ERROR
        Processing --> Error : SSE connection error
        
        Indexed --> Navigating : setTimeout 1.5s
        Navigating --> [*] : router.navigate()
        
        Error --> Idle : User dismisses / retries
    }
    
    state SearchComponent {
        [*] --> Empty
        Empty --> Searching : query.length > 0 (debounced)
        Searching --> Results : response received
        Searching --> Error : HTTP error
        Searching --> Empty : query cleared
        
        Results --> Searching : query changes / page changes
        Results --> Empty : query cleared
        Error --> Searching : retry / new query
    }
    
    state ViewerComponent {
        [*] --> Loading : ngOnInit()
        Loading --> Processing : status=PROCESSING
        Loading --> ErrorView : HTTP error / status=ERROR
        Loading --> Rendered : status=INDEXED
        
        Rendered --> [*] : goBack()
        ErrorView --> [*] : goBack()
        Processing --> Rendered : Poll/refresh (manual)
    }
    
    state SseConnection {
        [*] --> Connecting : observeDocument(id)
        Connecting --> Open : EventSource.onopen
        Open --> Receiving : EventSource.onmessage
        Receiving --> Open : Next event
        Open --> Closed : EventSource.onerror / unsubscribe
        Closed --> [*] : source.close()
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