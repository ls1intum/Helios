import { Component, computed, effect, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';

import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { InputNumberModule } from 'primeng/inputnumber';
import { DividerModule } from 'primeng/divider';
import { IftaLabelModule } from 'primeng/iftalabel';
import { injectMutation, injectQuery } from '@tanstack/angular-query-experimental';
import {
  getEnvironmentByIdOptions,
  getGitRepoSettingsOptions,
  updateGitRepoSettingsMutation,
  updateEnvironmentMutation,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';

@Component({
  selector: 'app-locking-thresholds',
  standalone: true,
  imports: [ToggleSwitchModule, InputNumberModule, DividerModule, IftaLabelModule, FormsModule, ButtonModule],
  templateUrl: './locking-thresholds.component.html',
})
export class LockingThresholdsComponent {
  // Add these to your component class
  isLockExpirationEnabled = signal(false);
  isLockReservationEnabled = signal(false);
  lockingExpirationThreshold = signal<number | undefined>(undefined);
  lockingReservationThreshold = signal<number | undefined>(undefined);

  environmentId = input<number | undefined>();
  repositoryId = input.required<number>();

  isPending = computed(() => this.fetchGitRepoThresholdsQuery.isPending() || this.fetchEnvironmentThresholdsQuery.isPending());
  isError = computed(() => this.fetchGitRepoThresholdsQuery.isError() || this.fetchEnvironmentThresholdsQuery.isError());

  fetchGitRepoThresholdsQuery = injectQuery(() => ({
    ...getGitRepoSettingsOptions({ path: { repositoryId: this.repositoryId() } }),
    enabled: () => !!this.repositoryId(),
    refetchOnWindowFocus: false,
  }));

  fetchEnvironmentThresholdsQuery = injectQuery(() => ({
    ...getEnvironmentByIdOptions({ path: { id: this.environmentId()! } }),
    enabled: () => this.environmentId() !== undefined,
    refetchOnWindowFocus: false,
  }));

  mutateGitRepoSettings = injectMutation(() => ({
    ...updateGitRepoSettingsMutation(),
    onSuccess: () => {
      console.log('Git repo settings updated');
    },
  }));

  mutateEnvironment = injectMutation(() => ({
    ...updateEnvironmentMutation(),
    onSuccess: () => {
      // this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Environment updated successfully' });
      console.log('Environment updated successfully');
    },
  }));

  constructor() {
    effect(
      () => {
        const gitRepoThresholds = this.fetchGitRepoThresholdsQuery.data();
        const environmentThresholds = this.fetchEnvironmentThresholdsQuery.data();

        // Use untracked to prevent immediate effect triggers
        const expThreshold = environmentThresholds?.lockExpirationThreshold ?? gitRepoThresholds?.lockExpirationThreshold ?? undefined;
        const resThreshold = environmentThresholds?.lockReservationThreshold ?? gitRepoThresholds?.lockReservationThreshold ?? undefined;

        // Update signals in batch
        this.lockingExpirationThreshold.set(expThreshold !== undefined && expThreshold > 0 ? expThreshold : undefined);
        this.lockingReservationThreshold.set(resThreshold !== undefined && resThreshold > 0 ? resThreshold : undefined);

        // Set toggle states based on thresholds
        this.isLockExpirationEnabled.set(expThreshold !== undefined && expThreshold >= 0);
        this.isLockReservationEnabled.set(resThreshold !== undefined && resThreshold >= 0);
      },
      { allowSignalWrites: true }
    );
  }

  // Updated toggle handler
  onThresholdToggle(type: 'expiration' | 'reservation') {
    // const gitRepoThresholds = this.fetchGitRepoThresholdsQuery.data();
    // const environmentThresholds = this.fetchEnvironmentThresholdsQuery.data();

    if (type === 'expiration') {
      // const expThreshold = environmentThresholds?.lockExpirationThreshold ?? gitRepoThresholds?.lockExpirationThreshold ?? undefined;
      const enabled = !this.isLockExpirationEnabled();
      this.isLockExpirationEnabled.set(enabled);
      this.lockingExpirationThreshold.set(enabled ? 60 : undefined);
    } else {
      // const resThreshold = environmentThresholds?.lockReservationThreshold ?? gitRepoThresholds?.lockReservationThreshold ?? undefined;
      const enabled = !this.isLockReservationEnabled();
      this.isLockReservationEnabled.set(enabled);
      this.lockingReservationThreshold.set(enabled ? 30 : undefined);
    }
  }
  // Add these methods to your component class
  onExpirationThresholdChange(value: number | null) {
    console.log('Expiration threshold changed:', value);
    this.lockingExpirationThreshold.set(value !== null ? value : undefined);
  }

  onReservationThresholdChange(value: number | null) {
    console.log('Reservation threshold changed:', value);
    this.lockingReservationThreshold.set(value !== null ? value : undefined);
  }
  // Update your existing updateLockingThresholds method
  updateLockingThresholds() {
    // Here you would typically send the values to your backend
    console.log('Saving thresholds:', {
      expiration: this.lockingExpirationThreshold(),
      reservation: this.lockingReservationThreshold(),
    });

    const lockExpriation = this.isLockExpirationEnabled() ? this.lockingExpirationThreshold() : -1;
    const lockReservation = this.isLockReservationEnabled() ? this.lockingReservationThreshold() : -1;

    console.log('Saving to API');
    if (this.environmentId() !== undefined) {
      console.log('Environment ID:', this.environmentId());
      this.mutateEnvironment.mutate({
        path: { id: this.environmentId()! },
        body: {
          lockExpirationThreshold: lockExpriation,
          lockReservationThreshold: lockReservation,
          id: this.environmentId()!,
          name: '', //TODO get name from environment
        },
      });
    } else {
      console.log('Repository ID:', this.repositoryId());
      this.mutateGitRepoSettings.mutate({
        path: {
          repositoryId: this.repositoryId(),
        },
        body: {
          lockExpirationThreshold: lockExpriation,
          lockReservationThreshold: lockReservation,
        },
      });
    }

    // Add your API call here
  }
}
