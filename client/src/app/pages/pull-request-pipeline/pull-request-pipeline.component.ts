import { CommonModule } from '@angular/common';
import { Component, computed, inject, input, signal, Signal } from '@angular/core';
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
import { PullRequestStoreService } from '@app/core/services/pull-requests';
import { MarkdownPipe } from '@app/core/modules/markdown/markdown.pipe';

@Component({
  selector: 'app-pull-request-pipeline',
  imports: [InputTextModule, AccordionModule, CommonModule, LockTagComponent, RouterLink, TagModule, IconsModule, EnvironmentCommitInfoComponent, ButtonModule, PipelineComponent, MarkdownPipe],
  providers: [FetchEnvironmentService],
  templateUrl: './pull-request-pipeline.component.html',
  styleUrl: './pull-request-pipeline.component.css'
})
export class PullRequestPipelineComponent {
  pullRequestId: Signal<number> = input.required();
  fetchEnvironments = inject(FetchEnvironmentService);
  environments = this.fetchEnvironments.getEnvironments().data;
  pullRequestStore = inject(PullRequestStoreService);
  currentPullRequest = computed(() => {
    const prs = this.pullRequestStore.pullRequests();
    const id = this.pullRequestId();
    const found = prs.find(pr => pr.id.toString() === id.toString());
    return found;
  });
}
