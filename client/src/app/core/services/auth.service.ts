import { Injectable } from '@angular/core';
import { environment } from 'environments/environment.dev';
import { Observable, of, delay } from 'rxjs';

@Injectable({
    providedIn: 'root'
})
export class AuthService {
    // Mock GitHub token
    getGithubToken(): Observable<string> {
        return of(environment.githubToken).pipe(delay(500));
    }
}