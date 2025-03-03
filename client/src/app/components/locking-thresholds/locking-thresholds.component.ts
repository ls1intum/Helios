import { Component, computed, effect, input, signal, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';

import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { InputNumberModule } from 'primeng/inputnumber';
import { DividerModule } from 'primeng/divider';
import { IftaLabelModule } from 'primeng/iftalabel';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import {
  getGitRepoSettingsOptions,
  updateGitRepoSettingsMutation,
  getEnvironmentsByUserLockingQueryKey,
  getAllEnvironmentsQueryKey,
  getAllEnabledEnvironmentsQueryKey,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { MessageService } from 'primeng/api';
@Component({
  selector: 'app-locking-thresholds',
  standalone: true,
  imports: [ToggleSwitchModule, InputNumberModule, DividerModule, IftaLabelModule, FormsModule, ButtonModule],
  templateUrl: './locking-thresholds.component.html',
})
export class LockingThresholdsComponent {
  private messageService = inject(MessageService);
  private queryClient = inject(QueryClient);
  isLockExpirationEnabled = signal(false);
  isLockReservationEnabled = signal(false);
  lockingExpirationThreshold = signal<number | undefined>(undefined);
  lockingReservationThreshold = signal<number | undefined>(undefined);

  repositoryId = input.required<number>();

  isPending = computed(() => this.fetchGitRepoThresholdsQuery.isPending());
  isError = computed(() => this.fetchGitRepoThresholdsQuery.isError());

  fetchGitRepoThresholdsQuery = injectQuery(() => ({
    ...getGitRepoSettingsOptions({ path: { repositoryId: this.repositoryId() } }),
    enabled: () => !!this.repositoryId(),
  }));

  mutateGitRepoSettings = injectMutation(() => ({
    ...updateGitRepoSettingsMutation(),
    onSuccess: () => {
      this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Thesholds successfully saved.' });
      this.queryClient.invalidateQueries({ queryKey: getAllEnvironmentsQueryKey() });
      this.queryClient.invalidateQueries({ queryKey: getAllEnabledEnvironmentsQueryKey() });
      this.queryClient.invalidateQueries({ queryKey: getEnvironmentsByUserLockingQueryKey() });
    },
  }));

  constructor() {
    effect(
      () => {
        const gitRepoThresholds = this.fetchGitRepoThresholdsQuery.data();

        // Use untracked to prevent immediate effect triggers
        const expThreshold = gitRepoThresholds?.lockExpirationThreshold ?? undefined;
        const resThreshold = gitRepoThresholds?.lockReservationThreshold ?? undefined;

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
    if (type === 'expiration') {
      const enabled = !this.isLockExpirationEnabled();
      this.isLockExpirationEnabled.set(enabled);
      this.lockingExpirationThreshold.set(enabled ? 60 : undefined);
    } else {
      const enabled = !this.isLockReservationEnabled();
      this.isLockReservationEnabled.set(enabled);
      this.lockingReservationThreshold.set(enabled ? 30 : undefined);
    }
  }

  onExpirationThresholdChange(value: number | null) {
    this.lockingExpirationThreshold.set(value !== null ? value : undefined);
  }

  onReservationThresholdChange(value: number | null) {
    this.lockingReservationThreshold.set(value !== null ? value : undefined);
  }

  updateLockingThresholds() {
    const lockExpriation = this.isLockExpirationEnabled() ? this.lockingExpirationThreshold() : -1;
    const lockReservation = this.isLockReservationEnabled() ? this.lockingReservationThreshold() : -1;
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

  hasUnsavedChanges = computed(() => {
    const serverData = this.fetchGitRepoThresholdsQuery.data();
    if (!serverData) return false;

    // Get current values
    const currentExpirationEnabled = this.isLockExpirationEnabled();
    const currentReservationEnabled = this.isLockReservationEnabled();
    const currentExpirationThreshold = this.lockingExpirationThreshold();
    const currentReservationThreshold = this.lockingReservationThreshold();

    // Get server values
    const serverExpirationEnabled = serverData.lockExpirationThreshold !== undefined && serverData.lockExpirationThreshold >= 0;
    const serverReservationEnabled = serverData.lockReservationThreshold !== undefined && serverData.lockReservationThreshold >= 0;
    const serverExpirationThreshold = serverExpirationEnabled ? serverData.lockExpirationThreshold : undefined;
    const serverReservationThreshold = serverReservationEnabled ? serverData.lockReservationThreshold : undefined;

    // Compare enabled states
    if (currentExpirationEnabled !== serverExpirationEnabled) return true;
    if (currentReservationEnabled !== serverReservationEnabled) return true;

    // Compare threshold values (only if respective feature is enabled)
    if (currentExpirationEnabled && currentExpirationThreshold !== serverExpirationThreshold) return true;
    if (currentReservationEnabled && currentReservationThreshold !== serverReservationThreshold) return true;

    // No changes detected
    return false;
  });
}
