import { Component, computed, input, model, output } from '@angular/core';
import { EnvironmentDto, EnvironmentReviewersDto } from '@app/core/modules/openapi';
import { getEnvironmentReviewersOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
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
  environmentName = computed(() => (this.environment().displayName?.trim() ? this.environment().displayName : (this.environment().name ?? '')));

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

  // derived data
  reviewers = computed(() => (this.query.data() as EnvironmentReviewersDto)?.reviewers ?? []);
  hasReviewers = computed(() => this.reviewers().length > 0);
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
