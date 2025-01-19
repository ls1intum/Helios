import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-unauthorized-page',
  imports: [CommonModule, ButtonModule],
  templateUrl: './unauthorized-page.component.html',
})
export class UnauthorizedPageComponent {
  router = inject(Router);
  navigateToHome() {
    this.router.navigate(['/']);
  }
}
