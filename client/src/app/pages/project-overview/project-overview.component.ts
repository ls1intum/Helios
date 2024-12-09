import { CommonModule } from '@angular/common';
import { Component, signal } from '@angular/core';
import { RepositoryInfoDTO } from '@app/core/modules/openapi';
import { IconsModule } from 'icons.module';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { ChipModule } from 'primeng/chip';
import { DataViewModule, DataView } from 'primeng/dataview';
import { SelectButton, SelectButtonModule } from 'primeng/selectbutton';
import { Tag, TagModule } from 'primeng/tag';

@Component({
  selector: 'app-project-overview',
  imports: [DataViewModule, ButtonModule, TagModule, CommonModule, CardModule, ChipModule, IconsModule],
  templateUrl: './project-overview.component.html',
  styleUrl: './project-overview.component.css'
})
export class ProjectOverviewComponent {

  repositories = signal<RepositoryInfoDTO[]>([{
    "id": 69562331,
    "name": "Artemis",
    "nameWithOwner": "ls1intum/Artemis",
    "description": "Artemis - Interactive Learning with Automated Feedback",
    "htmlUrl": "https://github.com/ls1intum/Artemis"
  }, {
    "id": 69562332,
    "name": "Thesis Track",
    "nameWithOwner": "ls1intum/ThesisTrack",
    "description": "Thesis Track - A tool for managing theses",
    "htmlUrl": "https://github.com/ls1intum/ThesisTrack"
  }]);

  showDialog() {
    console.log('show dialog');

  }

}
