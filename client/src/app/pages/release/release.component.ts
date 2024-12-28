import { Component, input, numberAttribute } from '@angular/core';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';

@Component({
  selector: 'app-release',
  imports: [PageHeadingComponent],
  templateUrl: './release.component.html',
})
export class ReleaseComponent {
  repositoryId = input.required({ transform: numberAttribute });
}
