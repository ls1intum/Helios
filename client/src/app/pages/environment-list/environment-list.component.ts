import { Component, inject } from '@angular/core';
import { AccordionModule } from 'primeng/accordion';
import { CommonModule } from '@angular/common';
import { LockTagComponent } from '@app/components/lock-tag/lock-tag.component';
import { TagModule } from 'primeng/tag';
import { IconsModule } from 'icons.module';
import { EnvironmentCommitInfoComponent } from '../../components/environment-commit-info/environment-commit-info.component';
import { ButtonModule } from 'primeng/button';
import { RouterLink } from '@angular/router';
import { FetchEnvironmentService } from '@app/core/services/fetch/environment';

@Component({
  selector: 'app-environment-list',
  imports: [AccordionModule, CommonModule, LockTagComponent, RouterLink, TagModule, IconsModule, EnvironmentCommitInfoComponent, ButtonModule],
  providers: [FetchEnvironmentService],
  templateUrl: './environment-list.component.html',
})
export class EnvironmentListComponent {
  fetchEnvironments = inject(FetchEnvironmentService);

  environments = this.fetchEnvironments.getEnvironments().data;
}
