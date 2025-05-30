import { Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { TextareaModule } from 'primeng/textarea';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconClipboardCheck, IconX } from 'angular-tabler-icons/icons';

export interface ReleaseEvaluationDialogData {
  releaseName: string;
  isWorking: boolean;
  comment?: string;
}

export interface ReleaseEvaluationDialogResult {
  isWorking: boolean;
  comment: string;
}

@Component({
  selector: 'app-release-evaluation-dialog',
  imports: [ReactiveFormsModule, TextareaModule, ButtonModule, TablerIconComponent],
  providers: [
    provideTablerIcons({
      IconClipboardCheck,
      IconX,
    }),
  ],
  templateUrl: './release-evaluation-dialog.component.html',
})
export class ReleaseEvaluationDialogComponent {
  private ref = inject(DynamicDialogRef);
  private config = inject(DynamicDialogConfig);

  data: ReleaseEvaluationDialogData = this.config.data;

  evaluationForm = new FormGroup({
    comment: new FormControl(this.data.comment ?? '', [Validators.maxLength(500)]),
  });

  submit() {
    if (this.evaluationForm.valid) {
      const comment = this.evaluationForm.get('comment')?.value || '';

      const result: ReleaseEvaluationDialogResult = {
        isWorking: this.data.isWorking,
        comment: comment,
      };

      this.ref.close(result);
    }
  }

  cancel() {
    this.ref.close();
  }
}
