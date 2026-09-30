# VisorDocsKATA

Visor de documentos técnicos

## Descripción

Aplicación web para cargar documentos técnicos (TXT, PDF, Markdown) con metadatos, procesarlos e indexarlos de forma asíncrona, buscarlos con Full-Text Search de alto rendimiento, visualizarlos sin descargar el archivo, y actualizar el estado en tiempo real sin hacer polling.

## Estado

**IN PROGRESS** – Fase 7 (Calidad: unit tests, integration tests, benchmark) en curso; Fase 8 (Documentación: README pendiente) ahora completada.

## Stack Tecnológico

| Capa | Tecnología | Versión |
|------|------------|---------|
| Backend | Quarkus (Java) | 3.x LTS |
| Frontend | Angular | 17+ |
| API | REST (JAX‑RS / RESTEasy Reactive) | — |
| Base de datos | PostgreSQL | 16 |
| Motor de búsqueda | Elasticsearch | 8.x |
| Comunicación en tiempo real | Server‑Sent Events (SSE) – Quarkus Reactive Streams | — |
| Procesamiento asíncrono | Quarkus EventBus + workers @Blocking | — |
| ORM | Hibernate ORM con Panache | — |
| Contenedores | Docker + Docker Compose | — |
| Arquitectura | Clean Architecture (dominio / aplicación / infraestructura / interfaces) | — |

## Arquitectura

El proyecto sigue una arquitectura limpia:

- **Dominio**: Entidades (`Document`, `DocumentStatus`) y reglas de negocio.
- **Aplicación**: Casos de uso (`UploadDocumentUseCase`, `ProcessDocumentUseCase`, `SearchDocumentsUseCase`).
- **Infraestructura**: Implementaciones de repositorios (Panache), cliente de Elasticsearch, extractores de texto, servicio SSE.
- **Interfaz**: Recursos REST (DTOs, mappers) y controladores Angular.

Ver `docs/architecture.md` para diagramas y justificaciones detalladas.

## Cómo ejecutar

### Prerrequisitos

- Docker y Docker Compose
- Java 21 (para construir el backend localmente)
- Node.js y Angular CLI (para el frontend)

### Paso a paso

1. **Clonar el repositorio**
   ```bash
   git clone <repository-url>
   cd VisorDocsKATA
   ```

2. **Levantar la infraestructura con Docker Compose**
   ```bash
   docker-compose up -d
   ```
   Esto iniciará PostgreSQL, Elasticsearch, el backend y el frontend.

3. **Verificar que los servicios estén saludables**
   - Backend: http://localhost:8080
   - Frontend: http://localhost:4200
   - Elasticsearch: http://localhost:9200
   - PostgreSQL: puerto 5432

4. **Ejecutar pruebas**
   ```bash
   # Backend unit tests
   cd backend
   mvn test
   # Frontend unit tests (si existen)
   cd ../frontend
   npm test
   ```

5. **Benchmark de rendimiento (k6)**
   ```bash
   # Asumiendo que k6 está instalado
   k6 run --vus 10 --duration 60s ./k6/search-test.js
   ```

## Endpoints API

### POST /api/documents
- **Content-Type**: `multipart/form-data`
- **Partes**: `file`, `title`, `author`, `category`, `tags` (separado por comas), `version`
- **Respuesta** (202 Accepted):
  ```json
  {
    "documentId": "uuid",
    "status": "PROCESSING"
  }
  ```

### GET /api/documents/{id}
- **Respuesta** (200 OK):
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
    "content": "Contenido extraído...",
    "originalFileName": "...",
    "fileType": "pdf",
    "createdAt": "...",
    "updatedAt": "..."
  }
  ```

### GET /api/documents/search?q={query}&page={0}&pageSize={20}
- **Respuesta** (200 OK):
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
- Eventos emitidos al cambiar estado:
  ```json
  { "documentId": "uuid", "status": "INDEXED" }
  { "documentId": "uuid", "status": "ERROR", "errorMessage": "..." }
  ```

## Documentación adicional

- `docs/architecture.md` – Diagrama de componentes y decisiones de diseño.
- `docs/ia.md` – Uso de IA en el proyecto (prompts, validación humana).

## Licencia

Este proyecto está bajo la licencia MIT – ver archivo `LICENSE` para más detalles.