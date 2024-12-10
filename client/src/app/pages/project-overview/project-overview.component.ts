import { CommonModule } from '@angular/common';
import { Component, ViewChild } from '@angular/core';
import { ConnectRepoComponent } from '@app/components/connect-repo/connect-repo.component';
import { RepositoryService } from '@app/core/services/repository.service';
import { IconsModule } from 'icons.module';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { ChipModule } from 'primeng/chip';
import { DataViewModule } from 'primeng/dataview';
import { TagModule } from 'primeng/tag';

@Component({
  selector: 'app-project-overview',
  imports: [DataViewModule, ButtonModule, TagModule, CommonModule, CardModule, ChipModule, IconsModule, ConnectRepoComponent],
  templateUrl: './project-overview.component.html',
  styleUrl: './project-overview.component.css'
})
export class ProjectOverviewComponent {
  @ViewChild(ConnectRepoComponent)
  repositoryConnection!: ConnectRepoComponent;

  repositories;
  loading;

  constructor(private repositoryService: RepositoryService) {
    this.repositories = this.repositoryService.repositories;
    this.loading = this.repositoryService.loading;
  }

  showDialog() {
    this.repositoryConnection.show();
  }

  refreshRepositories() {
    this.repositoryService.refreshRepositories().subscribe();
  }

}
