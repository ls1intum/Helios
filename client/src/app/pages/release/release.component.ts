import { Component, input, numberAttribute, ChangeDetectionStrategy } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';

@Component({
  selector: 'app-release',
  imports: [PageHeadingComponent, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './release.component.html',
})
export class ReleaseComponent {
  repositoryId = input.required({ transform: numberAttribute });
}
