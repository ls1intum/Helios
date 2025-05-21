import { Component, model, output } from '@angular/core';
import { TablerIconComponent } from 'angular-tabler-icons';
import { Dialog } from 'primeng/dialog';
import { Button } from 'primeng/button';

@Component({
  selector: 'app-secret-generate-confirmation',
  imports: [TablerIconComponent, Dialog, Button],
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
