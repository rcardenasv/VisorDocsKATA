# KATA 29 Septiembre — Buscador y Visor de Documentos Técnicos

## 1. Propósito

Este documento transforma la prueba técnica **“Buscador y Visor de Documentos Técnicos”** en una base de requerimientos estructurada para utilizarse como contexto de desarrollo con un **LLM (Large Language Model)**.

La fuente base establece como objetivo evaluar diseño de arquitectura, desarrollo backend/frontend, optimización de búsquedas, comunicación en tiempo real o asíncrona y buenas prácticas de código para un perfil **Full Stack Senior**. La solución debe permitir carga, procesamiento, indexación, búsqueda de alto rendimiento y visualización de documentos técnicos.

> **Fuente:** KATA 29 sept_Buscador y Visor Documentos Técnicos.pdf. Las historias de usuario y criterios de aceptación se encuentran principalmente en las páginas 1 a 3; los requisitos técnicos y de arquitectura en las páginas 3 y 4; y el checklist de evaluación en la página 5.

---

## 2. Objetivo funcional

Construir una aplicación web que permita:

1. Cargar uno o múltiples documentos técnicos.
2. Asociar metadatos a cada documento.
3. Procesar el contenido textual de los documentos.
4. Indexar el contenido para búsquedas Full-Text de alto rendimiento.
5. Buscar por términos o frases sobre título, metadatos y contenido.
6. Mostrar resultados con paginación y resaltado de fragmentos coincidentes.
7. Visualizar el documento y sus metadatos sin necesidad de descargar el archivo.
8. Actualizar automáticamente el estado del documento mediante comunicación en tiempo real cuando termine su procesamiento.
9. Manejar errores, validaciones y configuración mediante buenas prácticas de arquitectura.

---

## 3. Alcance

### Incluido

- Frontend web.
- Backend/API.
- Recepción individual y masiva de documentos.
- Formatos de archivo:
  - TXT
  - PDF
  - Markdown
- Metadatos:
  - título
  - autor
  - categoría
  - etiquetas
  - versión
- Procesamiento e indexación de contenido.
- Motor de búsqueda especializado, **sin utilizar `LIKE` ni equivalentes no indexados**.
- Búsqueda Full-Text.
- Paginación de resultados.
- Highlighting de fragmentos coincidentes.
- Visor de documentos.
- Estado de procesamiento.
- Notificación en tiempo real.
- Pruebas unitarias/integración para componentes críticos del backend.
- Documentación de arquitectura.
- Documentación del uso de IA.

### Fuera de alcance explícito

La prueba no especifica, por sí misma:

- Autenticación/autorización.
- Roles y permisos detallados.
- Edición del contenido del documento.
- Eliminación de documentos.
- Versionamiento físico de archivos.
- OCR para PDFs escaneados.
- Almacenamiento de archivos en una nube específica.
- Diseño visual corporativo específico.
- Despliegue productivo en un proveedor concreto.

Estas capacidades no deben asumirse como obligatorias salvo que sean necesarias para una implementación técnica concreta y se documenten como decisiones adicionales.

---

# 4. Requisitos funcionales

## RF-001 — Carga de documentos

El sistema debe permitir la carga de documentos técnicos de forma individual o masiva.

### Formatos soportados

- `.txt`
- `.pdf`
- `.md`

### Metadatos requeridos

- `title`
- `author`
- `category`
- `tags`
- `version`

### Reglas

- Validar extensión/formato.
- Validar tamaño máximo configurado.
- Rechazar archivos no soportados.
- Rechazar payloads inválidos.
- La recepción debe ser asíncrona.

### Resultado esperado

La respuesta inicial debe ser inmediata y retornar un identificador de seguimiento.

Estado inicial:

```text
PROCESSING
```

Ejemplo conceptual:

```json
{
  "documentId": "uuid",
  "status": "PROCESSING"
}
```

> El esquema exacto del JSON no está definido por la prueba; este ejemplo es una propuesta de implementación para orientar el desarrollo.

