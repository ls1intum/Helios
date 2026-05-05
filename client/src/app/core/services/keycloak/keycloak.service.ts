import { Injectable, signal } from '@angular/core';
import Keycloak from 'keycloak-js';
import { UserProfile } from './user-profile';
import { environment } from 'environments/environment';
@Injectable({
  providedIn: 'root',
})
export class KeycloakService {
  private static readonly AUTH_SYNC_STORAGE_KEY = 'helios:auth:sync';

  private _keycloak: Keycloak | undefined;
  private tokenRefreshIntervalId: ReturnType<typeof setInterval> | undefined;
  private keycloakEventsBound = false;
  private isClearingToken = false;
  private suppressLogoutBroadcast = false;

  private _isLoggedIn = signal(false);
  private _profile = signal<UserProfile | undefined>(undefined);

  constructor() {
    if (typeof window !== 'undefined') {
      window.addEventListener('storage', this.handleStorageEvent);
    }
  }

  get keycloak() {
    if (!this._keycloak) {
      this._keycloak = new Keycloak(environment.keycloak);
    }
    return this._keycloak;
  }

  get profile(): UserProfile | undefined {
    return this._profile();
  }

  isCurrentUser(login?: string) {
    return this.getPreferredUsername()?.toLowerCase() === login?.toLowerCase();
  }

  async init() {
    this.bindKeycloakEvents();

    const authenticated = await this.keycloak.init({
      onLoad: 'check-sso',
    });

    if (!authenticated) {
      this.applyLoggedOutState();
      return;
    }

    this._isLoggedIn.set(true);
    this.startTokenRefresh();
    await this.reloadProfile();
  }

  isLoggedIn() {
    return this._isLoggedIn();
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
    this.broadcastLogoutEvent();
    return this.keycloak.logout({ redirectUri: window.location.href });
  }

  private async reloadProfile() {
    const profile = (await this.keycloak.loadUserProfile()) as UserProfile;
    profile.token = this.keycloak.token || '';
    this._profile.set(profile);
  }

  private bindKeycloakEvents() {
    if (this.keycloakEventsBound) {
      return;
    }
    this.keycloakEventsBound = true;

    this.keycloak.onAuthSuccess = async () => {
      this._isLoggedIn.set(true);
      this.startTokenRefresh();
      await this.reloadProfile();
    };

    this.keycloak.onAuthRefreshSuccess = () => {
      this._isLoggedIn.set(!!this.keycloak.authenticated);
      const profile = this._profile();
      if (profile) {
        this._profile.set({
          ...profile,
          token: this.keycloak.token || '',
        });
      }
    };

    this.keycloak.onAuthRefreshError = () => {
      this.applyLoggedOutState({ clearToken: true, broadcast: true });
    };

    this.keycloak.onAuthLogout = () => {
      this.applyLoggedOutState({ broadcast: !this.suppressLogoutBroadcast });
    };
  }

  private startTokenRefresh() {
    if (this.tokenRefreshIntervalId !== undefined) {
      return;
    }

    // Regularly check if the token needs to be refreshed.
    this.tokenRefreshIntervalId = setInterval(async () => {
      if (!this.isLoggedIn()) {
        return;
      }
      try {
        // Update the token when it will be valid for less than 1 minute.
        await this.keycloak.updateToken(60);
      } catch {
        this.applyLoggedOutState({ clearToken: true, broadcast: true });
      }
    }, 60000);
  }

  private stopTokenRefresh() {
    if (this.tokenRefreshIntervalId !== undefined) {
      clearInterval(this.tokenRefreshIntervalId);
      this.tokenRefreshIntervalId = undefined;
    }
  }

  private clearToken() {
    if (!this._keycloak || this.isClearingToken) {
      return;
    }

    const keycloak = this._keycloak as Keycloak & { clearToken?: () => void };
    if (typeof keycloak.clearToken !== 'function') {
      return;
    }

    this.isClearingToken = true;
    try {
      keycloak.clearToken();
    } finally {
      this.isClearingToken = false;
    }
  }

  private applyLoggedOutState(options: { clearToken?: boolean; broadcast?: boolean } = {}) {
    this.stopTokenRefresh();
    this._isLoggedIn.set(false);
    this._profile.set(undefined);

    if (options.clearToken) {
      const previousSuppressLogoutBroadcast = this.suppressLogoutBroadcast;
      this.suppressLogoutBroadcast = true;
      this.clearToken();
      this.suppressLogoutBroadcast = previousSuppressLogoutBroadcast;
    }

    if (options.broadcast) {
      this.broadcastLogoutEvent();
    }
  }

  private handleStorageEvent = (event: StorageEvent) => {
    if (event.key !== KeycloakService.AUTH_SYNC_STORAGE_KEY || !event.newValue) {
      return;
    }

    try {
      const authEvent = JSON.parse(event.newValue) as { type?: string };
      if (authEvent.type === 'logout') {
        this.applyLoggedOutState({ clearToken: true });
      }
    } catch {
      // Ignore malformed storage events.
    }
  };

  private broadcastLogoutEvent() {
    if (typeof window === 'undefined' || !window.localStorage) {
      return;
    }

    window.localStorage.setItem(
      KeycloakService.AUTH_SYNC_STORAGE_KEY,
      JSON.stringify({
        type: 'logout',
        at: Date.now(),
      })
    );
  }
}
