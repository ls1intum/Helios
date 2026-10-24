import { Component, ChangeDetectionStrategy } from '@angular/core';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';

@Component({
  selector: 'app-privacy',
  imports: [PageHeadingComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './privacy.component.html',
})
export class PrivacyComponent {}
