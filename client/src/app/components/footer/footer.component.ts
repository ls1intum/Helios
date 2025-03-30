import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';

@Component({
  selector: 'app-footer',
  imports: [RouterLink, ButtonModule],
  templateUrl: './footer.component.html',
})
export class FooterComponent {}
