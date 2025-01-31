import { Component, computed, inject, input, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputSwitchModule } from 'primeng/inputswitch';
import { InputTextModule } from 'primeng/inputtext';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { ActivatedRoute, Router } from '@angular/router';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import {
  getAllEnvironmentsQueryKey,
  getEnvironmentByIdOptions,
  getEnvironmentByIdQueryKey,
  updateEnvironmentMutation,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { MessageService } from 'primeng/api';
import { Checkbox } from 'primeng/checkbox';
import { SelectModule } from 'primeng/select';
import { toObservable } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-environment-edit-form',
  imports: [AutoCompleteModule, ReactiveFormsModule, InputTextModule, InputSwitchModule, ButtonModule, Checkbox, SelectModule],
  templateUrl: './environment-edit-form.component.html',
})
export class EnvironmentEditFormComponent implements OnInit {
  private formBuilder = inject(FormBuilder);
  private queryClient = inject(QueryClient);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  constructor(private messageService: MessageService) {
    // This subscription is needed, because the form is initialized before the data is fetched.
    // As soon as the data is fetched, the form is updated with the fetched data.
    //
    // We used to call effect() here, but selecting an option within NgPrime Select
    // for some reason triggers the effect to run and reset the form to the initial state.
    toObservable(this.environment).subscribe(environment => {
      if (!environment) {
        return;
      }

      this.environmentForm.patchValue(environment);
    });
  }

  statusCheckTypes = [
    { label: 'Disabled', value: null },
    { label: 'HTTP Status', value: 'HTTP_STATUS' },
    { label: 'Artemis Info', value: 'ARTEMIS_INFO' },
  ];

  selectedCity: string = '';

  onStatusCheckTypeChange = (event: unknown) => {
    console.log(event);
  };

  environmentId = input<number>(0); // This is the environment id
  environmentForm!: FormGroup;

  environmentQuery = injectQuery(() => ({
    ...getEnvironmentByIdOptions({ path: { id: this.environmentId() } }),
    placeholderData: {
      id: 0,
      name: '',
      serverUrl: '',
      description: '',
      installedApps: [] as string[],
      enabled: false,
    },
  }));

  mutateEnvironment = injectMutation(() => ({
    ...updateEnvironmentMutation(),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: getEnvironmentByIdQueryKey({ path: { id: this.environmentId() } }) });
      this.queryClient.invalidateQueries({ queryKey: getAllEnvironmentsQueryKey() });
      this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Environment updated successfully' });
      this.redirectToEnvironmentList();
    },
  }));

  environment = computed(() => this.environmentQuery.data());

  ngOnInit(): void {
    const environment = this.environment();

    this.environmentForm = this.formBuilder.group({
      name: [environment?.name || '', Validators.required],
      installedApps: [environment?.installedApps || []],
      description: [environment?.description || ''],
      serverUrl: [environment?.serverUrl || ''],
      enabled: [environment?.enabled || false],
      statusCheckType: [environment?.statusCheckType || null],
      statusUrl: [environment?.statusUrl || ''],
    });
  }

  redirectToEnvironmentList = () => {
    this.router.navigate(['list'], { relativeTo: this.route.parent });
  };

  submitForm = () => {
    if (this.environmentForm && this.environmentForm.valid) {
      this.mutateEnvironment.mutate({
        path: { id: this.environmentId() },
        body: this.environmentForm.value,
      });
    }
  };
}
