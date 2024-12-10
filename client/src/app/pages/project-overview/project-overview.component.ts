import { CommonModule } from '@angular/common';
import { Component, signal, ViewChild } from '@angular/core';
import { ConnectRepoComponent } from '@app/components/connect-repo/connect-repo.component';
import { RepositoryInfoDTO } from '@app/core/modules/openapi';
import { RepositoryService } from '@app/core/services/repository';
import { IconsModule } from 'icons.module';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { ChipModule } from 'primeng/chip';
import { DataViewModule, DataView } from 'primeng/dataview';
import { SelectButton, SelectButtonModule } from 'primeng/selectbutton';
import { Tag, TagModule } from 'primeng/tag';

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
