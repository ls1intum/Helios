import { Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { TextareaModule } from 'primeng/textarea';

export interface ReleaseEvaluationDialogData {
  releaseName: string;
  isWorking: boolean;
}

export interface ReleaseEvaluationDialogResult {
  isWorking: boolean;
  comment: string;
}

@Component({
  selector: 'app-release-evaluation-dialog',
  imports: [DialogModule, ReactiveFormsModule, TextareaModule, ButtonModule],
  templateUrl: './release-evaluation-dialog.component.html',
})
export class ReleaseEvaluationDialogComponent {
  private ref = inject(DynamicDialogRef);
  private config = inject(DynamicDialogConfig);

  data: ReleaseEvaluationDialogData = this.config.data;

  evaluationForm = new FormGroup({
    comment: new FormControl('', [Validators.maxLength(500)]),
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
