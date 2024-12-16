import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { catchError, map, Observable, of, switchMap } from 'rxjs';
import { AuthService } from './auth.service';

export interface GithubOrg {
    login: string;
    id: number;
    avatar_url: string;
    description: string;
}

export interface GithubRepo {
    id: number;
    name: string;
    full_name: string;
    description: string;
    private: boolean;
    html_url: string;
}

@Injectable({
    providedIn: 'root'
})
export class GithubService {
    private token = signal<string | null>(null);

    setToken(token: string) {
        this.token.set(token);
    }

    private readonly API_BASE = 'https://api.github.com';

    constructor(
        private http: HttpClient,
        private authService: AuthService
    ) { }

    validateToken(token: string): Observable<boolean> {
        return this.http.get('https://api.github.com/user', {
            headers: {
                Authorization: `Bearer ${token}`,
                Accept: 'application/vnd.github.v3+json'
            }
        }).pipe(
            map(() => true),
            catchError(() => of(false))
        );
    }


    getOrganizations(): Observable<GithubOrg[]> {
        return this.authService.getGithubToken().pipe(
            switchMap(() =>
                this.http.get<GithubOrg[]>(`${this.API_BASE}/user/orgs`, {
                    headers: this.getHeaders()
                })
            )
        );
    }

    getOrgRepositories(orgName: string): Observable<GithubRepo[]> {
        return this.authService.getGithubToken().pipe(
            switchMap(() =>
                this.http.get<GithubRepo[]>(
                    `${this.API_BASE}/orgs/${orgName}/repos`,
                    { headers: this.getHeaders() }
                )
            )
        );
    }


    private getHeaders() {
        const token = this.token();
        if (!token) {
            throw new Error('GitHub token not set');
        }
        return {
            Authorization: `Bearer ${token}`,
            Accept: 'application/vnd.github.v3+json'
        };
    }

    // private getHeaders(token: string): HttpHeaders {
    //     return new HttpHeaders({
    //         'Authorization': `Bearer ${token}`,
    //         'Accept': 'application/vnd.github.v3+json'
    //     });
    // }
}