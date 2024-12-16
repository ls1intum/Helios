import { CommonModule } from '@angular/common';
import { Component, inject, input, Signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FetchEnvironmentService } from '@app/core/services/fetch/environment';
import { EnvironmentEditFormComponent } from '../../components/forms/environment-edit-form/environment-edit-form.component';
import { EnvironmentControllerService } from '@app/core/modules/openapi/api/environment-controller.service';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { catchError, tap } from 'rxjs';
import { ConnectedOverlayScrollHandler } from 'primeng/dom';


@Component({
  selector: 'app-environment-edit',
  imports: [CommonModule],
  providers: [FetchEnvironmentService],
  templateUrl: './environment-edit.component.html',
  styleUrl: './environment-edit.component.css',
})
export class EnvironmentEditComponent {
  route = inject(ActivatedRoute);
  environmentService = inject(EnvironmentControllerService);
  // environmentStore = inject(EnvironmentStoreService);
  id: Signal<string> = input.required();    
  environemnts = {}

  constructor() {
    console.log('EnvironmentEditComponent initialized');
    console.log('Environment ID:', this.id);
    this.updateEnvironmentQuery.refetch();
  }

  query = injectQuery(() => ({
    queryKey: ['environment'],
    queryFn: () => {
      console.log('Executing query for environment ID:', this.id());
      return this.environmentService.getEnvironmentById(Number(this.id()))
        .pipe(
          tap ((data: any) => {
            console.log('Fetched Environment:', data);
            this.environemnts = data;
          }),
          catchError((error) => {
            console.error('Error fetching environment:', error);
            return [];
          })
        ).subscribe();
    }
  }));

  updateEnvironmentQuery = injectQuery(() => ({
    queryKey: ['updateEnvironment'],
    queryFn: () => {
      const updateBody = {
        id: Number(this.id()),
        name: 'production', // Replace with the actual environment name
        installedApps: ["App1", "App2", "App3"],
        serverUrl: 'https://example.com',
      };
      console.log('Updating environment with:', updateBody);
      return this.environmentService.updateEnvironment(Number(this.id()), updateBody)
        .pipe(
          tap((response: any) => {
            console.log('Environment updated:', response);
          }),
          catchError((error) => {
            console.error('Error updating environment:', error);
            return [];
          })
        ).subscribe();
    }
  }));
}