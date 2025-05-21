import { Component, model, output } from '@angular/core';
import {provideTablerIcons, TablerIconComponent} from 'angular-tabler-icons';
import { Dialog } from 'primeng/dialog';
import { Button } from 'primeng/button';
import {IconKey, IconAlertTriangle} from 'angular-tabler-icons/icons';

@Component({
  selector: 'app-secret-generate-confirmation',
  imports: [TablerIconComponent, Dialog, Button],
  providers: [
    provideTablerIcons({
      IconKey,
      IconAlertTriangle,
    }),
  ],
  templateUrl: './secret-generate-confirmation.component.html',
})
export class SecretGenerateConfirmationComponent {
  isVisible = model.required<boolean>();
  /** Emits true if generate clicked, false if Cancel */
  confirmed = output<boolean>();

  onCancel() {
    this.isVisible.update(() => false);
    this.confirmed.emit(false);
  }

  onGenerate() {
    this.isVisible.update(() => false);
    this.confirmed.emit(true);
  }
}
