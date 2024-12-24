import { Injectable, inject } from '@angular/core';
import { HttpEvent, HttpHandler, HttpHeaders, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';
import { KeycloakService } from '../keycloak/keycloak.service';

@Injectable()
export class BearerInterceptor implements HttpInterceptor {
  private keycloakService = inject(KeycloakService);

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const token = this.keycloakService.keycloak.token;
    // TODO: Galiia -> find a better solutions
    // this was overwriting the github token for getting the repositories
    if (token && !request.headers.has('Authorization')) {
      const authReq = request.clone({
        headers: new HttpHeaders({
          Authorization: `Bearer ${token}`,
        }),
      });
      return next.handle(authReq);
    }
    return next.handle(request);
  }
}
