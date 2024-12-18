import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FetchEnvironmentService } from '@app/core/services/fetch/environment';
// import { EnvironmentEditFormComponent } from '../../components/forms/environment-edit-form/environment-edit-form.component';
import { EnvironmentEditFormComponent } from '@app/components/forms/environment-edit-form/environment-edit-form.component';

@Component({
  selector: 'app-environment-edit',
  imports: [CommonModule, EnvironmentEditFormComponent],
  providers: [FetchEnvironmentService],
  templateUrl: './environment-edit.component.html',
  styleUrl: './environment-edit.component.css',
})
export class EnvironmentEditComponent {
  route = inject(ActivatedRoute);
  id!: string;

  constructor() {
    const routeId = this.route.snapshot.paramMap.get('id');
    if (routeId) {
      this.id = routeId;
    }
  }
}
