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
import { BranchInfoDto, RepositoryInfoDto } from '@app/core/modules/openapi';

type BranchInfoWithLink = BranchInfoDto & { link: string; lastCommitLink: string };

@Component({
  selector: 'app-branches-table',
  imports: [TableModule, AvatarModule, TagModule, IconsModule, SkeletonModule, InputTextModule, TreeTableModule, ButtonModule, IconFieldModule, InputIconModule],
  templateUrl: './branches-table.component.html',
})
export class BranchTableComponent {
  router = inject(Router);
  featureBranchesTree = computed(() => this.convertBranchesToTreeNodes(this.getFeatureBranches()));

  specialBranches = ['master', 'main', 'dev', 'staging', 'development', 'prod', 'production', 'develop'];

  getSpecialBranches() {
    return this.branches().filter(branch => this.specialBranches.includes(branch.name.toLowerCase()));
  }

  getFeatureBranches() {
    return this.branches().filter(branch => !this.specialBranches.includes(branch.name.toLowerCase()));
  }

  query = injectQuery(() => getAllBranchesOptions());

  branches = computed<BranchInfoWithLink[]>(
    () =>
      this.query.data()?.map(branch => ({
        ...branch,
        link: `https://github.com/${branch!.repository!.nameWithOwner}/tree/${branch.name}`,
        lastCommitLink: `https://github.com/${branch!.repository!.nameWithOwner}/commit/${branch.commitSha}`,
      })) || []
  );

  openLink(url: string): void {
    window.open(url, '_blank');
  }

  openBranch(branch: BranchInfoDto): void {
    this.router.navigate(['repo', branch.repository?.id, 'ci-cd', 'branch', branch.name]);
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
            newNode.data.commit_sha = branch.commitSha;
            newNode.data.repository = branch.repository;
            newNode.data.lastCommitLink = branch.lastCommitLink;
            newNode.data.link = branch.link;
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

  viewPreference = inject(BranchViewPreferenceService);
  toggleView() {
    this.viewPreference.toggleViewMode();
  }
}

interface TreeNode {
  data: {
    name: string;
    commit_sha?: string;
    repository?: RepositoryInfoDto;
    link?: string;
    lastCommitLink?: string;
    type?: 'Branch' | 'Folder';
  };
  children?: TreeNode[];
}
