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

  isCurrentUser(login?: string) {
    return this.getPreferredUsername()?.toLowerCase() === login?.toLowerCase();
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

  getUserId() {
    const sub = this.keycloak.tokenParsed?.sub;
    return typeof sub === 'string' ? sub : undefined;
  }

  getPreferredUsername(): string | undefined {
    const preferredUsername = this.keycloak.tokenParsed?.['preferred_username'];
    return typeof preferredUsername === 'string' ? preferredUsername : undefined;
  }

  getUserGithubId(): string | number | undefined {
    const githubId = this.keycloak.tokenParsed?.['github_id'];
    return typeof githubId === 'string' || typeof githubId === 'number' ? githubId : undefined;
  }

  getUserGithubProfilePictureUrl(): string {
    const userId = this.getUserGithubId();
    if (userId) {
      return `https://avatars.githubusercontent.com/u/${userId}`;
    }

    return '';
  }

  getUserGithubProfileUrl() {
    const username = this.getPreferredUsername();
    if (username) {
      return `https://github.com/${username}`;
    }

    return '';
  }

  login() {
    if (environment.keycloak.skipLoginPage) {
      return this.keycloak.login({ idpHint: 'github' });
    }

    return this.keycloak.login();
  }

  logout() {
    return this.keycloak.logout({ redirectUri: window.location.href });
  }
}
