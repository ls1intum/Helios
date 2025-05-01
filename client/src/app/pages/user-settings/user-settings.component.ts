import { Component, computed, inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { MessageService } from 'primeng/api';
import { getUserSettingsOptions, getUserSettingsQueryKey, updateUserSettingsMutation } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
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
  styles: ``,
})
export class UserSettingsComponent implements OnInit {
  private fb = inject(FormBuilder);
  private messageService = inject(MessageService);
  private queryClient = inject(QueryClient);

  constructor() {
    toObservable(this.userSettings).subscribe();
  }

  ngOnInit(): void {
    this.settingsForm.get('globalNotificationsEnabled')?.valueChanges.subscribe(enabled => {
      this.onNotificationToggle(enabled);
    });
  }

  settingsForm: FormGroup = this.fb.group({
    email: [''],
    globalNotificationsEnabled: [false],
  });

  userSettingsQuery = injectQuery(() => ({
    ...getUserSettingsOptions(),
    enabled: true,
  }));

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

  saveEmail(): void {
    const emailControl = this.settingsForm.get('email');
    console.log('Email control:', emailControl);
    if (emailControl === null) return;
    if (emailControl?.invalid) return;

    console.log('Email update triggered:', emailControl.value);
    this.updateUserSettings.mutate({
      body: { notificationEmail: emailControl.value },
    });
  }

  onNotificationToggle(enabled: boolean): void {
    console.log('Notification toggle changed:', enabled);
    this.updateUserSettings.mutate({
      body: { notificationsEnabled: enabled },
    });
  }
}