---

## RF-002 — Procesamiento e indexación

Después de recibir el documento, el backend debe:

1. Validar y persistir la información necesaria.
2. Extraer el contenido textual.
3. Procesar el contenido.
4. Indexar el documento y los campos consultables.
5. Cambiar el estado a `INDEXED` cuando finalice correctamente.
6. Cambiar el estado a `ERROR` cuando ocurra un fallo de procesamiento.

### Flujo conceptual

```text
UPLOAD
  |
  v
PROCESSING
  |
  +----> ERROR
  |
  v
TEXT EXTRACTION
  |
  v
INDEXING
  |
  v
INDEXED
```

---

## RF-003 — Búsqueda avanzada

El usuario debe poder buscar documentos mediante:

- términos clave;
- frases;
- título;
- metadatos;
- contenido textual.

### Restricción crítica

**Está prohibido implementar la búsqueda mediante consultas SQL simples con `LIKE` o equivalentes no indexados.**

La solución debe utilizar un motor de búsqueda apropiado, por ejemplo:

- Elasticsearch
- OpenSearch
- PostgreSQL Full-Text Search con `TSVECTOR`/`GIN`
- RedisSearch
- Meilisearch
- MongoDB Text Index
- otra alternativa técnicamente justificable

### Rendimiento

La búsqueda debe ejecutarse dentro de un rango objetivo de:

```text
400 ms — 1000 ms
```

---

## RF-004 — Resultados de búsqueda

Los resultados deben:

- mostrar los documentos coincidentes;
- soportar paginación;
- incluir fragmentos relevantes del contenido;
- resaltar los términos o fragmentos coincidentes;
- permitir seleccionar un resultado para abrir su detalle.

Ejemplo conceptual:

```json
{
  "items": [
    {
      "documentId": "uuid",
      "title": "Manual de Arquitectura",
      "metadata": {
        "author": "Autor",
        "category": "Arquitectura",
        "tags": ["api", "integration"],
        "version": "1.2"
      },
      "highlight": [
        "...fragmento <mark>coincidente</mark>..."
      ]
    }
  ],
  "page": 0,
  "pageSize": 20,
  "total": 150
}
```

> Los nombres del contrato anterior son orientativos y deben consolidarse en el diseño de API.

---

## RF-005 — Visor de documentos

El usuario debe poder seleccionar un documento desde los resultados para visualizar:

- contenido estructurado;
- metadatos completos;
- cuerpo del documento.

### Restricción UX

La información debe poder consultarse **sin necesidad de descargar el archivo**.

El frontend debe ofrecer un renderizado fluido y una presentación clara.

---

## RF-006 — Actualización de estado en tiempo real

La aplicación no debe depender de polling tradicional para conocer cuándo termina el procesamiento.

Debe utilizar uno de estos mecanismos:

- WebSocket;
- Server-Sent Events (SSE);
- GraphQL Subscriptions.

### Comportamiento

Cuando el backend complete el procesamiento:

```text
PROCESSING -> INDEXED
```

o, si ocurre un error:

```text
PROCESSING -> ERROR
```

debe emitirse un evento al frontend.

El frontend actualizará reactivamente el estado del documento sin refrescar manualmente la pantalla.

---

# 5. Historias de usuario

## HU-01 — Carga de documentos con metadatos

**Como:** Administrador / Desarrollador

**Quiero:** subir un documento técnico en formato TXT, PDF o Markdown y adjuntar sus metadatos.

**Para:** incorporarlo a la base de conocimiento del sistema.

### Criterios de aceptación

- Existe un mecanismo REST o GraphQL para recepción de archivos.
- Se soporta carga individual o multipart.
- Se validan formato y tamaño máximo.
- La respuesta inicial es inmediata.
- La respuesta devuelve un ID de seguimiento.
- El estado inicial es `PROCESSING`.

---

## HU-02 — Búsqueda avanzada de documentos

**Como:** Usuario de la plataforma

**Quiero:** buscar mediante términos clave o frases sobre título, metadatos y contenido.

