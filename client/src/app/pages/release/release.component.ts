import { Component, input, numberAttribute } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';

@Component({
  selector: 'app-release',
  imports: [PageHeadingComponent, RouterOutlet],
  templateUrl: './release.component.html',
})
export class ReleaseComponent {
  repositoryId = input.required({ transform: numberAttribute });
}
