import { Component, computed, inject } from '@angular/core';
import { TableModule } from 'primeng/table';
import { AvatarModule } from 'primeng/avatar';
import { TagModule } from 'primeng/tag';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { IconsModule } from 'icons.module';
import { SkeletonModule } from 'primeng/skeleton';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { IconFieldModule } from 'primeng/iconfield';
import { TreeTableModule } from 'primeng/treetable';
import { ButtonModule } from 'primeng/button';
import { BranchViewPreferenceService } from '@app/core/services/branches-table/branch-view-preference';
import { Router } from '@angular/router';
import {
  getAllBranchesOptions,
  getAllBranchesQueryKey,
  setBranchPinnedByRepositoryIdAndNameAndUserIdMutation,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { BranchInfoDto } from '@app/core/modules/openapi';
import { ProgressBarModule } from 'primeng/progressbar';
import { DividerModule } from 'primeng/divider';
import { TooltipModule } from 'primeng/tooltip';
import { FormsModule } from '@angular/forms';
import { TimeAgoPipe } from '@app/pipes/time-ago.pipe';
import { FILTER_OPTIONS_TOKEN, SearchTableService } from '@app/core/services/search-table.service';
import { TableFilterComponent } from '../table-filter/table-filter.component';
import { WorkflowRunStatusComponent } from '@app/components/workflow-run-status-component/workflow-run-status.component';
import { HighlightPipe } from '@app/pipes/highlight.pipe';
import { MessageService } from 'primeng/api';

type BranchInfoWithLink = BranchInfoDto & { link: string; lastCommitLink: string };

const FILTER_OPTIONS = [
  { name: 'All Branches', filter: (branches: BranchInfoWithLink[]) => branches },
  { name: 'Default Branch', filter: (branches: BranchInfoWithLink[]) => branches.filter(branch => branch.isDefault) },
  { name: 'Protected Branches', filter: (branches: BranchInfoWithLink[]) => branches.filter(branch => branch.isProtected) },
  {
    name: 'Active Branches',
    filter: (branches: BranchInfoWithLink[]) =>
      branches.filter(branch => {
        const date = new Date(branch.updatedAt || '');
        const staleThreshold = new Date();
        staleThreshold.setDate(staleThreshold.getDate() - 30);

        return date >= staleThreshold;
      }),
  },
  {
    name: 'Stale Branches',
    filter: (branches: BranchInfoWithLink[]) =>
      branches.filter(branch => {
        const date = new Date(branch.updatedAt || '');
        const staleThreshold = new Date();
        staleThreshold.setDate(staleThreshold.getDate() - 30);

        return date < staleThreshold;
      }),
  },
];

@Component({
  selector: 'app-branches-table',
  imports: [
    TableModule,
    AvatarModule,
    TagModule,
    DividerModule,
    IconsModule,
    TooltipModule,
    TimeAgoPipe,
    SkeletonModule,
    ProgressBarModule,
    InputTextModule,
    TreeTableModule,
    ButtonModule,
    IconFieldModule,
    TableFilterComponent,
    InputIconModule,
    InputTextModule,
    FormsModule,
    WorkflowRunStatusComponent,
    HighlightPipe,
  ],
  providers: [SearchTableService, { provide: FILTER_OPTIONS_TOKEN, useValue: FILTER_OPTIONS }],
  templateUrl: './branches-table.component.html',
})
export class BranchTableComponent {
  router = inject(Router);
  viewPreference = inject(BranchViewPreferenceService);
  messageService = inject(MessageService);
  queryClient = inject(QueryClient);
  searchTableService = inject(SearchTableService<BranchInfoWithLink>);

  query = injectQuery(() => getAllBranchesOptions());
  setPinnedMutation = injectMutation(() => ({
    ...setBranchPinnedByRepositoryIdAndNameAndUserIdMutation(),
    onSuccess: () => {
      this.messageService.add({ severity: 'success', summary: 'Pin Pull Request', detail: 'The pull request was pinned successfully' });
      this.queryClient.invalidateQueries({ queryKey: getAllBranchesQueryKey() });
    },
  }));

  // Use only branch name for map, because it is unique in this view
  isHovered = new Map<string, boolean>();

  globalFilterFields = ['name', 'commitSha'];

  featureBranchesTree = computed(() => this.convertBranchesToTreeNodes(this.searchTableService.activeFilter().filter(this.branches())));

  branches = computed<BranchInfoWithLink[]>(
    () =>
      this.query.data()?.map(branch => ({
        ...branch,
        link: `https://github.com/${branch!.repository!.nameWithOwner}/tree/${branch.name}`,
        lastCommitLink: `https://github.com/${branch!.repository!.nameWithOwner}/commit/${branch.commitSha}`,
      })) || []
  );

  maxAheadBehindBy = computed(() => Math.max(...this.branches().map(branch => Math.max(branch.aheadBy || 0, branch.behindBy || 0))));

  openLink(event: Event, url: string): void {
    window.open(url, '_blank');
    event.stopPropagation();
  }

  calculateProgress(value: number): number {
    return (value * 100) / this.maxAheadBehindBy();
  }

  openBranch(branch: BranchInfoDto): void {
    if (branch.repository?.id) {
      this.router.navigate(['repo', branch.repository?.id, 'ci-cd', 'branch', branch.name]);
    }
  }

  setPinned(event: Event, branch: BranchInfoDto, isPinned: boolean): void {
    event.stopPropagation();
    if (!branch.repository) return;
    this.setPinnedMutation.mutate({ path: { repoId: branch.repository.id }, query: { name: branch.name, isPinned } });
    this.isHovered.set(branch.name, false);
  }

  convertBranchesToTreeNodes(branches: BranchInfoWithLink[]): TreeNode[] {
    const rootNodes: TreeNode[] = [
      {
        data: {
          name: 'Default',
          type: 'Folder',
        },
        children: [],
        expanded: true,
        subheader: true,
      },
      {
        data: {
          name: 'Protected Branches',
          type: 'Folder',
        },
        children: [],
        expanded: true,
        subheader: true,
      },
      {
        data: {
          name: 'General Branches',
          type: 'Folder',
        },
        children: [],
        expanded: true,
        subheader: true,
      },
    ];
    const nodeMap = new Map<string, TreeNode>();

    // Function to check if the branch name matches the search value
    const matchesSearch = (branch: BranchInfoWithLink) =>
      this.searchTableService.searchValue().toLowerCase() && branch.name.toLowerCase().includes(this.searchTableService.searchValue().toLowerCase());

    branches.forEach(branch => {
      const pathParts = branch.name.split('/');
      let currentPath = '';

      pathParts.forEach((part, index) => {
        const isLeaf = index === pathParts.length - 1;
        currentPath = currentPath ? `${currentPath}/${part}` : part;

        if (!nodeMap.has(currentPath)) {
          const newNode: TreeNode = {
            data: {
              name: part,
            },
            expanded: false,
          };

          // If it's a leaf node, add the branch info
          if (isLeaf) {
            newNode.data = branch;
          } else {
            newNode.children = [];
          }

          nodeMap.set(currentPath, newNode);

          // If it's the first level, add to root nodes
          if (index === 0) {
            if (branch.isDefault) {
              rootNodes[0].children!.push(newNode);
            } else if (branch.isProtected) {
              rootNodes[1].children!.push(newNode);
            } else {
              rootNodes[2].children!.push(newNode);
            }
          } else {
            // Add as child to parent node
            const parentPath = pathParts.slice(0, index).join('/');
            const parentNode = nodeMap.get(parentPath);
            if (parentNode && parentNode.children) {
              parentNode.children.push(newNode);
            }
          }

          // Expand nodes if they match the search
          if (matchesSearch(branch)) {
            // Start from the current node path and expand all the way up to the root
            let parentPath = currentPath;
            while (parentPath) {
              const parentNode = nodeMap.get(parentPath);
              if (parentNode) {
                // Expand the parent node
                parentNode.expanded = true;
              }
              // Move up one level by removing the last segment of the path
              parentPath = parentPath.includes('/') ? parentPath.slice(0, parentPath.lastIndexOf('/')) : '';
            }
          }
        }
      });
    });
    return rootNodes.filter(rootSubheader => rootSubheader.children!.length > 0);
  }
}

interface TreeNode {
  data: Partial<BranchInfoWithLink> & {
    type?: 'Branch' | 'Folder';
  };
  children?: TreeNode[];
  expanded?: boolean;
  subheader?: boolean;
}
