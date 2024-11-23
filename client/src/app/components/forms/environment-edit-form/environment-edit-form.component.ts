import { Component, inject, input, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Environment, FetchEnvironmentService } from '@app/core/services/fetch/environment';
import { ButtonModule } from 'primeng/button';
import { InputSwitchModule } from 'primeng/inputswitch';
import { InputTextModule } from 'primeng/inputtext';

@Component({
  selector: 'app-environment-edit-form',
  imports: [ReactiveFormsModule, InputTextModule, InputSwitchModule, ButtonModule],
  providers: [FetchEnvironmentService],
  templateUrl: './environment-edit-form.component.html',
  styleUrl: './environment-edit-form.component.css',
})
export class EnvironmentEditFormComponent implements OnInit {
  private formBuilder = inject(FormBuilder);
  private fetchEnvironment = inject(FetchEnvironmentService);

  mutateEnvironment = this.fetchEnvironment.upsertEnvironment();

  environment = input<Environment>({
    name: '',
    url: '',
    locked: false,
    production: false,
    commitHash: '',
    connectedSystems: [],
  });
  environmentForm!: FormGroup;

  ngOnInit(): void {
    this.environmentForm = this.formBuilder.group({
      name: [this.environment().name, [Validators.required, Validators.minLength(3)]],
      url: [this.environment().url, [Validators.required]],
      locked: [this.environment().locked, [Validators.required]],
      production: [this.environment().production],
      commitHash: [this.environment().commitHash],
      connectedSystems: [this.environment().connectedSystems],
    });
  }

  submitForm = () => {
    if (this.environmentForm.valid) {
      this.mutateEnvironment.mutate({ ...this.environmentForm.value, id: this.environment().id });
    }
  };
}
