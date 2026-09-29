import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { DocumentService } from '../../core/services/document.service';
import { SseService } from '../../core/services/sse.service';
import { ToastService } from '../../core/services/toast.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-upload',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="upload-page">
      <div class="upload-container">
        <div class="page-header">
          <h1 class="page-title">Subir Documento</h1>
          <p class="page-subtitle">Carga documentos técnicos en formato TXT, PDF o Markdown</p>
        </div>

        <form [formGroup]="uploadForm" (ngSubmit)="onSubmit()" class="upload-form" novalidate>

          <!-- Drag & Drop Zone -->
          <div
            class="drop-zone"
            [class.drop-zone--active]="isDragging()"
            [class.drop-zone--has-file]="selectedFile()"
            (dragover)="onDragOver($event)"
            (dragleave)="isDragging.set(false)"
            (drop)="onDrop($event)"
            (click)="fileInput.click()"
            id="drop-zone"
            role="button"
            tabindex="0"
            aria-label="Zona de arrastrar y soltar archivo"
            (keydown.enter)="fileInput.click()"
          >
            <input
              #fileInput
              type="file"
              accept=".txt,.pdf,.md"
              (change)="onFileSelected($event)"
              class="hidden-input"
              id="file-input"
            />
            @if (selectedFile()) {
              <div class="file-preview">
                <span class="file-icon">{{ getFileIcon(selectedFile()!.type) }}</span>
                <div class="file-info">
                  <span class="file-name">{{ selectedFile()!.name }}</span>
                  <span class="file-size">{{ formatSize(selectedFile()!.size) }}</span>
                </div>
                <button type="button" class="remove-file" (click)="removeFile($event)" aria-label="Eliminar archivo">✕</button>
              </div>
            } @else {
              <div class="drop-placeholder">
                <div class="drop-icon">📂</div>
                <p class="drop-text">Arrastra tu archivo aquí o <strong>haz clic para seleccionar</strong></p>
                <p class="drop-hint">TXT · PDF · Markdown (Máx. 10 MB)</p>
              </div>
            }
          </div>
          @if (fileError()) {
            <p class="field-error" role="alert">{{ fileError() }}</p>
          }

          <!-- Metadata Fields -->
          <div class="form-grid">
            <div class="form-group">
              <label for="title">Título *</label>
              <input id="title" type="text" formControlName="title" placeholder="Ej. Manual de Arquitectura" class="form-input" />
              @if (isInvalid('title')) {
                <p class="field-error">El título es obligatorio</p>
              }
            </div>

            <div class="form-group">
              <label for="author">Autor *</label>
              <input id="author" type="text" formControlName="author" placeholder="Ej. Juan García" class="form-input" />
              @if (isInvalid('author')) {
                <p class="field-error">El autor es obligatorio</p>
              }
            </div>

            <div class="form-group">
              <label for="category">Categoría *</label>
              <input id="category" type="text" formControlName="category" placeholder="Ej. Arquitectura, DevOps" class="form-input" />
              @if (isInvalid('category')) {
                <p class="field-error">La categoría es obligatoria</p>
              }
            </div>

            <div class="form-group">
              <label for="version">Versión *</label>
              <input id="version" type="text" formControlName="version" placeholder="Ej. 1.0.0" class="form-input" />
              @if (isInvalid('version')) {
                <p class="field-error">La versión es obligatoria</p>
              }
            </div>

            <div class="form-group form-group--full">
              <label for="tags">Etiquetas (separadas por coma)</label>
              <input id="tags" type="text" formControlName="tags" placeholder="Ej. quarkus, java, backend" class="form-input" />
            </div>
          </div>

          <!-- Submit -->
          <div class="form-actions">
            <button type="submit" class="btn-primary" id="btn-upload" [disabled]="isUploading()">
              @if (isUploading()) {
                <span class="spinner"></span>
                Procesando...
              } @else {
                ⬆️ Subir Documento
              }
            </button>
          </div>
        </form>

        <!-- Upload Status -->
        @if (uploadStatus()) {
          <div class="status-card" [class]="'status-card--' + uploadStatus()!.type" role="status" id="upload-status">
            <span class="status-icon">{{ uploadStatus()!.icon }}</span>
            <div class="status-body">
              <strong>{{ uploadStatus()!.title }}</strong>
              <p>{{ uploadStatus()!.message }}</p>
            </div>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .upload-page {
      min-height: calc(100vh - 64px);
      display: flex;
      align-items: flex-start;
      justify-content: center;
      padding: 3rem 1.5rem;
    }
    .upload-container {
      width: 100%;
      max-width: 720px;
    }
    .page-header { margin-bottom: 2rem; }
    .page-title {
      font-size: 2rem;
      font-weight: 800;
      background: var(--gradient-brand);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
      margin: 0 0 0.5rem;
    }
    .page-subtitle { color: var(--text-secondary); margin: 0; }

    .upload-form {
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
    }

    .drop-zone {
      border: 2px dashed var(--border-subtle);
      border-radius: var(--radius-xl);
      padding: 2.5rem;
      text-align: center;
      cursor: pointer;
      transition: all 0.25s ease;
      background: var(--surface-card);
    }
    .drop-zone:hover, .drop-zone--active {
      border-color: var(--accent);
      background: var(--accent-subtle);
    }
    .drop-zone--has-file {
      border-color: var(--accent);
      border-style: solid;
    }
    .hidden-input { display: none; }
    .drop-placeholder { display: flex; flex-direction: column; align-items: center; gap: 0.75rem; }
    .drop-icon { font-size: 3rem; }
    .drop-text { margin: 0; color: var(--text-secondary); font-size: 1rem; }
    .drop-hint { margin: 0; color: var(--text-muted); font-size: 0.85rem; }

    .file-preview {
      display: flex;
      align-items: center;
      gap: 1rem;
    }
    .file-icon { font-size: 2.5rem; }
    .file-info { flex: 1; text-align: left; }
    .file-name { display: block; font-weight: 600; color: var(--text-primary); word-break: break-all; }
    .file-size { font-size: 0.85rem; color: var(--text-muted); }
    .remove-file {
      background: rgba(239, 68, 68, 0.15);
      border: none;
      border-radius: 50%;
      width: 32px;
      height: 32px;
      cursor: pointer;
      color: #f87171;
      font-size: 0.9rem;
      flex-shrink: 0;
      transition: background 0.2s;
    }
    .remove-file:hover { background: rgba(239, 68, 68, 0.3); }

    .form-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
    }
    .form-group { display: flex; flex-direction: column; gap: 0.4rem; }
    .form-group--full { grid-column: 1 / -1; }
    .form-group label { font-size: 0.875rem; font-weight: 600; color: var(--text-secondary); }
    .form-input {
      padding: 0.625rem 0.875rem;
      background: var(--surface-card);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-md);
      color: var(--text-primary);
      font-size: 0.9rem;
      transition: border-color 0.2s, box-shadow 0.2s;
      outline: none;
    }
    .form-input::placeholder { color: var(--text-muted); }
    .form-input:focus {
      border-color: var(--accent);
      box-shadow: 0 0 0 3px var(--accent-subtle);
    }
    .field-error { margin: 0.2rem 0 0; font-size: 0.8rem; color: #f87171; }

    .form-actions { display: flex; justify-content: flex-end; }
    .btn-primary {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.75rem 2rem;
      background: var(--gradient-brand);
      color: white;
      border: none;
      border-radius: var(--radius-md);
      font-size: 1rem;
      font-weight: 600;
      cursor: pointer;
      transition: opacity 0.2s, transform 0.1s;
    }
    .btn-primary:hover:not(:disabled) { opacity: 0.9; transform: translateY(-1px); }
    .btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }

    .spinner {
      width: 16px;
      height: 16px;
      border: 2px solid rgba(255,255,255,0.3);
      border-top-color: white;
      border-radius: 50%;
      animation: spin 0.7s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }

    .status-card {
      display: flex;
      align-items: flex-start;
      gap: 1rem;
      padding: 1.25rem 1.5rem;
      border-radius: var(--radius-lg);
      border: 1px solid transparent;
      animation: fadeIn 0.3s ease;
    }
    @keyframes fadeIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
    .status-card--processing { background: rgba(99, 102, 241, 0.12); border-color: rgba(99, 102, 241, 0.3); }
    .status-card--indexed    { background: rgba(34, 197, 94, 0.12); border-color: rgba(34, 197, 94, 0.3); }
    .status-card--error      { background: rgba(239, 68, 68, 0.12); border-color: rgba(239, 68, 68, 0.3); }
    .status-icon { font-size: 1.5rem; flex-shrink: 0; }
    .status-body strong { color: var(--text-primary); }
    .status-body p { margin: 0.25rem 0 0; font-size: 0.9rem; color: var(--text-secondary); }

    @media (max-width: 600px) {
      .form-grid { grid-template-columns: 1fr; }
    }
  `]
})
export class UploadComponent {
  private fb = inject(FormBuilder);
  private documentService = inject(DocumentService);
  private sseService = inject(SseService);
  private toastService = inject(ToastService);
  private router = inject(Router);

  uploadForm: FormGroup = this.fb.group({
    title: ['', [Validators.required, Validators.minLength(1)]],
    author: ['', Validators.required],
    category: ['', Validators.required],
    version: ['', Validators.required],
    tags: ['']
  });

  selectedFile = signal<File | null>(null);
  fileError = signal<string | null>(null);
  isUploading = signal(false);
  isDragging = signal(false);
  uploadStatus = signal<{ type: string; icon: string; title: string; message: string } | null>(null);

  private sseSub?: Subscription;

  readonly ALLOWED_EXTENSIONS = ['txt', 'pdf', 'md'];
  readonly MAX_SIZE_MB = 10;

  isInvalid(field: string): boolean {
    const ctrl = this.uploadForm.get(field);
    return !!(ctrl && ctrl.invalid && ctrl.touched);
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragging.set(true);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragging.set(false);
    const file = event.dataTransfer?.files?.[0];
    if (file) this.setFile(file);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.setFile(file);
  }

  private setFile(file: File): void {
    const ext = file.name.split('.').pop()?.toLowerCase() ?? '';
    if (!this.ALLOWED_EXTENSIONS.includes(ext)) {
      this.fileError.set(`Formato no permitido: .${ext}. Use TXT, PDF o Markdown.`);
      return;
    }
    if (file.size > this.MAX_SIZE_MB * 1024 * 1024) {
      this.fileError.set(`El archivo supera el límite de ${this.MAX_SIZE_MB} MB.`);
      return;
    }
    this.fileError.set(null);
    this.selectedFile.set(file);
  }

  removeFile(event: Event): void {
    event.stopPropagation();
    this.selectedFile.set(null);
    this.fileError.set(null);
  }

  onSubmit(): void {
    this.uploadForm.markAllAsTouched();
    if (this.uploadForm.invalid) return;
    if (!this.selectedFile()) {
      this.fileError.set('Debes seleccionar un archivo.');
      return;
    }

    const formData = new FormData();
    formData.append('file', this.selectedFile()!);
    formData.append('title', this.uploadForm.value.title);
    formData.append('author', this.uploadForm.value.author);
    formData.append('category', this.uploadForm.value.category);
    formData.append('tags', this.uploadForm.value.tags ?? '');
    formData.append('version', this.uploadForm.value.version);

    this.isUploading.set(true);
    this.uploadStatus.set({ type: 'processing', icon: '⏳', title: 'Cargado', message: 'El documento está siendo procesado e indexado...' });

    this.documentService.uploadDocument(formData).subscribe({
      next: (res) => {
        this.isUploading.set(false);
        this.subscribeToSse(res.documentId);
      },
      error: (err) => {
        this.isUploading.set(false);
        const msg = err?.error?.error?.message ?? 'Error al subir el documento.';
        this.uploadStatus.set({ type: 'error', icon: '❌', title: 'Error', message: msg });
        this.toastService.error(msg);
      }
    });
  }

  private subscribeToSse(documentId: string): void {
    this.sseSub = this.sseService.observeDocument(documentId).subscribe({
      next: (event) => {
        if (event.status === 'INDEXED') {
          this.uploadStatus.set({ type: 'indexed', icon: '✅', title: 'Indexado', message: 'El documento fue indexado correctamente. Redirigiendo...' });
          this.toastService.success('¡Documento indexado exitosamente!');
          this.sseSub?.unsubscribe();
          setTimeout(() => this.router.navigate(['/documents', documentId]), 1500);
        } else if (event.status === 'ERROR') {
          this.uploadStatus.set({ type: 'error', icon: '❌', title: 'Error en procesamiento', message: event.errorMessage ?? 'Ocurrió un error al procesar el documento.' });
          this.toastService.error(event.errorMessage ?? 'Error al procesar el documento.');
          this.sseSub?.unsubscribe();
        }
      },
      error: () => {
        this.uploadStatus.set({ type: 'error', icon: '❌', title: 'Error de conexión', message: 'Se perdió la conexión SSE.' });
        this.sseSub?.unsubscribe();
      }
    });
  }

  getFileIcon(mimeType: string): string {
    if (mimeType.includes('pdf')) return '📕';
    if (mimeType.includes('text') || mimeType === '') return '📄';
    return '📝';
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }
}
