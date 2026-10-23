import { Component, input, ChangeDetectionStrategy } from '@angular/core';

@Component({
  selector: 'app-helios-icon',
  imports: [],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './helios-icon.component.html',
})
export class HeliosIconComponent {
  size = input.required<string>();
}
