import { Component, computed, inject, Injectable, signal, ViewChild } from '@angular/core';

import { Table, TableModule } from 'primeng/table';
import { AvatarModule } from 'primeng/avatar';
import { TagModule } from 'primeng/tag';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { catchError, tap } from 'rxjs';
import { IconsModule } from 'icons.module';
import { BranchControllerService, BranchInfoDTO } from '@app/core/modules/openapi';
import { SkeletonModule } from 'primeng/skeleton';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { IconFieldModule } from 'primeng/iconfield';
import { TreeTableModule } from 'primeng/treetable';
import { ButtonModule } from 'primeng/button';
import { CommonModule } from '@angular/common';
import { BranchViewPreferenceService } from '@app/core/services/branches-table/branch-view-preference';
import { Router } from '@angular/router';


@Component({
  selector: 'app-branches-table',
  imports: [
    TableModule,
    AvatarModule,
    TagModule,
    IconsModule,
    SkeletonModule,
    InputTextModule,
    TreeTableModule,
    CommonModule,
    ButtonModule,
    IconFieldModule,
    InputIconModule],
  templateUrl: './branches-table.component.html',
})
export class BranchTableComponent {

  branchService = inject(BranchControllerService);
  branchStore = inject(BranchStoreService);
  router = inject(Router);

  featureBranchesTree = computed(() => this.convertBranchesToTreeNodes(this.getFeatureBranches()));
  isError = signal(false);
  isEmpty = signal(false);
  isLoading = signal(false);

  specialBranches = ['master', 'main', 'dev', 'staging', 'development', 'prod', 'production', 'develop'];

  getSpecialBranches() {
    return this.branchStore.branches().filter(branch =>
      this.specialBranches.includes(branch.name.toLowerCase())
    );
  }

  getFeatureBranches() {
    const featureBranches = this.branchStore.branches().filter(branch =>
      !this.specialBranches.includes(branch.name.toLowerCase())
    );
    return featureBranches
  }


  query = injectQuery(() => ({
    queryKey: ['branches'],
    queryFn: () => {
      this.isLoading.set(true);
      return this.branchService.getAllBranches()
        .pipe(
          tap(data => {
            this.branchStore.setBranches(data);
            this.isEmpty.set(data.length === 0);
            this.isLoading.set(false);
          }),
          catchError(() => {
            this.isError.set(true);
            this.isLoading.set(false);
            return [];
          }
          )
        ).subscribe()
    },
  }));

  openLink(url: string): void {
    window.open(url, '_blank');
  }

  openBranch(branch: BranchInfoDTO): void {
    this.router.navigate(['repo', branch.repository?.id, 'branch', branch.name]);
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
            }
          };

          // If it's a leaf node, add the branch info
          if (isLeaf) {
            newNode.data.commit_sha = branch.commit_sha;
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
    console.log("root nodes", JSON.stringify(rootNodes, null, 2));
    return rootNodes;
  }

  viewPreference = inject(BranchViewPreferenceService);
  toggleView() {
    this.viewPreference.toggleViewMode();
  }


}

@Injectable({
  providedIn: 'root'
})
export class BranchStoreService {
  private branchesState = signal<BranchInfoWithLink[]>([]);
  get branches() {
    return this.branchesState.asReadonly();
  }
  setBranches(branches: BranchInfoDTO[]) {
    branches = branches.map(branch => ({
      ...branch,
      link: `https://github.com/${branch!.repository!.nameWithOwner}/tree/${branch.name}`,
      lastCommitLink: `https://github.com/${branch!.repository!.nameWithOwner}/commit/${branch.commit_sha}`
    }));
    this.branchesState.set(branches as BranchInfoWithLink[]);
  }
}

export type BranchInfoWithLink = BranchInfoDTO & { link: string, lastCommitLink: string };

interface TreeNode {
  data: {
    name: string;
    commit_sha?: string;
    repository?: any;
    link?: string;
    lastCommitLink?: string;
    type?: 'Branch' | 'Folder';
  };
  children?: TreeNode[];
}

