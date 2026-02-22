import { Injectable } from '@angular/core';
import Keycloak, { KeycloakTokenParsed } from 'keycloak-js';
import { UserProfile } from './user-profile';
import { environment } from 'environments/environment';
@Injectable({
  providedIn: 'root',
})
export class KeycloakService {
  private get tokenClaims(): KeycloakTokenParsed | null {
    return this.keycloak.tokenParsed ?? null;
  }

  private getTokenClaim(claim: string): unknown {
    return this.tokenClaims?.[claim];
  }

  private getStringTokenClaim(claim: string): string | undefined {
    const value = this.getTokenClaim(claim);
    return typeof value === 'string' ? value : undefined;
  }

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
    return this.getStringTokenClaim('sub');
  }

  getPreferredUsername(): string | undefined {
    return this.getStringTokenClaim('preferred_username');
  }

  getUserGithubId(): string | number | undefined {
    const githubId = this.getTokenClaim('github_id');
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
    return this.keycloak.login();
  }

  logout() {
    return this.keycloak.logout({ redirectUri: window.location.href });
  }
}
