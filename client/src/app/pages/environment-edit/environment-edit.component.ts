import { CommonModule } from '@angular/common';
import { Component, inject, input, Signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FetchEnvironmentService } from '@app/core/services/fetch/environment';
// import { EnvironmentEditFormComponent } from '../../components/forms/environment-edit-form/environment-edit-form.component';
import { EnvironmentControllerService } from '@app/core/modules/openapi/api/environment-controller.service';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { catchError, tap } from 'rxjs';
import { EnvironmentEditFormComponent } from '@app/components/forms/environment-edit-form/environment-edit-form.component';
import { toSignal } from '@angular/core/rxjs-interop';


@Component({
  selector: 'app-environment-edit',
  imports: [CommonModule, EnvironmentEditFormComponent],
  providers: [FetchEnvironmentService],
  templateUrl: './environment-edit.component.html',
  styleUrl: './environment-edit.component.css',
})
export class EnvironmentEditComponent {
  route = inject(ActivatedRoute);
  id!: string  

  constructor() {
    const routeId = this.route.snapshot.paramMap.get('id');
    if (routeId) {
      this.id = routeId;
    }
  }
}
