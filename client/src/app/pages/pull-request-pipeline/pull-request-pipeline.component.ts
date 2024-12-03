import { CommonModule } from '@angular/common';
import { Component, inject, input, Signal } from '@angular/core';
import { Pipeline, PipelineService } from '@app/core/services/pipeline';
import { PipelineComponent } from '@app/components/pipeline/pipeline.component';
import { FetchEnvironmentService } from '@app/core/services/fetch/environment';
import { AccordionModule } from 'primeng/accordion';
import { LockTagComponent } from '@app/components/lock-tag/lock-tag.component';
import { RouterLink } from '@angular/router';
import { TagModule } from 'primeng/tag';
import { IconsModule } from 'icons.module';
import { EnvironmentCommitInfoComponent } from '@app/components/environment-commit-info/environment-commit-info.component';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';

@Component({
  selector: 'app-pull-request-pipeline',
  imports: [InputTextModule, AccordionModule, CommonModule, LockTagComponent, RouterLink, TagModule, IconsModule, EnvironmentCommitInfoComponent, ButtonModule, PipelineComponent],
  providers: [FetchEnvironmentService],
  templateUrl: './pull-request-pipeline.component.html',
})
export class PullRequestPipelineComponent {
  pullRequestId: Signal<number> = input.required();
  fetchEnvironments = inject(FetchEnvironmentService);
  environments = this.fetchEnvironments.getEnvironments().data;
}
