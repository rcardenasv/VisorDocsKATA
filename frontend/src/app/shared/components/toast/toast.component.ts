import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService, Toast } from '../../../core/services/toast.service';

@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="toast-container" aria-live="polite" aria-atomic="false">
      @for (toast of toastService.toasts(); track toast.id) {
        <div
          class="toast"
          [class]="'toast toast--' + toast.type"
          role="alert"
          [id]="'toast-' + toast.id"
        >
          <span class="toast-icon">{{ getIcon(toast.type) }}</span>
          <span class="toast-message">{{ toast.message }}</span>
          <button class="toast-close" (click)="toastService.remove(toast.id)" aria-label="Cerrar notificación">✕</button>
        </div>
      }
    </div>
  `,
  styles: [`
    .toast-container {
      position: fixed;
      bottom: 2rem;
      right: 2rem;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      z-index: 9999;
      max-width: 420px;
    }
    .toast {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 1rem 1.25rem;
      border-radius: var(--radius-lg);
      backdrop-filter: blur(16px);
      box-shadow: var(--shadow-lg);
      animation: slideInRight 0.3s ease;
      border: 1px solid transparent;
    }
    @keyframes slideInRight {
      from { transform: translateX(100%); opacity: 0; }
      to   { transform: translateX(0); opacity: 1; }
    }
    .toast--success {
      background: rgba(34, 197, 94, 0.15);
      border-color: rgba(34, 197, 94, 0.3);
      color: #4ade80;
    }
    .toast--error {
      background: rgba(239, 68, 68, 0.15);
      border-color: rgba(239, 68, 68, 0.3);
      color: #f87171;
    }
    .toast--info {
      background: rgba(99, 102, 241, 0.15);
      border-color: rgba(99, 102, 241, 0.3);
      color: #a5b4fc;
    }
    .toast-icon { font-size: 1.2rem; flex-shrink: 0; }
    .toast-message { flex: 1; font-size: 0.9rem; font-weight: 500; }
    .toast-close {
      background: none;
      border: none;
      cursor: pointer;
      color: inherit;
      opacity: 0.7;
      font-size: 1rem;
      padding: 0;
      flex-shrink: 0;
      transition: opacity 0.2s;
    }
    .toast-close:hover { opacity: 1; }
  `]
})
export class ToastComponent {
  toastService = inject(ToastService);

  getIcon(type: Toast['type']): string {
    return { success: '✅', error: '❌', info: 'ℹ️' }[type];
  }
}
