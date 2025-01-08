import { Injectable } from '@angular/core';
import Keycloak from 'keycloak-js';
import { UserProfile } from './user-profile';
import { environment } from 'environments/environment';
@Injectable({
  providedIn: 'root',
})
export class KeycloakService {
  private _keycloak: Keycloak | undefined;

  get keycloak() {
    if (!this._keycloak) {
      this._keycloak = new Keycloak(environment.keycloak);
    }
    return this._keycloak;
  }

  private _profile: UserProfile | undefined;

  get profile(): UserProfile | undefined {
    return this._profile;
  }

  async init() {
    const authenticated = await this.keycloak.init({
      onLoad: 'check-sso',
    });

    if (!authenticated) {
      return;
    }

    // Regulary check if the token needs to be refreshed and refresh it if necessary
    setInterval(() => {
      // Update the token when will last less than 5 minutes
      this.keycloak.updateToken(300);
    }, 60000);

    this._profile = (await this.keycloak.loadUserProfile()) as UserProfile;
    this._profile.token = this.keycloak.token || '';
  }

  isLoggedIn() {
    return this.keycloak.authenticated;
  }

  login() {
    return this.keycloak.login();
  }

  logout() {
    return this.keycloak.logout({ redirectUri: window.location.href });
  }
}
