import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, switchMap } from 'rxjs';
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
    private readonly API_BASE = 'https://api.github.com';

    constructor(
        private http: HttpClient,
        private authService: AuthService
    ) { }

    getOrganizations(): Observable<GithubOrg[]> {
        return this.authService.getGithubToken().pipe(
            switchMap(token =>
                this.http.get<GithubOrg[]>(`${this.API_BASE}/user/orgs`, {
                    headers: this.getHeaders(token)
                })
            )
        );
    }

    getOrgRepositories(orgName: string): Observable<GithubRepo[]> {
        return this.authService.getGithubToken().pipe(
            switchMap(token =>
                this.http.get<GithubRepo[]>(
                    `${this.API_BASE}/orgs/${orgName}/repos`,
                    { headers: this.getHeaders(token) }
                )
            )
        );
    }

    private getHeaders(token: string): HttpHeaders {
        return new HttpHeaders({
            'Authorization': `Bearer ${token}`,
            'Accept': 'application/vnd.github.v3+json'
        });
    }
}