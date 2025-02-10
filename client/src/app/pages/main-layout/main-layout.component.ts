import { NgClass } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router, RouterOutlet } from '@angular/router';
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

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [RouterOutlet, ToastModule, IconsModule, ButtonModule, TooltipModule, DividerModule, AvatarModule, CardModule, NgClass, FooterComponent, NavigationBarComponent],
  templateUrl: './main-layout.component.html',
})
export class MainLayoutComponent implements OnInit {
  private keycloakService = inject(KeycloakService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  repositoryId = signal<number | undefined>(undefined);

  ngOnInit(): void {
    // Initialize on first load (Refresh)
    this.updateRepositoryId();

    // Listen for route changes (After initial load)
    this.router.events.pipe(filter(event => event instanceof NavigationEnd)).subscribe(() => {
      this.updateRepositoryId();
    });
  }

  private updateRepositoryId(): void {
    let child = this.route.firstChild;

    while (child?.firstChild) {
      child = child.firstChild;
    }

    if (child) {
      const idFromSnapshot = child.snapshot.paramMap.get('repositoryId');
      if (idFromSnapshot) {
        this.repositoryId.set(Number(idFromSnapshot));
      } else {
        this.repositoryId.set(undefined);
      }
    } else {
      this.repositoryId.set(undefined);
    }
  }

  login() {
    this.keycloakService.login();
  }

  isLoggedIn = computed(() => this.keycloakService.isLoggedIn());
}
