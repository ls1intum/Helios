import { Component, input, numberAttribute, ChangeDetectionStrategy } from '@angular/core';
import { EnvironmentEditFormComponent } from '@app/components/forms/environment-edit-form/environment-edit-form.component';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';

@Component({
  selector: 'app-environment-edit',
  imports: [EnvironmentEditFormComponent, PageHeadingComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './environment-edit.component.html',
})
export class EnvironmentEditComponent {
  repositoryId = input.required({ transform: numberAttribute });
  id = input.required({ transform: numberAttribute });
}
