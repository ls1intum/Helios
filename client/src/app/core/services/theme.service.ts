import { effect, Injectable, signal } from '@angular/core';

/**
 * Shared theme state. Owns the dark-mode signal and the DOM class toggle so any component
 * (e.g. chart wrappers) can react to theme changes via `effect()`.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly STORAGE_KEY = 'theme';

  readonly isDarkMode = signal<boolean>(this.initialIsDark());

  constructor() {
    effect(() => {
      document.querySelector('html')?.classList.toggle('dark-mode-enabled', this.isDarkMode());
    });
  }

  toggle(): void {
    const next = !this.isDarkMode();
    this.isDarkMode.set(next);
    localStorage.setItem(this.STORAGE_KEY, next ? 'dark' : 'light');
  }

  private initialIsDark(): boolean {
    const saved = localStorage.getItem(this.STORAGE_KEY);
    if (saved === 'light' || saved === 'dark') {
      return saved === 'dark';
    }
    return window.matchMedia('(prefers-color-scheme: dark)').matches;
  }
}
