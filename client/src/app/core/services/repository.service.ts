import { Injectable, signal } from '@angular/core';
import { Observable, of, tap } from 'rxjs';
import { RepositoryInfoDto } from '../modules/openapi';

@Injectable({
  providedIn: 'root',
})
export class RepositoryService {
  currentRepositoryId = signal<number | null>(null);
  private _repositories = signal<RepositoryInfoDto[]>([]);
  public repositories = this._repositories.asReadonly();

  private _loading = signal<boolean>(false);
  public loading = this._loading.asReadonly();

  constructor() {
    this.loadFromStorage();
  }

  connectRepository(repoData: Partial<RepositoryInfoDto>): Observable<RepositoryInfoDto> {
    this._loading.set(true);

    const newRepo: RepositoryInfoDto = {
      id: Number(repoData.id),
      name: repoData.name || '',
      description: repoData.description,
      htmlUrl: repoData.htmlUrl || '',
      nameWithOwner: repoData.nameWithOwner || '',
    };

    return of(newRepo).pipe(
      tap({
        next: repo => {
          this._repositories.update(repos => {
            const updatedRepos = [...repos, repo];
            this.saveToStorage(updatedRepos);
            return updatedRepos;
          });
          this._loading.set(false);
        },
        error: () => this._loading.set(false),
      })
    );
  }

  private saveToStorage(repositories: RepositoryInfoDto[]) {
    localStorage.setItem('connected-repositories', JSON.stringify(repositories));
  }
  private loadFromStorage() {
    const stored = localStorage.getItem('connected-repositories');
    if (stored) {
      this._repositories.set(JSON.parse(stored));
    }
  }
}
