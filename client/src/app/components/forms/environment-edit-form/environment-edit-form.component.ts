import { Component, computed, effect, inject, input, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputSwitchModule } from 'primeng/inputswitch';
import { InputTextModule } from 'primeng/inputtext';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { ActivatedRoute, Router } from '@angular/router';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { getAllEnvironmentsQueryKey, getEnvironmentByIdOptions, getEnvironmentByIdQueryKey, updateEnvironmentMutation } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { MessageService } from 'primeng/api';

@Component({
  selector: 'app-environment-edit-form',
  imports: [AutoCompleteModule, ReactiveFormsModule, InputTextModule, InputSwitchModule, ButtonModule],
  templateUrl: './environment-edit-form.component.html',
})
export class EnvironmentEditFormComponent implements OnInit {
  private formBuilder = inject(FormBuilder);
  private queryClient = injectQueryClient();
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  constructor(private messageService: MessageService) {
    // This effect is needed, because the form is initialized before the data is fetched.
    // As soon as the data is fetched, the form is updated with the fetched data.
    effect(() => {
      if (this.environment()) {
        this.environmentForm.patchValue(this.environment() || {});
      }
    });
  }

  environmentId = input<number>(0); // This is the environment id
  environmentForm!: FormGroup;

  environmentQuery = injectQuery(() => ({
    ...getEnvironmentByIdOptions({ path: {id: this.environmentId() } }),
    placeholderData: {
      id: 0,
      name: '',
      serverUrl: '',
      description: '',
      installedApps: [] as string[],
    },
  }));

  mutateEnvironment = injectMutation(() => ({
    ...updateEnvironmentMutation(),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: getEnvironmentByIdQueryKey({ path: { id: this.environmentId() }})});
      this.queryClient.invalidateQueries({ queryKey: getAllEnvironmentsQueryKey()});
      this.messageService.add({severity: 'success', summary: 'Success', detail: 'Environment updated successfully'});
      this.redirectToEnvironmentList();
    }
  }));

  environment = computed(() => this.environmentQuery.data());

  ngOnInit(): void {
    this.environmentForm = this.formBuilder.group({
      name: [this.environment()?.name || '', Validators.required],
      installedApps: [this.environment()?.installedApps || []],
      description: [this.environment()?.description || ''],
      serverUrl: [this.environment()?.serverUrl || ''],
    });
  }

  redirectToEnvironmentList = () => {
    this.router.navigate(['list'], { relativeTo: this.route.parent });
  }

  submitForm = () => {
    if (this.environmentForm && this.environmentForm.valid) {
      this.mutateEnvironment.mutate({ path: {id: this.environmentId()}, body: this.environmentForm.value });
    }
  };
}
