# Documentos Técnicos - Uso de Inteligencia Artificial

> **Ubicación:** `docs/ia.md` | **Finalidad:** Documentar herramientas, prompts y validación humana.
> **Referencia obligatoria:** `AGENTS.md§10`, `AGENTS.md§15`, `AGENTS.md§600` (Principio 6: "Documenta decisiones")

---

## 1. Propósito y Alcance

Este documento registra el uso de herramientas de Inteligencia Artificial durante el desarrollo de **VisorDocsKATA**. El agente LLM (opencode) ha sido utilizado como asistente de desarrollo senior para:

- Diseñar y refactorizar código Java y TypeScript
- Generar consultas Elasticsearch DSL
- Crear y revisar contratos de API REST
- Escribir documentación técnica
- Ayuda en diseño de arquitectura y patrones

**Nota importante:** Toda decisión técnica, contrato de API o cambio de arquitectura ha sido revisado y validado por un ingeniero humano antes de ser aplicado. La IA sugiere, el humano decide y valida.

---

## 2. Herramientas Utilizadas

| Herramienta | Propósito | Cuándo se usa |
|-------------|-----------|---------------|
| **opencode (nvidia/nemotron-3.5-lightning-30b-a3b)** | Asistencia de código, investigación, generación de boilerplate, revisiones | Durante todo el desarrollo: creación de componentes, fixes de bugs, diseño de queries, documentación |
| **Git/GitHub** | Control de versiones, historial de cambios | Commit de cambios, PR, rollback si es necesario |
| **Docker Desktop** | Levantar infraestructura (PG, ES, backend, frontend) | Fase de desarrollo local, integración continua |
| **Postman / curl** | Testing de endpoints REST | Validación manual de respuestas API |
| **VS Code** | Edición de archivos, debugging | Desarrollo day-to-day |

---

## 3. Ejemplos de Prompts Clave

A continuación se documentan algunos de los prompts más relevantes utilizados durante el desarrollo:

### 3.1. Diseño inicial de arquitectura

```
Actúa como Senior Full Stack Engineer y Software Architect. Construye una aplicación web para cargar documentos técnicos (TXT, PDF, Markdown) con metadatos, procesarlos e indexarlos de forma asíncrona, buscarlos con Full-Text Search de alto rendimiento usando Elasticsearch 8, visualizarlos sin descargar el archivo, y actualizar el estado en tiempo real sin polling.

Stack: Quarkus 3.x (Java), Angular 17+, PostgreSQL 16, ES 8.x, Docker Compose.

Restricciones obligatorias:
- NO usar SQL LIKE ni estrategias no indexadas para búsqueda
- Búsqueda Full-Text obligatoria mediante ES 8
- Respuesta inmediata en POST /documents con documentId + status: PROCESSING
- Sin polling en frontend; usar SSE
- Sin secretos hardcodeados
- Cobertura de pruebas >= 80%

Entregar: Estructura de repo, modelos de dominio, contratos API REST, descripción de flujo asíncrono, y_plan_de_fases.
```

### 3.2. Corrección de configuración Elasticsearch en tests

```
El backend Quarkus tiene application.properties con perfil test que tiene hardcodeado `quarkus.elasticsearch.hosts=localhost:9200`, pero en Docker Compose Elasticsearch corre como servicio `elasticsearch:9200`. El .env ya tiene `ELASTICSEARCH_URL=elasticsearch:9200`. 

¿Cómo corregir el application.properties para que el perfil test use la variable de entorno con fallback a `elasticsearch:9200`? Generar la línea exacta y explicarle por qué el test profile necesitaba esto.
```

### 3.3. Fix error media_type_header_exception en búsqueda ES

```
El endpoint GET /api/documents/search devuelve error `[media_type_header_exception] Invalid media-type value on headers [Content-Type, Accept]` cuando `quarkus.elasticsearch.java-client.compatibility-mode=true`. 

¿Cuál es la configuración correcta para setear `compatibility-mode=false` en Quarkus 3.x? ¿O hay que ajustar `quarkus.elasticsearch.http.headers` en su lugar? Investigar y proponer la solución mínima que funcione.
```

### 3.5. Fix nginx proxy para frontend (API 404 en Docker)