**Para:** localizar rápidamente la documentación requerida.

### Criterios de aceptación

- La búsqueda se ejecuta en un rango de 400 ms a 1000 ms.
- No se utiliza `LIKE` ni una estrategia equivalente no indexada.
- Se utiliza un motor de búsqueda especializado.
- Existe paginación.
- Se resaltan los fragmentos coincidentes.

---

## HU-03 — Visor de documentos y detalle

**Como:** Usuario

**Quiero:** seleccionar un documento de los resultados y visualizar su contenido estructurado y metadatos completos.

**Para:** consultar la información sin descargar el archivo.

### Criterios de aceptación

- Existe una vista de detalle.
- El frontend renderiza el contenido de forma fluida.
- Los metadatos se visualizan claramente.
- El cuerpo del documento es legible.
- La consulta no requiere descargar el archivo como paso de usuario.

---

## HU-04 — Notificaciones e integración en tiempo real

**Como:** Usuario

**Quiero:** recibir una notificación inmediata cuando el estado del documento cambie a `INDEXED` o `ERROR`.

**Para:** saber cuándo el documento está disponible para búsqueda y visualización sin refrescar la pantalla.

### Criterios de aceptación

- Se implementa WebSocket, SSE o GraphQL Subscription.
- El backend emite un evento al completar el procesamiento.
- El frontend recibe el evento.
- La interfaz actualiza el estado de manera reactiva.
- No se utiliza polling tradicional como mecanismo principal de actualización.

---

# 6. Requisitos no funcionales

## RNF-001 — Rendimiento

El motor de búsqueda debe cumplir un objetivo de respuesta de:

```text
400 ms <= respuesta <= 1000 ms
```

La medición debe realizarse bajo condiciones de prueba documentadas.

Se recomienda registrar:

- latencia promedio;
- percentiles;
- cantidad de documentos indexados;
- tamaño de los documentos;
- concurrencia utilizada en la prueba.

> La prueba establece el rango objetivo, pero no define una carga de concurrencia ni tamaño de dataset obligatorios. Estos parámetros deberán fijarse durante la implementación.

---

## RNF-002 — Asincronía

El procesamiento pesado no debe bloquear la respuesta inicial del endpoint de carga.

Arquitectura esperada:

```text
Cliente
   |
   | Upload
   v
API
   |
   | ACK + documentId
   v
Cliente

API
 |
 v
Procesamiento asíncrono
 |
 v
Extracción de texto
 |
 v
Indexación
 |
 v
Evento de estado
 |
 v
Frontend
```

---

## RNF-003 — Arquitectura

La arquitectura queda libre a elección del candidato.

Opciones indicadas en la prueba:

- Hexagonal Architecture;
- Clean Architecture;
- Layered Architecture;
- Domain-Driven Design (DDD).

La decisión debe quedar justificada en `docs/architecture.md`.

---

## RNF-004 — Calidad de código

Debe existir:

- manejo estructurado de errores;
- middleware/interceptores de excepciones;
- configuración mediante variables de entorno;
- pruebas unitarias;
- pruebas de integración para componentes críticos del backend;
- separación clara de responsabilidades.
- pruebas de cobertura mayor o igial a 80%

---

## RNF-005 — Configuración

La configuración del sistema debe gestionarse mediante variables de entorno y no mediante secretos o configuraciones sensibles hardcodeadas.

Ejemplo conceptual:

```env
DATABASE_URL=
SEARCH_URL=
SEARCH_INDEX=
PORT=
REALTIME_TRANSPORT=
MAX_FILE_SIZE=
```

Los nombres definitivos deben ajustarse al stack seleccionado.

---

# 7. Stack tecnológico permitido

## Backend

Debe elegirse una opción:

- Express.js
- NestJS
- FastAPI
- Spring Boot
- Quarkus

## Frontend

Debe elegirse una opción:

- React
- Next.js
- Angular

## Comunicación

Se pueden utilizar:

