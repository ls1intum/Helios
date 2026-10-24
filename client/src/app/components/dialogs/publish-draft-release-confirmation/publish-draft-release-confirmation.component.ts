import { Component, input, model, output, ChangeDetectionStrategy } from '@angular/core';
import { Dialog } from 'primeng/dialog';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { Button } from 'primeng/button';
import { IconUpload } from 'angular-tabler-icons/icons';

@Component({
  selector: 'app-publish-draft-release-confirmation',
  imports: [Dialog, TablerIconComponent, Button],
  providers: [
    provideTablerIcons({
      IconUpload,
    }),
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './publish-draft-release-confirmation.component.html',
})
export class PublishDraftReleaseConfirmationComponent {
  isVisible = model.required<boolean>();
  releaseName = input.required<string>();

  /** Emits true if publish clicked, false if Cancel */
  confirmed = output<boolean>();

  onCancel() {
    this.isVisible.update(() => false);
    this.confirmed.emit(false);
  }

  onPublish() {
    this.isVisible.update(() => false);
    this.confirmed.emit(true);
  }
}
