import { Injectable, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { delay, Observable, of, tap } from 'rxjs';
import { RepositoryInfoDTO } from '../modules/openapi';

@Injectable({
    providedIn: 'root'
})
export class RepositoryService {
    private http = inject(HttpClient);

    private readonly API_URL = 'your-api-url/repositories';

    private _repositories = signal<RepositoryInfoDTO[]>([]);
    public repositories = this._repositories.asReadonly();

    private _loading = signal<boolean>(false);
    public loading = this._loading.asReadonly();

    constructor() {
        this.loadFromStorage();
        // this.fetchRepositories().subscribe();
    }

    // private fetchRepositories(): Observable<RepositoryInfoDTO[]> {
    //     this._loading.set(true);

    //     return this.http.get<RepositoryInfoDTO[]>(this.API_URL).pipe(
    //         tap({
    //             next: (repos) => {
    //                 this._repositories.set(repos);
    //                 this._loading.set(false);
    //             },
    //             error: () => this._loading.set(false)
    //         })
    //     );
    // }
    private fetchRepositories(): Observable<RepositoryInfoDTO[]> {
        this._loading.set(true);

        // Mock API call with delay
        return of(mockRepositories).pipe(
            delay(1000), // Simulate network delay
            tap({
                next: (repos) => {
                    this._repositories.set(repos);
                    this._loading.set(false);
                },
                error: () => this._loading.set(false)
            })
        );
    }
    connectRepository(repoData: Partial<RepositoryInfoDTO>): Observable<RepositoryInfoDTO> {
        this._loading.set(true);

        // return this.http.post<RepositoryInfoDTO>(`${this.API_URL}/connect`, {
        //     provider,
        //     ...repoData
        // }).pipe(
        //     tap({
        //         next: (newRepo) => {
        //             this._repositories.update(repos => [...repos, newRepo]);
        //             this._loading.set(false);
        //         },
        //         error: () => this._loading.set(false)
        //     })
        // );

        // Create mock repository data
        const newRepo: RepositoryInfoDTO = {
            id: Number(repoData.id),
            name: repoData.name || '',
            description: repoData.description,
            htmlUrl: repoData.htmlUrl || '',
            nameWithOwner: repoData.nameWithOwner || ''
        };

        // Simulate API delay
        return of(newRepo).pipe(
            delay(1000),
            tap({
                next: (repo) => {
                    this._repositories.update(repos => {
                        const updatedRepos = [...repos, repo];
                        this.saveToStorage(updatedRepos);
                        return updatedRepos;
                    });
                    this._loading.set(false);
                },
                error: () => this._loading.set(false)
            })
        );
    }

    private saveToStorage(repositories: RepositoryInfoDTO[]) {
        localStorage.setItem('connected-repositories', JSON.stringify(repositories));
    }
    private loadFromStorage() {
        const stored = localStorage.getItem('connected-repositories');
        if (stored) {
            this._repositories.set(JSON.parse(stored));
        }
    }

    disconnectRepository(id: string): Observable<void> {
        this._loading.set(true);

        return this.http.delete<void>(`${this.API_URL}/${id}`).pipe(
            tap({
                next: () => {
                    this._repositories.update(repos =>
                        repos.filter(repo => repo.id.toString() !== id)
                    );
                    this._loading.set(false);
                },
                error: () => this._loading.set(false)
            })
        );
    }

    refreshRepositories(): Observable<RepositoryInfoDTO[]> {
        return this.fetchRepositories();
    }
}

const mockRepositories: RepositoryInfoDTO[] = [{
    "id": 69562331,
    "name": "Artemis",
    "nameWithOwner": "ls1intum/Artemis",
    "description": "Artemis - Interactive Learning with Automated Feedback",
    "htmlUrl": "https://github.com/ls1intum/Artemis"
}, {
    "id": 69562332,
    "name": "Thesis Track",
    "nameWithOwner": "ls1intum/ThesisTrack",
    "description": "Thesis Track - A tool for managing theses",
    "htmlUrl": "https://github.com/ls1intum/ThesisTrack"
}]