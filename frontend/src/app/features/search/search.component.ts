import { Component, inject, signal, OnDestroy } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { SearchService } from '../../core/services/search.service';
import { SearchItem, SearchResponse } from '../../core/models/document.model';
import { debounceTime, distinctUntilChanged, switchMap, catchError, of, Subject, takeUntil } from 'rxjs';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

@Component({
  selector: 'app-search',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="search-page">
      <!-- Hero Search Bar -->
      <div class="search-hero">
        <h1 class="hero-title">Búsqueda de Documentos</h1>
        <p class="hero-subtitle">Busca en todo el contenido técnico indexado</p>
        <div class="search-bar-wrapper">
          <span class="search-icon">🔍</span>
          <input
            type="search"
            [formControl]="searchControl"
            placeholder="Busca por título, autor, contenido o etiquetas..."
            class="search-input"
            id="search-input"
            autocomplete="off"
            aria-label="Campo de búsqueda"
          />
          @if (isLoading()) {
            <span class="search-spinner"></span>
          }
        </div>
      </div>

      <div class="results-section">
        <!-- Error -->
        @if (error()) {
          <div class="alert alert--error" role="alert">
            ⚠️ {{ error() }}
          </div>
        }

        <!-- No query yet -->
        @if (!searchControl.value && !isLoading()) {
          <div class="empty-state">
            <div class="empty-icon">📚</div>
            <h2>Empieza a buscar</h2>
            <p>Escribe al menos un término para encontrar documentos</p>
          </div>
        }

        <!-- Results header -->
        @if (response() && searchControl.value) {
          <div class="results-header">
            <span class="results-count">
              <strong>{{ response()!.total }}</strong> resultado{{ response()!.total !== 1 ? 's' : '' }}
              para <em>"{{ searchControl.value }}"</em>
            </span>
          </div>

          <!-- Result list -->
          @if (response()!.items.length > 0) {
            <ul class="results-list" aria-label="Resultados de búsqueda">
              @for (item of response()!.items; track item.documentId) {
                <li class="result-card" (click)="goToDocument(item.documentId)" role="button" tabindex="0" [id]="'result-' + item.documentId" (keydown.enter)="goToDocument(item.documentId)">
                  <div class="result-header">
                    <h3 class="result-title" [innerHTML]="safeHighlight(item.title)"></h3>
                    <span class="result-category badge">{{ item.metadata.category }}</span>
                  </div>
                  <div class="result-meta">
                    <span>👤 {{ item.metadata.author }}</span>
                    <span>📦 v{{ item.metadata.version }}</span>
                    @for (tag of item.metadata.tags; track tag) {
                      <span class="badge badge--tag">{{ tag }}</span>
                    }
                  </div>
                  @if (item.highlight.length > 0) {
                    <div class="result-highlights">
                      @for (fragment of item.highlight.slice(0, 2); track $index) {
                        <p class="highlight-fragment" [innerHTML]="sanitize(fragment)">...</p>
                      }
                    </div>
                  }
                </li>
              }
            </ul>
          } @else {
            <div class="empty-state">
              <div class="empty-icon">🔎</div>
              <h2>Sin resultados</h2>
              <p>Ningún documento coincide con "{{ searchControl.value }}"</p>
            </div>
          }

          <!-- Pagination -->
          @if (response()!.total > response()!.pageSize) {
            <div class="pagination" role="navigation" aria-label="Paginación">
              <button
                class="page-btn"
                id="btn-prev"
                [disabled]="currentPage() === 0"
                (click)="changePage(currentPage() - 1)"
                aria-label="Página anterior"
              >← Anterior</button>

              <span class="page-info">
                Página {{ currentPage() + 1 }} de {{ totalPages() }}
              </span>

              <button
                class="page-btn"
                id="btn-next"
                [disabled]="currentPage() >= totalPages() - 1"
                (click)="changePage(currentPage() + 1)"
                aria-label="Página siguiente"
              >Siguiente →</button>
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [`
    .search-page {
      min-height: calc(100vh - 64px);
      padding: 0 1.5rem 4rem;
    }
    .search-hero {
      text-align: center;
      padding: 4rem 1rem 2rem;
    }
    .hero-title {
      font-size: 2.5rem;
      font-weight: 800;
      background: var(--gradient-brand);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
      margin: 0 0 0.5rem;
    }
    .hero-subtitle { color: var(--text-secondary); margin: 0 0 2rem; }

    .search-bar-wrapper {
      position: relative;
      max-width: 680px;
      margin: 0 auto;
    }
    .search-icon {
      position: absolute;
      left: 1rem;
      top: 50%;
      transform: translateY(-50%);
      font-size: 1.2rem;
      pointer-events: none;
    }
    .search-input {
      width: 100%;
      padding: 1rem 3.5rem;
      background: var(--surface-card);
      border: 1px solid var(--border-subtle);
      border-radius: 999px;
      color: var(--text-primary);
      font-size: 1.05rem;
      outline: none;
      transition: border-color 0.2s, box-shadow 0.2s;
      box-sizing: border-box;
    }
    .search-input::placeholder { color: var(--text-muted); }
    .search-input:focus {
      border-color: var(--accent);
      box-shadow: 0 0 0 4px var(--accent-subtle);
    }
    .search-spinner {
      position: absolute;
      right: 1.2rem;
      top: 50%;
      transform: translateY(-50%);
      width: 18px;
      height: 18px;
      border: 2px solid var(--border-subtle);
      border-top-color: var(--accent);
      border-radius: 50%;
      animation: spin 0.7s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }

    .results-section { max-width: 760px; margin: 0 auto; }

    .alert {
      padding: 1rem 1.25rem;
      border-radius: var(--radius-md);
      margin-bottom: 1.5rem;
    }
    .alert--error {
      background: rgba(239, 68, 68, 0.12);
      border: 1px solid rgba(239, 68, 68, 0.3);
      color: #f87171;
    }

    .empty-state {
      text-align: center;
      padding: 4rem 1rem;
      color: var(--text-secondary);
    }
    .empty-icon { font-size: 4rem; margin-bottom: 1rem; }
    .empty-state h2 { font-size: 1.4rem; margin: 0 0 0.5rem; color: var(--text-primary); }
    .empty-state p { margin: 0; }

    .results-header {
      margin-bottom: 1rem;
      color: var(--text-secondary);
      font-size: 0.9rem;
    }
    .results-header em { color: var(--text-primary); font-style: normal; }

    .results-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 1rem; }

    .result-card {
      padding: 1.25rem 1.5rem;
      background: var(--surface-card);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-lg);
      cursor: pointer;
      transition: all 0.2s ease;
    }
    .result-card:hover {
      border-color: var(--accent);
      box-shadow: 0 0 0 3px var(--accent-subtle);
      transform: translateY(-1px);
    }
    .result-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 1rem;
      margin-bottom: 0.5rem;
    }
    .result-title {
      margin: 0;
      font-size: 1.05rem;
      font-weight: 700;
      color: var(--text-primary);
    }
    .result-meta {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.85rem;
      color: var(--text-secondary);
      margin-bottom: 0.75rem;
    }
    .badge {
      padding: 0.2rem 0.6rem;
      border-radius: 999px;
      font-size: 0.78rem;
      font-weight: 600;
      background: var(--accent-subtle);
      color: var(--accent);
    }
    .badge--tag {
      background: rgba(148, 163, 184, 0.1);
      color: var(--text-muted);
    }
    .result-highlights { display: flex; flex-direction: column; gap: 0.4rem; }
    .highlight-fragment {
      margin: 0;
      padding: 0.4rem 0.75rem;
      border-left: 3px solid var(--accent);
      font-size: 0.88rem;
      color: var(--text-secondary);
      background: var(--surface-hover);
      border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
    }
    :host ::ng-deep mark {
      background: rgba(250, 204, 21, 0.25);
      color: #fbbf24;
      border-radius: 2px;
      padding: 0 2px;
    }

    .pagination {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 1rem;
      margin-top: 2rem;
    }
    .page-btn {
      padding: 0.5rem 1.25rem;
      background: var(--surface-card);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-md);
      color: var(--text-secondary);
      cursor: pointer;
      font-size: 0.9rem;
      transition: all 0.2s;
    }
    .page-btn:hover:not(:disabled) {
      border-color: var(--accent);
      color: var(--accent);
    }
    .page-btn:disabled { opacity: 0.4; cursor: not-allowed; }
    .page-info { font-size: 0.9rem; color: var(--text-secondary); }
  `]
})
export class SearchComponent implements OnDestroy {
  private searchService = inject(SearchService);
  private router = inject(Router);
  private sanitizer = inject(DomSanitizer);

  searchControl = new FormControl('');
  response = signal<SearchResponse | null>(null);
  isLoading = signal(false);
  error = signal<string | null>(null);
  currentPage = signal(0);

  private destroy$ = new Subject<void>();

  constructor() {
    this.searchControl.valueChanges.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(query => {
      this.currentPage.set(0);
      this.doSearch(query ?? '');
    });
  }

  get totalPages(): () => number {
    return () => Math.ceil((this.response()?.total ?? 0) / (this.response()?.pageSize ?? 20));
  }

  doSearch(query: string): void {
    if (!query.trim()) {
      this.response.set(null);
      return;
    }
    this.isLoading.set(true);
    this.error.set(null);
    this.searchService.searchDocuments(query, this.currentPage()).pipe(
      catchError(err => {
        this.error.set(err?.error?.error?.message ?? 'Error al realizar la búsqueda.');
        return of(null);
      })
    ).subscribe(res => {
      this.isLoading.set(false);
      this.response.set(res);
    });
  }

  changePage(page: number): void {
    this.currentPage.set(page);
    this.doSearch(this.searchControl.value ?? '');
  }

  goToDocument(id: string): void {
    this.router.navigate(['/documents', id]);
  }

  sanitize(html: string): SafeHtml {
    return this.sanitizer.bypassSecurityTrustHtml(html);
  }

  safeHighlight(text: string): SafeHtml {
    return this.sanitizer.bypassSecurityTrustHtml(text);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