- REST
- GraphQL
- WebSocket
- SSE
- GraphQL Subscriptions

La prueba permite combinar mecanismos siempre que la solución esté técnicamente justificada.

## Persistencia y búsqueda

La base de datos es de libre elección junto con un motor de búsqueda adecuado.

Alternativas mencionadas en la prueba:

- PostgreSQL Full-Text Search
- Elasticsearch
- OpenSearch
- MongoDB Text Index
- RedisSearch
- Meilisearch

---

# 8. Propuesta de componentes del sistema

> Esta sección es una estructuración técnica derivada de los requisitos de la prueba, no una arquitectura impuesta por el documento original.

```text
                    +-----------------------+
                    |       Frontend        |
                    | React / Next / Angular|
                    +-----------+-----------+
                                |
              +-----------------+-----------------+
              |                                   |
              v                                   v
       +-------------+                    +---------------+
       | Document API|                    | Search API    |
       +------+------+                    +-------+-------+
              |                                   |
              v                                   v
       +-------------+                    +---------------+
       | Persistence |                    | Search Engine |
       |   DB        |                    | ES/OpenSearch |
       +-------------+                    +---------------+
              |
              v
       +------------------+
       | Async Processing |
       | Worker / Queue   |
       +--------+---------+
                |
       +--------+---------+
       | Text Extraction  |
       +--------+---------+
                |
                v
       +------------------+
       | Search Indexing  |
       +--------+---------+
                |
                v
       +------------------+
       | Realtime Events  |
       | WebSocket / SSE  |
       +------------------+
                |
                v
             Frontend
```

---

# 9. Modelo de dominio mínimo

## Document

Campos conceptuales:

```text
Document
├── id
├── title
├── author
├── category
├── tags[]
├── version
├── originalFileName
├── fileType
├── fileSize
├── content
├── status
├── createdAt
├── updatedAt
└── errorMessage?
```

### Estados

```text
PROCESSING
INDEXED
ERROR
```

> El documento original solo exige explícitamente los estados `PROCESSING`, `INDEXED` y `ERROR`. Campos adicionales como `createdAt`, `updatedAt`, `fileSize` o `errorMessage` son propuestas de diseño para soportar trazabilidad y operación.

---

# 10. Contratos de API orientativos

## POST /documents

Objetivo: iniciar la carga de un documento.

### Request

```text
multipart/form-data
```

Partes:

```text
file
title
author
category
tags
version
```

### Response

```json
{
  "documentId": "uuid",
  "status": "PROCESSING"
}
```

---

## GET /documents/{id}

Objetivo: obtener detalle y estado de un documento.

### Response conceptual

```json
{
  "documentId": "uuid",
  "status": "INDEXED",
  "metadata": {
    "title": "Documento técnico",
    "author": "Autor",
    "category": "Arquitectura",
    "tags": ["api", "backend"],
    "version": "1.0"
  },
  "content": "Contenido procesado..."
}
```

---

## GET /documents/search

Objetivo: realizar búsqueda Full-Text.

Parámetros orientativos:

```text
q
page
pageSize
```

### Response conceptual

```json
{
  "items": [],
  "page": 0,
  "pageSize": 20,
  "total": 0
}
```

---

## Canal en tiempo real

Ejemplo conceptual con WebSocket:

```text
document.status.changed
```

Payload:

```json
{
  "documentId": "uuid",
  "status": "INDEXED"
}
```

En caso de error:

```json
{
  "documentId": "uuid",
  "status": "ERROR",
  "errorMessage": "..."
}
```

---

# 11. Flujo funcional completo

## Caso exitoso

```text
1. Usuario selecciona archivo.
2. Frontend agrega metadatos.
3. Frontend envía multipart al backend.
4. Backend valida archivo y metadatos.
5. Backend registra documento.
6. Backend retorna documentId + PROCESSING.
7. Procesador asíncrono extrae texto.
8. Motor de búsqueda indexa contenido/metadatos.
9. Backend cambia estado a INDEXED.
10. Backend emite evento.
11. Frontend recibe evento.
12. UI actualiza el documento sin refresh.
13. Usuario puede buscarlo.
14. Usuario abre el visor.
```

