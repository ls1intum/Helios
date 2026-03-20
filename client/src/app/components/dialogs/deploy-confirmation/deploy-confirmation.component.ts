import { Component, computed, input, model, output } from '@angular/core';
import { EnvironmentDeploymentReadinessDto, EnvironmentDto, EnvironmentReviewersDto, RequiredWorkflowStatusDto } from '@app/core/modules/openapi';
import { getDeploymentReadinessOptions, getEnvironmentReviewersOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { SkeletonModule } from 'primeng/skeleton';
import { DialogModule } from 'primeng/dialog';
import { NgClass } from '@angular/common';
import { PrimeTemplate } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconAlertTriangle, IconInfoCircle, IconServer, IconCloudUpload } from 'angular-tabler-icons/icons';
import { InputText } from 'primeng/inputtext';

@Component({
  selector: 'app-deploy-confirmation',
  imports: [SkeletonModule, DialogModule, ButtonModule, NgClass, PrimeTemplate, TablerIconComponent, InputText],
  providers: [
    provideTablerIcons({
      IconAlertTriangle,
      IconInfoCircle,
      IconServer,
      IconCloudUpload,
    }),
  ],
  templateUrl: './deploy-confirmation.component.html',
})
export class DeployConfirmationComponent {
  /** Input text for the confirmation */
  repoConfirm = '';

  /** Two-way bind this from the parent */
  isVisible = model.required<boolean>();
  /** The environment to deploy */
  environment = input.required<EnvironmentDto>();
  /** Optional source ref shown for deploy flows that need extra context. */
  sourceRef = input<{ ref: string; type: 'branch' | 'tag' }>();
  /** Exact commit that will be deployed. */
  commitSha = input<string | undefined>();
  environmentName = computed(() => (this.environment().displayName?.trim() ? this.environment().displayName : (this.environment().name ?? '')));
  environmentKind = computed(() => {
    switch (this.environment().type) {
      case 'PRODUCTION':
        return 'production';
      case 'STAGING':
        return 'staging';
      default:
        return 'test server';
    }
  });
  displaySourceRef = computed(() => this.sourceRef()?.ref?.trim() || '');
  shortCommitSha = computed(() => this.commitSha()?.slice(0, 7) || '');
  hasReadinessContext = computed(() => !!this.environment().id && this.sourceRef()?.type === 'branch' && !!this.displaySourceRef() && !!this.commitSha()?.trim());

  /** Emits true if Deploy clicked, false if Cancel */
  confirmed = output<boolean>();

  // Fetch Reviewers
  query = injectQuery(() => ({
    ...getEnvironmentReviewersOptions({
      path: { environmentId: this.environment().id },
    }),
    enabled: !!this.environment().id,
    throwOnError: false,
    retry: false,
  }));
  readinessQuery = injectQuery(() => ({
    ...getDeploymentReadinessOptions({
      path: { environmentId: this.environment().id },
      query: {
        branch: this.displaySourceRef(),
        sha: this.commitSha() ?? '',
      },
    }),
    enabled: () => this.hasReadinessContext(),
    throwOnError: false,
    retry: false,
  }));

  // derived data
  reviewers = computed(() => (this.query.data() as EnvironmentReviewersDto)?.reviewers ?? []);
  hasReviewers = computed(() => this.reviewers().length > 0);
  readiness = computed(() => this.readinessQuery.data() as EnvironmentDeploymentReadinessDto | undefined);
  readinessStatus = computed(() => this.readiness()?.status);
  blockedWorkflows = computed(() => (this.readiness()?.workflows ?? []).filter(workflow => workflow.status && workflow.status !== 'READY'));
  missingWorkflows = computed(() => (this.readiness()?.workflows ?? []).filter(workflow => workflow.status === 'MISSING_RUN'));
  showReadinessWarning = computed(() => {
    const status = this.readinessStatus();
    return status === 'WAITING' || status === 'FAILED' || status === 'MISSING_RUN';
  });
  showBlockedWorkflowList = computed(() => this.readinessStatus() !== 'MISSING_RUN');
  showMissingWorkflowList = computed(() => this.readinessStatus() === 'MISSING_RUN');
  isLoading = computed(() => this.query.isPending() || (this.hasReadinessContext() && this.readinessQuery.isPending()));
  reviewersLine = computed(() => {
    if (!this.hasReviewers()) {
      return '';
    }

    return this.reviewers()
      .map(r => {
        const name = r.name || r.login;
        return r.type !== 'User' ? `${name} (Team)` : name;
      })
      .join(', ');
  });

  deploymentReadinessSummary(): string {
    switch (this.readinessStatus()) {
      case 'WAITING':
        return `Some are still running on branch ${this.displaySourceRef()} at commit ${this.shortCommitSha()}.`;
      case 'FAILED':
        return `At least one did not succeed on branch ${this.displaySourceRef()} at commit ${this.shortCommitSha()}.`;
      case 'MISSING_RUN':
        return `No matching runs were found on branch ${this.displaySourceRef()} at commit ${this.shortCommitSha()} for these workflows:`;
      default:
        return '';
    }
  }

  describeBlockedWorkflow(workflow: RequiredWorkflowStatusDto): string {
    switch (workflow.status) {
      case 'WAITING':
        return `Latest matching run is still ${this.formatWorkflowValue(workflow.runStatus)}.`;
      case 'FAILED':
        return `Latest matching run finished with ${this.formatWorkflowValue(workflow.runConclusion || workflow.runStatus)}.`;
      case 'MISSING_RUN':
        return 'No matching run found.';
      default:
        return '';
    }
  }

  getWorkflowDisplayName(workflow: RequiredWorkflowStatusDto): string {
    const name = workflow.workflowName?.trim();
    if (name) {
      return name;
    }

    if (workflow.workflowId) {
      return `Workflow #${workflow.workflowId}`;
    }

    return 'Unnamed workflow';
  }

  private formatWorkflowValue(value?: string): string {
    return value ? value.toLowerCase().replaceAll('_', ' ') : 'unknown';
  }

  onRepoInput(event: Event) {
    this.repoConfirm = (event.target as HTMLInputElement).value;
  }

  onCancel() {
    this.isVisible.update(() => false);
    this.confirmed.emit(false);
  }

  onDeploy() {
    this.isVisible.update(() => false);
    this.confirmed.emit(true);
  }

  get visible(): boolean {
    return this.isVisible();
  }

  set visible(val: boolean) {
    this.isVisible.set(val);
  }
}
