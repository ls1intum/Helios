import { Component, computed, inject, OnInit, signal, effect } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { IconsModule } from 'icons.module';
import { AvatarModule } from 'primeng/avatar';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { DividerModule } from 'primeng/divider';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { FooterComponent } from '@app/components/footer/footer.component';
import { NavigationBarComponent } from '@app/components/navigation-bar/navigation-bar.component';
import { filter } from 'rxjs';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { HeliosIconComponent } from '@app/components/helios-icon/helios-icon.component';
import { ProfileNavSectionComponent } from '@app/components/profile-nav-section/profile-nav-section.component';
import { TagModule } from 'primeng/tag';

@Component({
  selector: 'app-main-layout',
  imports: [
    RouterOutlet,
    ToastModule,
    IconsModule,
    ButtonModule,
    TooltipModule,
    DividerModule,
    AvatarModule,
    CardModule,
    FooterComponent,
    NavigationBarComponent,
    HeliosIconComponent,
    ProfileNavSectionComponent,
    TagModule,
    RouterLink,
    ConfirmDialogModule,
  ],
  templateUrl: './main-layout.component.html',
})
export class MainLayoutComponent implements OnInit {
  private keycloakService = inject(KeycloakService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  repositoryId = signal<number | undefined>(undefined);
  isDarkModeEnabled = signal(window.matchMedia('(prefers-color-scheme: dark)').matches);
  isLoggedIn = computed(() => this.keycloakService.isLoggedIn());

  constructor() {
    effect(() => {
      document.querySelector('html')?.classList.toggle('dark-mode-enabled', this.isDarkModeEnabled());
    });
  }

  ngOnInit(): void {
    // Initialize on first load (Refresh)
    this.updateRepositoryId();

    // Listen for route changes (After initial load)
    this.router.events.pipe(filter(event => event instanceof NavigationEnd)).subscribe(() => {
      this.updateRepositoryId();
    });
  }

  /**
   * Updates the `repositoryId` signal based on the current route.
   *
   * This method traverses the activated route hierarchy to find and extract
   * the `repositoryId` parameter. It ensures that `repositoryId` is set correctly
   * even when navigating through deeply nested routes.
   *
   * ### Example Route Structure:
   * Given the URL: `/repo/:repositoryId/ci-cd/pr`
   * The Angular route hierarchy will be:
   * - `repo/:repositoryId`  (firstChild)
   * - `ci-cd`               (firstChild)
   * - `pr`                  (firstChild, stops here)
   *
   * If the `repositoryId` is found, it is stored as a number. Otherwise, the value is set to `undefined`.
   *
   * If the user navigates to a non-repository page (e.g., `/about`, `/privacy`), `repositoryId` is cleared.
   *
   * **Note:**
   * Even if the last child route (e.g., 'pr') does not contain `repositoryId`,
   * Angular keeps parent route parameters accessible.
   * Since `'repo/:repositoryId'` is an ancestor route, its parameter is still available.
   *
   * @returns {void}
   */
  private updateRepositoryId(): void {
    let child = this.route.firstChild;

    // Traverse to the last child route in the hierarchy
    while (child?.firstChild) {
      child = child.firstChild;
    }

    // If no child route exists, reset repositoryId and return
    if (!child) {
      this.repositoryId.set(undefined);
      return;
    }

    // Attempt to extract 'repositoryId' from the deepest child route
    const idFromSnapshot = child.snapshot.paramMap.get('repositoryId');

    // Convert to a number if 'repositoryId' exists
    let repositoryId: number | undefined;
    if (idFromSnapshot && !isNaN(Number(idFromSnapshot))) {
      repositoryId = Number(idFromSnapshot);
    } else {
      repositoryId = undefined;
    }

    // Set repositoryId (either a valid number or undefined)
    this.repositoryId.set(repositoryId);
  }

  login() {
    this.keycloakService.login();
  }

  toggleDarkMode() {
    this.isDarkModeEnabled.set(!this.isDarkModeEnabled());
  }
}
