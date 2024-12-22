import { Component, inject, Input, input, OnInit, Signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Environment, FetchEnvironmentService } from '@app/core/services/fetch/environment';
import { ButtonModule } from 'primeng/button';
import { InputSwitchModule } from 'primeng/inputswitch';
import { InputTextModule } from 'primeng/inputtext';
import { EnvironmentControllerService } from '@app/core/modules/openapi/api/environment-controller.service';
import { EnvironmentDTO } from '@app/core/modules/openapi';
import { catchError, tap } from 'rxjs';
import { CommonModule } from '@angular/common';
import { ChipsModule } from 'primeng/chips';
import { ActivatedRoute, Router } from '@angular/router';

@Component({
  selector: 'app-environment-edit-form',
  imports: [CommonModule, ReactiveFormsModule, InputTextModule, InputSwitchModule, ButtonModule, ChipsModule],
  templateUrl: './environment-edit-form.component.html',
  styleUrls: ['./environment-edit-form.component.css'],
})
export class EnvironmentEditFormComponent implements OnInit {
  private formBuilder = inject(FormBuilder);
  environmentService = inject(EnvironmentControllerService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  @Input() id!: string; // This is the environment id
  environment = <EnvironmentDTO>({ // This is the environment object
    id: 0,
    name: '',
    serverUrl: '',
    description: '',
    installedApps: [] as string[],
  });
  environmentForm!: FormGroup;

  ngOnInit(): void {
    if (!this.id) {
      alert('Environment id is required');
      this.router.navigate(['../list'], { relativeTo: this.route.parent });
      return;
    }
    this.environmentForm = this.formBuilder.group({
      name: [this.environment.name || '', Validators.required],
      installedApps: [this.environment.installedApps || []],
      description: [this.environment.description || ''],
      serverUrl: [this.environment.serverUrl || ''],
    });

    this.environmentService.getEnvironmentById(Number(this.id))
      .pipe(
        tap((data: EnvironmentDTO) => {
          this.environment = data;
          this.environmentForm.patchValue(this.environment);
        }),
        catchError((error) => {
          alert('Environment not found');
          this.redirectToEnvironmentList();
          return [];
        })
      ).subscribe();
  }

  redirectToEnvironmentList = () => {
    this.router.navigate(['../list'], { relativeTo: this.route.parent });
  }

  submitForm = () => {
    if (this.environmentForm && this.environmentForm.valid) {
      this.environmentService.updateEnvironment(this.environment.id, this.environmentForm.value).subscribe(() => {
        this.redirectToEnvironmentList();
      });
    }
  };
}
