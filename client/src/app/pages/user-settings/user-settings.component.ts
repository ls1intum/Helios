import { Component, computed, inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { MessageService } from 'primeng/api';
import {
  getNotificationPreferencesOptions,
  getNotificationPreferencesQueryKey,
  getUserSettingsOptions,
  getUserSettingsQueryKey,
  updateNotificationPreferencesMutation,
  updateUserSettingsMutation,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Skeleton } from 'primeng/skeleton';
import { ToggleSwitch } from 'primeng/toggleswitch';
import { toObservable } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-user-settings',
  imports: [Card, Button, ReactiveFormsModule, InputText, FormsModule, Skeleton, ToggleSwitch],
  templateUrl: './user-settings.component.html',
})
export class UserSettingsComponent implements OnInit {
  private fb = inject(FormBuilder);
  private messageService = inject(MessageService);
  private queryClient = inject(QueryClient);

  readonly notificationLabels: Record<string, string> = {
    DEPLOYMENT_FAILED: 'Deployment failed',
    LOCK_EXPIRED: 'Lock expired',
    LOCK_UNLOCKED: 'Lock unlocked',
  };

  constructor() {
    toObservable(this.userSettings).subscribe();
  }

  ngOnInit(): void {
    this.settingsForm.get('globalNotificationsEnabled')?.valueChanges.subscribe(enabled => {
      this.onNotificationToggle(enabled);
    });
  }

  settingsForm: FormGroup = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    globalNotificationsEnabled: [false],
  });

  // Notification email and global notifications enabled
  userSettingsQuery = injectQuery(() => ({
    ...getUserSettingsOptions(),
    enabled: true,
  }));

  // Individual notification preferences
  notificationPreferencesQuery = injectQuery(() => {
    const isUserReady = this.userSettingsQuery.isSuccess();
    return {
      ...getNotificationPreferencesOptions(),
      enabled: isUserReady,
    };
  });

  userSettings = computed(() => {
    const userSettings = this.userSettingsQuery.data();
    this.settingsForm.patchValue(
      {
        email: userSettings?.notificationEmail || '',
        globalNotificationsEnabled: userSettings?.notificationsEnabled || false,
      },
      { emitEvent: false }
    );
    return userSettings;
  });

  individualTogglesDisabled = computed(() => !this.userSettings()?.notificationsEnabled);

  notificationPreferences = computed(() => {
    return this.notificationPreferencesQuery.data() ?? [];
  });

  updateUserSettings = injectMutation(() => ({
    ...updateUserSettingsMutation(),
    onSuccess: () => {
      this.messageService.add({
        severity: 'success',
        summary: 'Success',
        detail: 'User settings updated successfully.',
      });
      this.queryClient.invalidateQueries({ queryKey: getUserSettingsQueryKey() });
    },
    onError: () => {
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to update user settings!',
      });
      this.queryClient.invalidateQueries({ queryKey: getUserSettingsQueryKey() });
    },
  }));

  updateNotificationPreferences = injectMutation(() => ({
    ...updateNotificationPreferencesMutation(),
    onSuccess: () => {
      this.messageService.add({
        severity: 'success',
        summary: 'Success',
        detail: 'Notification preferences updated.',
      });
      this.queryClient.invalidateQueries({ queryKey: getNotificationPreferencesQueryKey() });
    },
    onError: () => {
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to update preferences!',
      });
      this.queryClient.invalidateQueries({ queryKey: getNotificationPreferencesQueryKey() });
    },
  }));

  saveEmail(): void {
    const emailControl = this.settingsForm.get('email');
    if (emailControl === null) return;
    if (emailControl?.invalid) return;

    this.updateUserSettings.mutate({
      body: { notificationEmail: emailControl.value },
    });
  }

  onNotificationToggle(enabled: boolean): void {
    this.updateUserSettings.mutate({
      body: { notificationsEnabled: enabled },
    });
  }

  onToggleNotificationPreference(type: string | undefined, newValue: boolean): void {
    const updatedPreferences = this.notificationPreferences().map(pref => (pref.type === type ? { ...pref, enabled: newValue } : pref));
    this.updateNotificationPreferences.mutate({ body: { preferences: updatedPreferences } });
  }
}
