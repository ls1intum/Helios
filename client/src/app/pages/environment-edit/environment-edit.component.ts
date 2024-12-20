import { Component, input, numberAttribute } from '@angular/core';
import { FetchEnvironmentService } from '@app/core/services/fetch/environment';
import { EnvironmentEditFormComponent } from '@app/components/forms/environment-edit-form/environment-edit-form.component';


@Component({
  selector: 'app-environment-edit',
  imports: [EnvironmentEditFormComponent],
  providers: [FetchEnvironmentService],
  templateUrl: './environment-edit.component.html',
})
export class EnvironmentEditComponent {
  id = input.required({ transform: numberAttribute });
}
