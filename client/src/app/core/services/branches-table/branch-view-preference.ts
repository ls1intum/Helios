import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class BranchViewPreferenceService {
  private readonly STORAGE_KEY = 'branch-view-preference';
  private viewModeState = signal<BranchViewOptions>(
    (() => {
      const stored = localStorage.getItem(this.STORAGE_KEY);
      return stored === 'Tree' || stored === 'Table' ? stored : 'Tree';
    })()
  );

  get viewMode() {
    return this.viewModeState.asReadonly();
  }

  toggleViewMode() {
    const newMode = this.viewModeState() === 'Tree' ? 'Table' : 'Tree';
    this.viewModeState.set(newMode);
    localStorage.setItem(this.STORAGE_KEY, newMode);
  }
}

export type BranchViewOptions = 'Tree' | 'Table';
