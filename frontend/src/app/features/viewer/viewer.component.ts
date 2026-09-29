import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { DocumentService } from '../../core/services/document.service';
import { DocumentDetail } from '../../core/models/document.model';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

@Component({
  selector: 'app-viewer',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="viewer-page">

      <!-- Loading -->
      @if (isLoading()) {
        <div class="loading-state" role="status" aria-live="polite">
          <div class="loading-spinner"></div>
          <p>Cargando documento...</p>
        </div>
      }

      <!-- Error -->
      @if (error()) {
        <div class="error-state" role="alert">
          <div class="error-icon">⚠️</div>
          <h2>Error al cargar el documento</h2>
          <p>{{ error() }}</p>
          <button class="btn-back" (click)="goBack()" id="btn-back-error">← Volver a búsqueda</button>
        </div>
      }

      <!-- Document loaded -->
      @if (document()) {
        <div class="viewer-layout">

          <!-- Left Panel: Metadata -->
          <aside class="metadata-panel" aria-label="Metadatos del documento">
            <button class="btn-back" (click)="goBack()" id="btn-back">← Volver</button>

            <div class="meta-section">
              <h2 class="meta-title">{{ document()!.metadata.title }}</h2>

              <div class="status-badge" [class]="'status-badge--' + document()!.status.toLowerCase()">
                {{ getStatusIcon(document()!.status) }} {{ document()!.status }}
              </div>
            </div>

            <dl class="meta-list">
              <dt>Autor</dt>
              <dd id="meta-author">{{ document()!.metadata.author }}</dd>

              <dt>Categoría</dt>
              <dd id="meta-category">{{ document()!.metadata.category }}</dd>

              <dt>Versión</dt>
              <dd id="meta-version">{{ document()!.metadata.version }}</dd>

              <dt>Tipo</dt>
              <dd id="meta-type">{{ document()!.fileType?.toUpperCase() }}</dd>

              <dt>Archivo</dt>
              <dd id="meta-filename" class="meta-filename">{{ document()!.originalFileName }}</dd>

              <dt>Creado</dt>
              <dd id="meta-created">{{ document()!.createdAt | date:'dd/MM/yyyy HH:mm' }}</dd>

              <dt>Actualizado</dt>
              <dd id="meta-updated">{{ document()!.updatedAt | date:'dd/MM/yyyy HH:mm' }}</dd>
            </dl>

            @if (document()!.metadata.tags?.length) {
              <div class="meta-tags">
                <strong>Etiquetas</strong>
                <div class="tags-cloud">
                  @for (tag of document()!.metadata.tags; track tag) {
                    <span class="tag-badge" id="tag-{{ tag }}">{{ tag }}</span>
                  }
                </div>
              </div>
            }

            @if (document()!.errorMessage) {
              <div class="error-alert" role="alert">
                <strong>Error de procesamiento:</strong>
                <p>{{ document()!.errorMessage }}</p>
              </div>
            }
          </aside>

          <!-- Right Panel: Content -->
          <main class="content-panel" aria-label="Contenido del documento">
            @if (document()!.status === 'PROCESSING') {
              <div class="processing-state">
                <div class="loading-spinner"></div>
                <p>El documento aún se está procesando...</p>
              </div>
            } @else if (document()!.status === 'ERROR') {
              <div class="empty-content">
                <span>❌</span>
                <p>No se pudo procesar este documento.</p>
              </div>
            } @else if (document()!.fileType === 'pdf') {
              <iframe
                id="pdf-viewer"
                [src]="pdfUrl()"
                class="pdf-frame"
                title="Visor de PDF"
                aria-label="Contenido PDF del documento"
              ></iframe>
            } @else if (document()!.fileType === 'md') {
              <article class="markdown-content" id="markdown-content" [innerHTML]="renderedMarkdown()"></article>
            } @else {
              <pre class="text-content" id="text-content">{{ document()!.content }}</pre>
            }
          </main>
        </div>
      }
    </div>
  `,
  styles: [`
    .viewer-page {
      min-height: calc(100vh - 64px);
    }

    .loading-state, .error-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: calc(100vh - 64px);
      gap: 1rem;
      color: var(--text-secondary);
      text-align: center;
    }
    .error-state { color: #f87171; }
    .error-icon { font-size: 3rem; }

    .loading-spinner {
      width: 40px;
      height: 40px;
      border: 3px solid var(--border-subtle);
      border-top-color: var(--accent);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }

    .viewer-layout {
      display: grid;
      grid-template-columns: 300px 1fr;
      min-height: calc(100vh - 64px);
    }

    /* Metadata Panel */
    .metadata-panel {
      padding: 2rem 1.5rem;
      border-right: 1px solid var(--border-subtle);
      background: var(--surface-card);
      overflow-y: auto;
      position: sticky;
      top: 64px;
      height: calc(100vh - 64px);
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
    }

    .btn-back {
      background: none;
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-md);
      padding: 0.5rem 1rem;
      color: var(--text-secondary);
      cursor: pointer;
      font-size: 0.875rem;
      width: fit-content;
      transition: all 0.2s;
    }
    .btn-back:hover { border-color: var(--accent); color: var(--accent); }

    .meta-section { display: flex; flex-direction: column; gap: 0.75rem; }
    .meta-title {
      font-size: 1.1rem;
      font-weight: 700;
      color: var(--text-primary);
      margin: 0;
      line-height: 1.4;
    }

    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.3rem 0.75rem;
      border-radius: 999px;
      font-size: 0.8rem;
      font-weight: 600;
      width: fit-content;
    }
    .status-badge--indexed    { background: rgba(34, 197, 94, 0.15); color: #4ade80; }
    .status-badge--processing { background: rgba(99, 102, 241, 0.15); color: #a5b4fc; }
    .status-badge--error      { background: rgba(239, 68, 68, 0.15); color: #f87171; }

    .meta-list {
      margin: 0;
      display: grid;
      grid-template-columns: auto 1fr;
      gap: 0.5rem 1rem;
      font-size: 0.875rem;
    }
    dt { color: var(--text-muted); font-weight: 600; text-transform: uppercase; font-size: 0.75rem; letter-spacing: 0.05em; }
    dd { margin: 0; color: var(--text-primary); word-break: break-word; }
    .meta-filename { font-family: monospace; font-size: 0.8rem; }

    .meta-tags { display: flex; flex-direction: column; gap: 0.5rem; }
    .meta-tags strong { font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); }
    .tags-cloud { display: flex; flex-wrap: wrap; gap: 0.4rem; }
    .tag-badge {
      padding: 0.2rem 0.6rem;
      background: var(--accent-subtle);
      color: var(--accent);
      border-radius: 999px;
      font-size: 0.78rem;
      font-weight: 600;
    }

    .error-alert {
      padding: 0.875rem 1rem;
      background: rgba(239, 68, 68, 0.12);
      border: 1px solid rgba(239, 68, 68, 0.3);
      border-radius: var(--radius-md);
      font-size: 0.85rem;
      color: #f87171;
    }
    .error-alert p { margin: 0.25rem 0 0; }

    /* Content Panel */
    .content-panel {
      padding: 2rem;
      overflow-y: auto;
    }

    .processing-state, .empty-content {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 100%;
      min-height: 200px;
      gap: 1rem;
      color: var(--text-secondary);
      text-align: center;
    }
    .empty-content span { font-size: 3rem; }

    .pdf-frame {
      width: 100%;
      height: calc(100vh - 100px);
      border: none;
      border-radius: var(--radius-md);
    }

    .text-content {
      white-space: pre-wrap;
      word-break: break-word;
      font-family: 'Courier New', Courier, monospace;
      font-size: 0.9rem;
      line-height: 1.7;
      color: var(--text-primary);
      padding: 1.5rem;
      background: var(--surface-card);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-lg);
      overflow-x: auto;
    }

    .markdown-content {
      max-width: 72ch;
      color: var(--text-primary);
      line-height: 1.8;
    }
    :host ::ng-deep .markdown-content h1,
    :host ::ng-deep .markdown-content h2,
    :host ::ng-deep .markdown-content h3 {
      color: var(--text-primary);
      margin-top: 2rem;
      margin-bottom: 0.75rem;
    }
    :host ::ng-deep .markdown-content code {
      background: var(--surface-card);
      padding: 0.1em 0.4em;
      border-radius: 4px;
      font-size: 0.9em;
    }
    :host ::ng-deep .markdown-content pre {
      background: var(--surface-card);
      padding: 1rem 1.25rem;
      border-radius: var(--radius-md);
      overflow-x: auto;
    }
    :host ::ng-deep .markdown-content blockquote {
      border-left: 4px solid var(--accent);
      margin-left: 0;
      padding-left: 1rem;
      color: var(--text-secondary);
    }

    @media (max-width: 768px) {
      .viewer-layout { grid-template-columns: 1fr; }
      .metadata-panel { position: static; height: auto; }
    }
  `]
})
export class ViewerComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private documentService = inject(DocumentService);
  private sanitizer = inject(DomSanitizer);

  document = signal<DocumentDetail | null>(null);
  isLoading = signal(true);
  error = signal<string | null>(null);
  pdfUrl = signal<SafeResourceUrl>('');
  renderedMarkdown = signal<string>('');

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set('ID de documento no encontrado en la URL.');
      this.isLoading.set(false);
      return;
    }

    this.documentService.getDocument(id).subscribe({
      next: (doc) => {
        this.document.set(doc);
        this.isLoading.set(false);

        if (doc.fileType === 'pdf') {
          const url = `/api/documents/${id}/content`;
          this.pdfUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(url));
        }
        if (doc.fileType === 'md' && doc.content) {
          this.renderedMarkdown.set(this.renderMarkdown(doc.content));
        }
      },
      error: (err) => {
        this.isLoading.set(false);
        this.error.set(err?.error?.error?.message ?? 'No se pudo cargar el documento.');
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/search']);
  }

  getStatusIcon(status: string): string {
    return { INDEXED: '✅', PROCESSING: '⏳', ERROR: '❌' }[status] ?? '❓';
  }

  /**
   * Simple markdown-to-HTML renderer without external dependencies.
   * For production, replace with ngx-markdown.
   */
  private renderMarkdown(md: string): string {
    return md
      .replace(/^### (.+)$/gm, '<h3>$1</h3>')
      .replace(/^## (.+)$/gm, '<h2>$1</h2>')
      .replace(/^# (.+)$/gm, '<h1>$1</h1>')
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.+?)\*/g, '<em>$1</em>')
      .replace(/`(.+?)`/g, '<code>$1</code>')
      .replace(/^> (.+)$/gm, '<blockquote>$1</blockquote>')
      .replace(/\[(.+?)\]\((.+?)\)/g, '<a href="$2">$1</a>')
      .replace(/\n\n/g, '</p><p>')
      .replace(/^/, '<p>').replace(/$/, '</p>');
  }
}
