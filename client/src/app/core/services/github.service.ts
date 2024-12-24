import { HttpClient, HttpParams } from '@angular/common/http';
import { catchError, EMPTY, expand, map, Observable, of, reduce, switchMap } from 'rxjs';
import { Injectable, signal, inject } from '@angular/core';
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
  providedIn: 'root',
})
export class GithubService {
  private http = inject(HttpClient);
  private authService = inject(AuthService);

  private token = signal<string | null>(null);

  setToken(token: string) {
    this.token.set(token);
  }

  private readonly API_BASE = 'https://api.github.com';
  private readonly PER_PAGE = 100;

  validateToken(token: string): Observable<boolean> {
    return this.http
      .get('https://api.github.com/user', {
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'application/vnd.github.v3+json',
        },
      })
      .pipe(
        map(() => true),
        catchError(() => of(false))
      );
  }

  getOrganizations(): Observable<GithubOrg[]> {
    return this.authService.getGithubToken().pipe(
      switchMap(() =>
        this.http.get<GithubOrg[]>(`${this.API_BASE}/user/orgs`, {
          headers: this.getHeaders(),
        })
      )
    );
  }

  getOrgRepositories(orgName: string): Observable<GithubRepo[]> {
    return this.authService.getGithubToken().pipe(switchMap(() => this.getPaginatedResults<GithubRepo>(`${this.API_BASE}/orgs/${orgName}/repos`)));
  }

  private getPaginatedResults<T>(url: string): Observable<T[]> {
    const getPage = (
      pageUrl: string,
      page: number
    ): Observable<{
      data: T[];
      nextPage: number | null;
    }> => {
      const params = new HttpParams().set('per_page', this.PER_PAGE).set('page', page);

      return this.http
        .get<T[]>(pageUrl, {
          headers: this.getHeaders(),
          params,
          observe: 'response',
        })
        .pipe(
          map(response => {
            const linkHeader = response.headers.get('link');
            const hasNext = linkHeader?.includes('rel="next"') ?? false;
            return {
              data: response.body as T[],
              nextPage: hasNext ? page + 1 : null,
            };
          })
        );
    };

    return getPage(url, 1).pipe(
      expand(response => (response.nextPage ? getPage(url, response.nextPage) : EMPTY)),
      map(response => response.data),
      reduce((acc: T[], current: T[]) => [...acc, ...current], [])
    );
  }

  private getHeaders() {
    const token = this.token();
    if (!token) {
      throw new Error('GitHub token not set');
    }
    return {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github.v3+json',
    };
  }

  // private getHeaders(token: string): HttpHeaders {
  //     return new HttpHeaders({
  //         'Authorization': `Bearer ${token}`,
  //         'Accept': 'application/vnd.github.v3+json'
  //     });
  // }
}
