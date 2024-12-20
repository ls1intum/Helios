import { Component, input } from '@angular/core';

@Component({
  selector: 'app-helios-icon',
  imports: [],
  templateUrl: './helios-icon.component.html',
})
export class HeliosIconComponent {
  size = input.required<string>();
}
