import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, CommonModule],
  template: `
    <nav class="navbar">
      <div class="navbar-brand">
        <a routerLink="/search" class="brand-link">
          <span class="brand-icon">📄</span>
          <span class="brand-name">VisorDocs</span>
        </a>
      </div>
      <ul class="navbar-links">
        <li>
          <a routerLink="/search" routerLinkActive="active" [routerLinkActiveOptions]="{exact: true}" id="nav-search">
            🔍 Buscar
          </a>
        </li>
        <li>
          <a routerLink="/upload" routerLinkActive="active" id="nav-upload">
            ⬆️ Subir Documento
          </a>
        </li>
      </ul>
    </nav>
  `,
  styles: [`
    .navbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 2rem;
      height: 64px;
      background: var(--surface-glass);
      backdrop-filter: blur(16px);
      border-bottom: 1px solid var(--border-subtle);
      position: sticky;
      top: 0;
      z-index: 100;
    }
    .brand-link {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      text-decoration: none;
    }
    .brand-icon { font-size: 1.5rem; }
    .brand-name {
      font-size: 1.25rem;
      font-weight: 700;
      background: var(--gradient-brand);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
    }
    .navbar-links {
      display: flex;
      gap: 0.25rem;
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .navbar-links a {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.5rem 1rem;
      border-radius: var(--radius-md);
      color: var(--text-secondary);
      text-decoration: none;
      font-size: 0.9rem;
      font-weight: 500;
      transition: all 0.2s ease;
    }
    .navbar-links a:hover {
      background: var(--surface-hover);
      color: var(--text-primary);
    }
    .navbar-links a.active {
      background: var(--accent-subtle);
      color: var(--accent);
      font-weight: 600;
    }
  `]
})
export class NavbarComponent {}
