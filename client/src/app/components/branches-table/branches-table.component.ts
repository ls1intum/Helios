import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { AvatarModule } from 'primeng/avatar';
import { TagModule } from 'primeng/tag';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { tap } from 'rxjs';
import { IconsModule } from 'icons.module';
import { BranchControllerService, BranchInfoDTO } from '@app/core/modules/openapi';


@Component({
  selector: 'app-branches-table',
  imports: [CommonModule, TableModule, AvatarModule, TagModule, IconsModule],
  templateUrl: './branches-table.component.html',
})
export class BranchTableComponent {
  branchService = inject(BranchControllerService);


  branches = signal<BranchInfoDTO[]>([]);
  isLoading = signal(true);

  query = injectQuery(() => ({
    queryKey: ['branches'],
    queryFn: () => this.branchService.getAllBranches()
        .pipe(
          tap(data => {
            this.branches.set(data);
            this.isLoading.set(false);
          })
        ).subscribe(),
  }));

  // openBranch(url: string): void {
  //   window.open(url, '_blank');
  // }
}
