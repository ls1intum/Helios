import { CommonModule, Location } from '@angular/common';
import { Component, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';
import { getWorkflowRunLogsOptions, getWorkflowRunLogsQueryKey } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import type { WorkflowRunLogFileDto, WorkflowRunLogGroupDto, WorkflowRunLogsResponse } from '@app/core/modules/openapi/types.gen';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { Button } from 'primeng/button';
import { SkeletonModule } from 'primeng/skeleton';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconArrowLeft, IconExternalLink, IconFileText, IconFolder, IconRefresh, IconServerOff } from 'angular-tabler-icons/icons';

type SelectedWorkflowRunLogFile = {
  groupName: string;
  file: WorkflowRunLogFileDto;
};

type WorkflowRunLogLine = {
  number: number;
  content: string;
};

@Component({
  selector: 'app-workflow-run-logs',
  imports: [CommonModule, PageHeadingComponent, Button, SkeletonModule, TablerIconComponent],
  providers: [
    provideTablerIcons({
      IconArrowLeft,
      IconExternalLink,
      IconFileText,
      IconFolder,
      IconRefresh,
      IconServerOff,
    }),
  ],
  templateUrl: './workflow-run-logs.component.html',
})
export class WorkflowRunLogsComponent {
  repositoryId = input.required({ transform: numberAttribute });
  workflowRunId = input.required({ transform: numberAttribute });

  private location = inject(Location);

  selectedFilePath = signal<string | null>(null);

  logsQuery = injectQuery(() => ({
    ...getWorkflowRunLogsOptions({ path: { workflowRunId: this.workflowRunId() } }),
    queryKey: getWorkflowRunLogsQueryKey({ path: { workflowRunId: this.workflowRunId() } }),
    retry: false,
  }));

  logs = computed<WorkflowRunLogsResponse | undefined>(() => this.logsQuery.data());

  groups = computed<WorkflowRunLogGroupDto[]>(() => this.logs()?.groups ?? []);

  groupedFiles = computed(() =>
    this.groups().flatMap((group: WorkflowRunLogGroupDto) =>
      group.files.map((file: WorkflowRunLogFileDto) => ({
        groupName: group.name,
        file,
      }))
    )
  );

  selectedFile = computed<SelectedWorkflowRunLogFile | null>(() => {
    const selectedFilePath = this.selectedFilePath();
    if (!selectedFilePath) {
      return this.groupedFiles()[0] ?? null;
    }

    return this.groupedFiles().find((entry: SelectedWorkflowRunLogFile) => entry.file.path === selectedFilePath) ?? this.groupedFiles()[0] ?? null;
  });

  hasLogs = computed(() => this.groupedFiles().length > 0);
  activeFilePath = computed(() => this.selectedFile()?.file.path ?? null);
  selectedFileLines = computed<WorkflowRunLogLine[]>(() => {
    const content = this.selectedFile()?.file.content ?? '';

    return content.split(/\r?\n/).map((line, index) => ({
      number: index + 1,
      content: line,
    }));
  });

  constructor() {
    effect(() => {
      const selectedFile = this.selectedFile();
      if (selectedFile && this.selectedFilePath() !== selectedFile.file.path) {
        this.selectedFilePath.set(selectedFile.file.path);
      }
    });
  }

  selectFile(path: string) {
    this.selectedFilePath.set(path);
  }

  retry() {
    this.logsQuery.refetch();
  }

  goBack() {
    this.location.back();
  }

  openExternalLogs() {
    const htmlUrl = this.logs()?.htmlUrl;
    if (htmlUrl) {
      window.open(htmlUrl, '_blank');
    }
  }
}
