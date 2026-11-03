import { Component, ChangeDetectionStrategy } from '@angular/core';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';

@Component({
  selector: 'app-imprint',
  imports: [PageHeadingComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './imprint.component.html',
})
export class ImprintComponent {}
