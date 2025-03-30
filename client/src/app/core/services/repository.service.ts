import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class RepositoryService {
  currentRepositoryId = signal<number | null>(null);
}