## Caso con error

```text
1. Upload
2. PROCESSING
3. Fallo de extracción/indexación
4. Estado ERROR
5. Emisión de evento
6. Frontend actualiza UI
7. Usuario visualiza el error
```

---

# 12. Requisitos específicos para el motor de búsqueda

## Campos recomendados para indexación

```text
title
author
category
tags
content
version
```

## Capacidades mínimas

- Full-Text Search.
- Búsqueda por frases.
- Búsqueda sobre varios campos.
- Relevancia.
- Highlighting.
- Paginación.
- Índices apropiados.

## Restricción obligatoria

No utilizar:

```sql
WHERE content LIKE '%termino%'
```

ni mecanismos equivalentes que realicen búsquedas completas no indexadas.

---

# 13. Procesamiento asíncrono

El sistema debe separar la recepción de archivos del procesamiento pesado.

## Requisito

La operación de carga debe responder sin esperar a que finalice la indexación.

## Diseño sugerido

```text
API
 |
 | DocumentCreated
 v
Queue / Worker
 |
 +--> Extract text
 |
 +--> Normalize content
 |
 +--> Index document
 |
 +--> Persist status
 |
 +--> Publish event
```

La prueba no exige una tecnología específica de cola. La elección debe documentarse si se introduce una.

---

# 14. Tiempo real

## Opciones

### WebSocket

Adecuado cuando se requiere comunicación bidireccional o gestión de múltiples eventos.

### SSE

Adecuado para el patrón principalmente servidor → cliente requerido por esta prueba.

### GraphQL Subscription

Alternativa cuando toda la API se construya alrededor de GraphQL.

La elección debe justificarse en `docs/architecture.md`.

---

# 15. Frontend

## Vistas mínimas

### 15.1 Carga

Debe permitir:

- selección de archivo;
- captura de metadatos;
- validación;
- envío;
- visualización del estado de procesamiento.

### 15.2 Búsqueda

Debe permitir:

- introducir término o frase;
- ejecutar búsqueda;
- navegar entre páginas;
- visualizar fragmentos destacados;
- seleccionar documento.

### 15.3 Detalle/visor

Debe mostrar:

- título;
- autor;
- categoría;
- etiquetas;
- versión;
- contenido.

### 15.4 Estado en tiempo real

Debe mostrar cambios:

```text
PROCESSING
INDEXED
ERROR
```

sin refrescar manualmente.

---

# 16. Validaciones

## Archivo

Validar como mínimo:

- extensión;
- MIME type cuando sea aplicable;
- tamaño máximo;
- archivo vacío;
- errores de lectura;
- errores de extracción de contenido.

## Metadatos

Validar:

- presencia de campos requeridos;
- tipos;
- longitud máxima;
- valores inválidos.

Los límites exactos no están definidos por la prueba y deben establecerse como parte de la implementación.

---

# 17. Manejo de errores

Debe existir una estrategia uniforme para errores.

## Tipos

```text
ValidationError
UnsupportedFileTypeError
FileSizeExceededError
DocumentNotFoundError
TextExtractionError
IndexingError
SearchError
InternalServerError
```

> Los nombres son una propuesta para implementación.

## Respuesta API orientativa

```json
{
  "error": {
    "code": "UNSUPPORTED_FILE_TYPE",
    "message": "File type is not supported",
    "correlationId": "uuid"
  }
}
```

---

# 18. Pruebas

## Unitarias

Cubrir como mínimo:

- validación de archivos;
- validación de metadatos;
- extracción de contenido;
- transformación de documentos;
- indexación;
- búsqueda;
- reglas de cambio de estado.

## Integración

Cubrir como mínimo:

- carga → procesamiento;
- persistencia;
- indexación;
- búsqueda;
- actualización de estado;
- integración con el motor de búsqueda.

## Prueba del requisito de rendimiento

