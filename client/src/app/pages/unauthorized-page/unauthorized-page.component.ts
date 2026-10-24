import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';

@Component({
  selector: 'app-unauthorized-page',
  imports: [ButtonModule],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './unauthorized-page.component.html',
})
export class UnauthorizedPageComponent {
  router = inject(Router);
  navigateToHome() {
    this.router.navigate(['/']);
  }
}