```
El frontend en nginx (puerto 4200) devuelve 404 en todas las peticiones /api/* porque:
1. nginx cachea la resolución DNS de `backend` al inicio (antes de que el contenedor backend esté listo)
2. El orden de `location /` antes de `location /api/` hace que `try_files` intercepte las peticiones

Solución aplicada:
1. Añadir `resolver 127.0.0.11 valid=10s;` (DNS interno de Docker)
2. Usar variable en `proxy_pass`: `set $backend "http://backend:8080"; proxy_pass $backend;`
3. Poner `location /api/` y `/api/events` ANTES de `location /`
```

### 3.4. Creación de componente Angular de subida

```
Diseñar un componente Angular 17 para la página de carga de documentos. Debe tener:
- Formulario reactivo con campos: título, autor, categoría, etiquetas, versión
- Input de archivo con accept=".txt,.pdf,.md" y drag-and-drop
- Validación client-side de extensión y tamaño máximo (10 MB)
- Spinner de estado después del envío
- Consumo de SSE para notificar cuándo cambia el estado a INDEXED o ERROR
- Toast de éxito/error y navegación a /documents/:id cuando termina

Generar el componente TypeScript completo, el template HTML con estilos CSS inline, y el servicio DocumentService mínimo que haga POST multipart al backend.
```

---

## 4. Flujo de Trabajo IA-Humano

```mermaid
graph TD
    A[Ingeniero humano: define requisito/objetivo] -->|1. Solicitar ayuda| B[opencode (LLM)]
    B -->|2. Generar código/decisiones| C[Código/generado]
    C -->|3. Revisar y validar| A
    A -- Aprobar -->|4. Aplicar si es correcto| D[Aplicado al repo]
    A -- Rechazar/Modificar -->|5. Solicitar ajustes| B
    D -->|6. Tests y validación| E[Tests pasan / fallan]
    E -->|7. Si OK, continuar| F[Siguiente tarea]
    E -->|Si falla, regresar a| A
```

**Regla fundamental:** La IA sugiere, el humano valida y decide. Ningún cambio se aplica al repositorio sin revisión y aprobación humana.

---

## 5. Validación Humana Realizada

| Área | Tarea | Validado por | Resultado |
|------|-------|--------------|-----------|
| **Backend** | Configuración ES en application.properties | Ingeniero humano | Aprobado: `%test.quarkus.elasticsearch.hosts=${ELASTICSEARCH_URL:elasticsearch:9200}` |
| **Backend** | Fix `compatibility-mode=false` (low-level REST client) | Ingeniero humano | Aprobado: resuelve error `media_type_header_exception` |
| **Backend** | nginx proxy fix (resolver + variable $backend) | Ingeniero humano | Aprobado: resuelve 404 en /api/* desde frontend |
| **Frontend** | Componente upload con SSE | Ingeniero humano | Aprobado: muestra documentId inmediatamente + SSE subscription |
| **Arquitectura** | Decisiones en `architecture.md` | Ingeniero humano | Aprobado: coherente con AGENTS.md y restricciones |
| **Testing** | Estructura de TODO y memory.md | Ingeniero humano | Aprobado: refleja estado real del proyecto |

---

## 6. Límites y Precauciones

| Riesgo | Mitigación |
|--------|------------|
| **Alucinaciones** (información incorrecta) | Revisar siempre lógica de negocio, queries ES, contratos API |
| **Sesgo hacia soluciones complejas** | Aplicar principio KISS; revisar si hay solución más simple |
| **Descuidar restricciones AGENTS.md** | Revisar cada cambio contra la lista de restricciones (sección 3 de AGENTS.md) |
| **Secretos o credenciales en código** | Verificar que no haya hardcoding; toda configuración mediante vars de entorno |

---

## 7. Próximos Documentos IA

Los siguientes documentos relacionados con IA aún no están completos y podrían generarse en futuras sesiones:

- [ ] `docs/architecture.md§7` - Decisiones de arquitectura documentadas ✅ (actualizado)
- [ ] Ejemplos de queries ES optimizadas (perfil de rendimiento)
- [ ] Patrones de SSE en Angular reutilizables
- [ ] Plantillas de tests unitarios para servicios Quarkus
- [ ] Documentación del nginx resolver pattern para Docker Compose

---

*Documento generado con asistencia de IA (nvidia/nemotron-3.5-lightning-30b-a3b) y validación humana. Última actualización: 2026-09-30. Consulte `AGENTS.md§600` sobre principio "Documenta decisiones".*