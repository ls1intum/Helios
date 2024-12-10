import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { delay, Observable, of, tap } from 'rxjs';
import { RepositoryInfoDTO } from '../modules/openapi';

@Injectable({
    providedIn: 'root'
})
export class RepositoryService {
    private readonly API_URL = 'your-api-url/repositories';

    private _repositories = signal<RepositoryInfoDTO[]>([]);
    public repositories = this._repositories.asReadonly();

    private _loading = signal<boolean>(false);
    public loading = this._loading.asReadonly();

    constructor(private http: HttpClient) {
        this.fetchRepositories().subscribe();
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
    connectRepository(provider: 'github' | 'gitlab', repoData: Partial<RepositoryInfoDTO>): Observable<RepositoryInfoDTO> {
        this._loading.set(true);

        return this.http.post<RepositoryInfoDTO>(`${this.API_URL}/connect`, {
            provider,
            ...repoData
        }).pipe(
            tap({
                next: (newRepo) => {
                    this._repositories.update(repos => [...repos, newRepo]);
                    this._loading.set(false);
                },
                error: () => this._loading.set(false)
            })
        );
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