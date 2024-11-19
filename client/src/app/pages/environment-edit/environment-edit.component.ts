import { CommonModule } from '@angular/common';
import { Component, inject, input, Signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FetchEnvironmentService } from '@app/core/services/fetch/environment';
import { EnvironmentEditFormComponent } from '../../components/forms/environment-edit-form/environment-edit-form.component';

@Component({
  selector: 'app-environment-edit',
  imports: [CommonModule, EnvironmentEditFormComponent],
  providers: [FetchEnvironmentService],
  templateUrl: './environment-edit.component.html',
  styleUrl: './environment-edit.component.css',
})
export class EnvironmentEditComponent {
  route = inject(ActivatedRoute);
  fetchEnvironments = inject(FetchEnvironmentService);
  id: Signal<string> = input.required();

  environmentQuery = this.fetchEnvironments.getEnvironmentById(this.id);
}