Debe incluir una prueba reproducible que evidencie el objetivo de:

```text
400 ms — 1000 ms
```

Se debe documentar:

```text
dataset
concurrencia
hardware
versión del motor
tipo de consulta
resultado
```

---

# 19. Estructura de repositorio requerida

La prueba especifica una estructura mínima:

```text
/
├── backend/
│   └── código fuente de API / Backend
│
├── frontend/
│   └── código fuente de la aplicación web
│
├── packages/
│   └── shared/
│       └── DTOs, interfaces o utilidades compartidas (opcional)
│
├── docs/
│   ├── architecture.md
│   └── ia.md
│
├── docker-compose.yml
└── README.md
```

`docker-compose.yml` es indicado como deseable para facilitar el levantamiento unificado del entorno.

---

# 20. Documentación obligatoria

## docs/architecture.md

Debe contener obligatoriamente:

### 20.1 Diagrama de arquitectura/flujo

Debe mostrar la interacción entre:

```text
Frontend
Backend
Motor de búsqueda
Mecanismo de tiempo real
```

### 20.2 Justificación de decisiones

Debe explicar:

- stack elegido;
- base de datos;
- motor de búsqueda;
- mecanismo de comunicación;
- estrategia de procesamiento asíncrono.

### 20.3 Estrategia de tiempo real

Debe describir la implementación de:

- WebSocket, o
- SSE, o
- Pub/Sub, o
- GraphQL Subscription.

### 20.4 Escalabilidad

Debe explicar cómo escalar la arquitectura ante un incremento masivo de documentos.

---

# 21. Uso de IA — docs/ia.md

Este archivo debe documentar de forma transparente el uso de Inteligencia Artificial durante la resolución de la prueba.

## Debe incluir

### Herramientas utilizadas

Ejemplos dados por la prueba:

- ChatGPT
- GitHub Copilot
- Claude
- Cursor
- Gemini

Se deben registrar únicamente las herramientas realmente utilizadas.

### Casos de uso

Documentar en qué etapas fue utilizada IA, por ejemplo:

- boilerplate;
- generación de código;
- generación de pruebas;
- optimización de queries;
- refactorización;
- análisis de errores;
- documentación.

### Prompts clave

Registrar ejemplos de prompts utilizados y cómo se fueron refinando.

### Validación humana

Explicar:

- qué código fue revisado;
- qué cambios se realizaron;
- qué decisiones fueron tomadas manualmente;
- cómo se verificó el resultado.

---

# 22. Requerimientos de arquitectura para desarrollo con LLM

Esta sección convierte el documento de la prueba en instrucciones operativas para un LLM de desarrollo.

## Principios

El LLM debe:

1. Mantener separación entre dominio, aplicación, infraestructura e interfaces cuando la arquitectura seleccionada lo requiera.
2. Evitar lógica de negocio dentro de controladores.
3. Evitar acceso directo a infraestructura desde el dominio.
4. Mantener contratos tipados.
5. Aplicar validación de entrada.
6. Centralizar manejo de excepciones.
7. Utilizar configuración por variables de entorno.
8. Mantener funciones pequeñas y responsabilidades claras.
9. Implementar pruebas junto con la funcionalidad.
10. Evitar introducir dependencias innecesarias.

---

# 23. Instrucciones maestras para el LLM

El siguiente bloque puede utilizarse como **system/developer prompt** para una IA de programación:

