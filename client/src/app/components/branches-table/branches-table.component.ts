import { Component, inject, Injectable, signal } from '@angular/core';

import { TableModule } from 'primeng/table';
import { AvatarModule } from 'primeng/avatar';
import { TagModule } from 'primeng/tag';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { catchError, tap } from 'rxjs';
import { IconsModule } from 'icons.module';
import { BranchControllerService, BranchInfoDTO } from '@app/core/modules/openapi';
import { SkeletonModule } from 'primeng/skeleton';


@Component({
  selector: 'app-branches-table',
  imports: [TableModule, AvatarModule, TagModule, IconsModule, SkeletonModule],
  templateUrl: './branches-table.component.html',
})
export class BranchTableComponent {
  branchService = inject(BranchControllerService);
  branchStore = inject(BranchStoreService);

  isError = signal(false);
  isEmpty = signal(false);
  isLoading = signal(false);


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
}

@Injectable({
  providedIn: 'root'
})
export class BranchStoreService {
  private branchesState = signal<BranchInforWithLink[]>([]);
  get branches() {
    return this.branchesState.asReadonly();
  }
  setBranches(branches: BranchInfoDTO[]) {
    branches = branches.map(branch => ({
      ...branch,
      link: `https://github.com/${branch!.repository!.nameWithOwner}/tree/${branch.name}`,
      lastCommitLink: `https://github.com/${branch!.repository!.nameWithOwner}/commit/${branch.commit_sha}`
    }));
    this.branchesState.set(branches as BranchInforWithLink[]);
  }
}

export type BranchInforWithLink = BranchInfoDTO & { link: string, lastCommitLink: string };
