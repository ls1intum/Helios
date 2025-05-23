import { Component, computed, input, model, output } from '@angular/core';
import { EnvironmentDto } from '@app/core/modules/openapi';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconLock } from 'angular-tabler-icons/icons';

@Component({
  selector: 'app-lock-confirmation',
  imports: [Button, Dialog, TablerIconComponent],
  providers: [
    provideTablerIcons({
      IconLock,
    }),
  ],
  templateUrl: './lock-confirmation.component.html',
})
export class LockConfirmationComponent {
  /** Two-way bind this from the parent */
  isVisible = model.required<boolean>();
  /** The environment to deploy */
  environment = input.required<EnvironmentDto>();
  environmentName = computed(() => (this.environment().displayName?.trim() ? this.environment().displayName : (this.environment().name ?? '')));

  /** Emits true if lock clicked, false if Cancel */
  confirmed = output<boolean>();

  onCancel() {
    this.isVisible.update(() => false);
    this.confirmed.emit(false);
  }

  onLock() {
    this.isVisible.update(() => false);
    this.confirmed.emit(true);
  }
}
