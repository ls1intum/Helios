import { Component, computed, inject, signal } from '@angular/core';
import { TableModule } from 'primeng/table';
import { AvatarModule } from 'primeng/avatar';
import { TagModule } from 'primeng/tag';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { IconsModule } from 'icons.module';
import { SkeletonModule } from 'primeng/skeleton';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { IconFieldModule } from 'primeng/iconfield';
import { TreeTable, TreeTableModule } from 'primeng/treetable';
import { ButtonModule } from 'primeng/button';
import { BranchViewPreferenceService } from '@app/core/services/branches-table/branch-view-preference';
import { Router } from '@angular/router';
import { getAllBranchesOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { BranchInfoDto } from '@app/core/modules/openapi';
import { ProgressBarModule } from 'primeng/progressbar';
import { DividerModule } from 'primeng/divider';
import { TooltipModule } from 'primeng/tooltip';
import { FormsModule } from '@angular/forms';

type BranchInfoWithLink = BranchInfoDto & { link: string; lastCommitLink: string };

@Component({
  selector: 'app-branches-table',
  imports: [
    TableModule,
    AvatarModule,
    TagModule,
    DividerModule,
    IconsModule,
    TooltipModule,
    SkeletonModule,
    ProgressBarModule,
    InputTextModule,
    TreeTableModule,
    ButtonModule,
    IconFieldModule,
    InputIconModule,
    InputTextModule,
    FormsModule,
  ],
  templateUrl: './branches-table.component.html',
})
export class BranchTableComponent {
  router = inject(Router);
  viewPreference = inject(BranchViewPreferenceService);

  featureBranchesTree = computed(() => this.convertBranchesToTreeNodes(this.branches()));

  query = injectQuery(() => getAllBranchesOptions());

  globalFilterFields = ['name', 'commitSha'];

  searchValue = signal<string>('');

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

  clearFilter(tt: TreeTable): void {
    tt.filterGlobal('', 'contains');
    this.searchValue.set('');
  }

  calculateProgress(value: number): number {
    return (value * 100) / this.maxAheadBehindBy();
  }

  openBranch(branch: BranchInfoDto): void {
    if (branch.repository?.id) {
      this.router.navigate(['repo', branch.repository?.id, 'ci-cd', 'branch', branch.name]);
    }
  }

  onInput(tt: TreeTable, event: Event): void {
    tt.filterGlobal((event.target as HTMLInputElement).value, 'contains');
  }

  convertBranchesToTreeNodes(branches: BranchInfoWithLink[]): TreeNode[] {
    const rootNodes: TreeNode[] = [];
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
            rootNodes.push(newNode);
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
    return rootNodes;
  }
}

interface TreeNode {
  data: Partial<BranchInfoWithLink> & {
    type?: 'Branch' | 'Folder';
  };
  children?: TreeNode[];
}