```text
Actúa como Senior Full Stack Engineer y Software Architect.

Objetivo:
Construir una aplicación web para carga, procesamiento, indexación, búsqueda y visualización de documentos técnicos.

Restricciones funcionales obligatorias:
- Soportar TXT, PDF y Markdown.
- Soportar carga individual y masiva.
- Recibir metadatos: title, author, category, tags y version.
- Responder inmediatamente al upload con un documentId y estado PROCESSING.
- Procesar e indexar de forma asíncrona.
- Implementar búsqueda Full-Text.
- NO utilizar SQL LIKE ni estrategias equivalentes no indexadas.
- La búsqueda debe tener como objetivo 400 ms a 1000 ms.
- Implementar paginación.
- Implementar highlighting de coincidencias.
- Permitir visualizar contenido y metadatos sin descargar el archivo.
- Actualizar estados INDEXED y ERROR mediante WebSocket, SSE o GraphQL Subscription.
- El frontend debe actualizarse sin polling tradicional como mecanismo principal.

Calidad:
- Arquitectura limpia y justificable.
- Manejo estructurado de errores.
- Variables de entorno.
- Pruebas unitarias e integración para componentes críticos.
- Código mantenible y desacoplado.
- No hardcodear secretos.
- Documentar decisiones técnicas.

Antes de modificar código:
1. Inspecciona la estructura existente.
2. Identifica arquitectura y dependencias actuales.
3. Evita reescrituras innecesarias.
4. Propón cambios pequeños y verificables.
5. Mantén compatibilidad con los contratos existentes.

Al implementar:
- Explica brevemente la decisión técnica.
- Crea primero contratos/interfaces cuando aplique.
- Implementa backend y frontend de forma incremental.
- Agrega pruebas.
- Verifica errores de compilación/lint/test.
- Actualiza README y documentación.

Para cada tarea:
- Requisitos atendidos.
- Archivos creados/modificados.
- Decisiones relevantes.
- Pruebas ejecutadas.
- Riesgos o pendientes.

No inventes requisitos de negocio que no estén definidos.
Cuando exista una decisión no especificada por la prueba, proponla explícitamente y documenta el supuesto.
```

---

# 24. Plan de implementación recomendado

## Fase 1 — Skeleton

- Crear repositorio.
- Crear backend.
- Crear frontend.
- Crear documentación base.
- Configurar variables de entorno.
- Configurar Docker Compose.

## Fase 2 — Persistencia y documentos

- Modelo `Document`.
- Migraciones/esquema.
- Endpoint de carga.
- Validaciones.
- Estado `PROCESSING`.

## Fase 3 — Procesamiento

- Worker/proceso asíncrono.
- Extracción de texto.
- Normalización.
- Indexación.
- Estados `INDEXED` / `ERROR`.

## Fase 4 — Búsqueda

- Configuración del índice.
- Búsqueda Full-Text.
- Paginación.
- Highlighting.
- Medición de rendimiento.

## Fase 5 — Visor

- Pantalla de resultados.
- Pantalla detalle.
- Renderizado por tipo de documento.
- Visualización de metadatos.

## Fase 6 — Tiempo real

- WebSocket/SSE/GraphQL Subscription.
- Eventos de procesamiento.
- Actualización reactiva del frontend.

## Fase 7 — Calidad

- Unit tests.
- Integration tests.
- Manejo de errores.
- Validación end-to-end.
- Prueba de rendimiento.

## Fase 8 — Documentación

- `README.md`
- `docs/architecture.md`
- `docs/ia.md`

---

# 25. Checklist de Definition of Done

## Backend

- [ ] Endpoint de carga funcional.
- [ ] Carga individual y masiva.
- [ ] TXT/PDF/Markdown soportados.
- [ ] Metadatos validados.
- [ ] Respuesta asíncrona inmediata.
- [ ] Estados `PROCESSING`, `INDEXED`, `ERROR`.
- [ ] Procesamiento asíncrono.
- [ ] Motor de búsqueda implementado.
- [ ] No existe `LIKE` para búsqueda.
- [ ] Paginación.
- [ ] Highlighting.
- [ ] Manejo estructurado de errores.
- [ ] Unit tests.
- [ ] Integration tests.

## Frontend

- [ ] Carga de documentos.
- [ ] Captura de metadatos.
- [ ] Búsqueda.
- [ ] Paginación.
- [ ] Highlighting.
- [ ] Visor.
- [ ] Metadatos visibles.
- [ ] Estado en tiempo real.
- [ ] Sin refresh manual para cambios de estado.

## Arquitectura

