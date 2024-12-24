import { Component, input, numberAttribute } from '@angular/core';
import { EnvironmentEditFormComponent } from '@app/components/forms/environment-edit-form/environment-edit-form.component';

@Component({
  selector: 'app-environment-edit',
  imports: [EnvironmentEditFormComponent],
  templateUrl: './environment-edit.component.html',
})
export class EnvironmentEditComponent {
  id = input.required({ transform: numberAttribute });
}
