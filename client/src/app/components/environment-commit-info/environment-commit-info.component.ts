import { Component, computed, inject, Input } from '@angular/core';
import { FetchCommitService } from '@app/core/services/fetch/commit';
import { IconsModule } from 'icons.module';
import { TagModule } from 'primeng/tag';

@Component({
  selector: 'app-environment-commit-info',
  imports: [TagModule, IconsModule],
  providers: [FetchCommitService],
  templateUrl: './environment-commit-info.component.html',
  styleUrl: './environment-commit-info.component.css',
})
export class EnvironmentCommitInfoComponent {
  fetchEnvironments = inject(FetchCommitService);

  commits = this.fetchEnvironments.getCommits().data;
  @Input({ required: true }) commitHash!: string;

  commit = computed(() => this.commits()?.find(commit => commit.commitHash === this.commitHash));
}
