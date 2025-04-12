import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';

@Component({
  selector: 'app-page-not-found',
  imports: [ButtonModule],
  templateUrl: './page-not-found.component.html',
})
export class PageNotFoundComponent {
  router = inject(Router);
  navigateToHome() {
    this.router.navigate(['/']);
  }
}
