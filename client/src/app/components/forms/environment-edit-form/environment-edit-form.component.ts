import { Component, computed, inject, input, OnInit } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
  getAllEnabledEnvironmentsQueryKey,
  getAllEnvironmentsQueryKey,
  getEnvironmentByIdOptions,
  getEnvironmentByIdQueryKey,
  getEnvironmentsByUserLockingQueryKey,
  getWorkflowsByRepositoryIdOptions,
  updateEnvironmentMutation,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconExclamationCircle } from 'angular-tabler-icons/icons';
import { MessageService } from 'primeng/api';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { DividerModule } from 'primeng/divider';
import { InputSwitchModule } from 'primeng/inputswitch';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { ToggleSwitch } from 'primeng/toggleswitch';

@Component({
  selector: 'app-environment-edit-form',
  imports: [
    AutoCompleteModule,
    ReactiveFormsModule,
    InputTextModule,
    CardModule,
    DividerModule,
    InputSwitchModule,
    ButtonModule,
    MessageModule,
    SelectModule,
    ToggleSwitch,
    TablerIconComponent,
  ],
  providers: [provideTablerIcons({ IconExclamationCircle })],
  templateUrl: './environment-edit-form.component.html',
})
export class EnvironmentEditFormComponent implements OnInit {
  private formBuilder = inject(FormBuilder);
  private queryClient = inject(QueryClient);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  repositoryId = input.required<number>();

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
    { label: 'Disabled', value: null, requiresUrl: false, info: null },
    { label: 'HTTP Status', value: 'HTTP_STATUS', requiresUrl: true, info: 'Sends a request to a URL expecting a 200 OK response.' },
    { label: 'Artemis Info', value: 'ARTEMIS_INFO', requiresUrl: true, info: 'Uses the Actuator /management/info endpoint to check health.' },
    { label: 'Push Update', value: 'PUSH_UPDATE', requiresUrl: false, info: 'This mode relies on external push signals. No polling is done.' },
  ];

  showStatusUrlField(value: string | null): boolean {
    const typeObj = this.statusCheckTypes.find(t => t.value === value);
    return !!typeObj?.requiresUrl;
  }

  getStatusCheckInfoText(value: string | null): string | null {
    const typeObj = this.statusCheckTypes.find(t => t.value === value);
    return typeObj?.info || null;
  }

  environmentId = input<number>(0); // This is the environment id
  environmentForm!: FormGroup;

  environmentTypes = [
    { label: 'None', value: null },
    { label: 'Test', value: 'TEST' },
    { label: 'Staging', value: 'STAGING' },
    { label: 'Production', value: 'PRODUCTION' },
  ];

  environmentQuery = injectQuery(() => ({
    ...getEnvironmentByIdOptions({ path: { id: this.environmentId() } }),
    placeholderData: {
      id: 0,
      name: '',
      type: 'TEST' as const,
      serverUrl: '',
      deploymentWorkflowBranch: '',
      description: '',
      installedApps: [] as string[],
      enabled: false,
    },
  }));

  workflowsQuery = injectQuery(() => ({
    ...getWorkflowsByRepositoryIdOptions({ path: { repositoryId: this.repositoryId() } }),
    enabled: () => !!this.repositoryId(),
  }));

  workflowOptions = computed(() => {
    const workflows = this.workflowsQuery.data() || [];
    workflows.filter(w => w.state === 'ACTIVE');
    return workflows.map(w => ({ name: w.name, file: w.fileNameWithExtension || '', value: w })).sort((a, b) => a.file.localeCompare(b.file));
  });

  mutateEnvironment = injectMutation(() => ({
    ...updateEnvironmentMutation(),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: getEnvironmentByIdQueryKey({ path: { id: this.environmentId() } }) });
      this.queryClient.invalidateQueries({ queryKey: getAllEnvironmentsQueryKey() });
      this.queryClient.invalidateQueries({ queryKey: getAllEnabledEnvironmentsQueryKey() });
      this.queryClient.invalidateQueries({ queryKey: getEnvironmentsByUserLockingQueryKey() });
      this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Environment updated successfully' });
      this.redirectToEnvironmentList();
    },
  }));

  environment = computed(() => this.environmentQuery.data());

  ngOnInit(): void {
    const environment = this.environment();

    this.environmentForm = this.formBuilder.group(
      {
        displayName: [environment?.displayName || ''],
        type: [this.environment()?.type || 'TEST'],
        deploymentWorkflow: [environment?.deploymentWorkflow || null],
        installedApps: [environment?.installedApps || []],
        description: [environment?.description || ''],
        serverUrl: [environment?.serverUrl || ''],
        deploymentWorkflowBranch: [environment?.deploymentWorkflowBranch || ''],
        enabled: [environment?.enabled || false],
        statusCheckType: [environment?.statusCheckType || null],
        statusUrl: [environment?.statusUrl || ''],
        lockExpirationThreshold: [environment?.lockExpirationThreshold],
        lockReservationThreshold: [environment?.lockReservationThreshold],
      },
      {
        validators: requireWorkflowWhenEnabledValidator,
      }
    );
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

export const requireWorkflowWhenEnabledValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const enabled = control.get('enabled')?.value;
  const deploymentWorkflow = control.get('deploymentWorkflow')?.value;

  return enabled && !deploymentWorkflow ? { requireWorkflowWhenEnabled: true } : null;
};
