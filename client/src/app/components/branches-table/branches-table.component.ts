import { Component, computed, inject } from '@angular/core';
import { TableModule } from 'primeng/table';
import { AvatarModule } from 'primeng/avatar';
import { TagModule } from 'primeng/tag';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { IconsModule } from 'icons.module';
import { SkeletonModule } from 'primeng/skeleton';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { IconFieldModule } from 'primeng/iconfield';
import { TreeTableModule } from 'primeng/treetable';
import { ButtonModule } from 'primeng/button';
import { BranchViewPreferenceService } from '@app/core/services/branches-table/branch-view-preference';
import { Router } from '@angular/router';
import { getAllBranchesOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { BranchInfoDto } from '@app/core/modules/openapi';
import { ProgressBarModule } from 'primeng/progressbar';
import { DividerModule } from 'primeng/divider';
import { TooltipModule } from 'primeng/tooltip';
import { FormsModule } from '@angular/forms';
import { TimeAgoPipe } from '@app/pipes/time-ago.pipe';
import { FILTER_OPTIONS_TOKEN, SearchTableService } from '@app/core/services/search-table.service';
import { TableFilterComponent } from '../table-filter/table-filter.component';

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
  ],
  providers: [SearchTableService, { provide: FILTER_OPTIONS_TOKEN, useValue: FILTER_OPTIONS }],
  templateUrl: './branches-table.component.html',
})
export class BranchTableComponent {
  router = inject(Router);
  viewPreference = inject(BranchViewPreferenceService);
  searchTableService = inject(SearchTableService<BranchInfoWithLink>);

  featureBranchesTree = computed(() => this.convertBranchesToTreeNodes(this.searchTableService.activeFilter().filter(this.branches())));

  query = injectQuery(() => getAllBranchesOptions());

  globalFilterFields = ['name', 'commitSha'];

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