- [ ] `docs/architecture.md`.
- [ ] Diagrama.
- [ ] Justificación tecnológica.
- [ ] Estrategia de tiempo real.
- [ ] Estrategia de escalabilidad.

## IA

- [ ] `docs/ia.md`.
- [ ] Herramientas utilizadas.
- [ ] Casos de uso.
- [ ] Prompts clave.
- [ ] Evidencia de validación humana.

## Entrega

- [ ] `README.md`.
- [ ] Instrucciones de instalación.
- [ ] Instrucciones de ejecución.
- [ ] Configuración de entorno.
- [ ] `docker-compose.yml` deseable.
- [ ] Repositorio accesible para evaluación.

---

# 26. Checklist de sustentación

La prueba reserva **30 minutos**, divididos en:

### 15 minutos — Exposición y demostración

Mostrar:

1. Arquitectura.
2. Flujo de carga.
3. Procesamiento asíncrono.
4. Indexación.
5. Búsqueda.
6. Highlighting.
7. Visor.
8. Actualización en tiempo real.

### 15 minutos — Preguntas técnicas

Preparar explicación sobre:

- decisiones de arquitectura;
- motor de búsqueda;
- concurrencia;
- procesamiento asíncrono;
- rendimiento;
- escalabilidad;
- manejo de errores;
- pruebas;
- uso de IA.

---

# 27. Decisiones abiertas que deben resolverse antes de codificar

El documento fuente permite libertad técnica en varios puntos. El equipo/LLM debe tomar una decisión explícita sobre:

| Tema | Opciones indicadas | Decisión pendiente |
|---|---|---|
| Backend | Express, NestJS, FastAPI, Spring Boot, Quarkus | Definir |
| Frontend | React, Next.js, Angular | Definir |
| API | REST / GraphQL | Definir |
| Tiempo real | WebSocket / SSE / GraphQL Subscription | Definir |
| DB | Libre elección | Definir |
| Motor búsqueda | Elasticsearch / OpenSearch / PostgreSQL FTS / etc. | Definir |
| Procesamiento asíncrono | Libre elección | Definir |
| Arquitectura | Hexagonal / Clean / Layered / DDD | Definir |
| Almacenamiento de archivos | No especificado | Definir |
| Límite máximo de archivo | No especificado | Definir |
| Concurrencia objetivo | No especificada | Definir |
| Dataset de prueba | No especificado | Definir |

Estas decisiones deben documentarse y justificarse, no quedar implícitas.

---

# 28. Trazabilidad requisito → implementación

Se recomienda mantener una matriz como la siguiente durante el desarrollo:

| Requisito | Componente | Evidencia |
|---|---|---|
| RF-001 Carga | Document API | Test + endpoint |
| RF-002 Procesamiento | Worker | Test + logs |
| RF-003 Búsqueda | Search service | Test + benchmark |
| RF-004 Resultados | Search API + Frontend | E2E |
| RF-005 Visor | Frontend | Demo |
| RF-006 Tiempo real | Event layer + Frontend | Demo |
| RNF-001 Rendimiento | Search engine | Benchmark |
| RNF-003 Arquitectura | Backend/Frontend | architecture.md |
| RNF-004 Calidad | Todo el sistema | Unit/Integration Tests |
| IA | docs/ia.md | Documentación |

---

# 29. Resultado esperado

Al completar la implementación debe existir una aplicación que demuestre de extremo a extremo:

```text
Documento
   |
   v
Upload
   |
   v
PROCESSING
   |
   v
Procesamiento asíncrono
   |
   v
Extracción de texto
   |
   v
Indexación
   |
   v
INDEXED
   |
   v
Evento en tiempo real
   |
   v
Búsqueda Full-Text
   |
   v
Resultados + Highlighting
   |
   v
Visor + Metadatos
```

La solución será considerada alineada con la prueba cuando cubra los criterios funcionales, técnicos, de tiempo real, rendimiento, arquitectura, calidad y documentación establecidos en el documento fuente.
