import { MarkdownPipe } from '@app/core/modules/markdown/markdown.pipe';
import { AvatarGroupModule } from 'primeng/avatargroup';
import { TooltipModule } from 'primeng/tooltip';
import { Component, computed, inject } from '@angular/core';
import { TableModule } from 'primeng/table';
import { AvatarModule } from 'primeng/avatar';
import { TagModule } from 'primeng/tag';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { IconsModule } from 'icons.module';
import { SkeletonModule } from 'primeng/skeleton';
import { ActivatedRoute, Router } from '@angular/router';
import { DateService } from '@app/core/services/date.service';
import { getAllPullRequestsOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { PullRequestBaseInfoDto, PullRequestInfoDto } from '@app/core/modules/openapi';
import { ButtonModule } from 'primeng/button';
import { DividerModule } from 'primeng/divider';
import { SelectModule } from 'primeng/select';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { FormsModule } from '@angular/forms';
import { TimeAgoPipe } from '@app/pipes/time-ago.pipe';
import { FILTER_OPTIONS_TOKEN, SearchTableService } from '@app/core/services/search-table.service';
import { TableFilterComponent } from '../table-filter/table-filter.component';
import { WorkflowRunStatusComponent } from '@app/components/workflow-run-status-component/workflow-run-status.component';
import { PullRequestStatusIconComponent } from '@app/components/pull-request-status-icon/pull-request-status-icon.component';

const FILTER_OPTIONS = [
  { name: 'All pull requests', filter: (prs: PullRequestBaseInfoDto[]) => prs },
  { name: 'Open pull requests', filter: (prs: PullRequestBaseInfoDto[]) => prs.filter(pr => pr.state === 'OPEN') },
  { name: 'Your pull requests', filter: (prs: PullRequestBaseInfoDto[], username: string) => prs.filter(pr => pr.author?.login.toLowerCase() === username.toLowerCase()) },
  {
    name: 'Everything assigned to you',
    filter: (prs: PullRequestBaseInfoDto[], username: string) => prs.filter(pr => pr.assignees?.some(assignee => assignee.login.toLowerCase() === username.toLowerCase())),
  },
  {
    name: 'Everything that requests a review by you',
    filter: (prs: PullRequestBaseInfoDto[], username: string) => prs.filter(pr => pr.reviewers?.some(reviewer => reviewer.login.toLowerCase() === username.toLowerCase())),
  },
];

@Component({
  selector: 'app-pull-request-table',
  imports: [
    TableModule,
    AvatarModule,
    TagModule,
    TimeAgoPipe,
    FormsModule,
    SelectModule,
    IconsModule,
    SkeletonModule,
    AvatarGroupModule,
    TooltipModule,
    MarkdownPipe,
    ButtonModule,
    TableFilterComponent,
    DividerModule,
    WorkflowRunStatusComponent,
    PullRequestStatusIconComponent,
  ],
  providers: [SearchTableService, { provide: FILTER_OPTIONS_TOKEN, useValue: FILTER_OPTIONS }],
  templateUrl: './pull-request-table.component.html',
  styles: [
    `
      :host ::ng-deep {
        .p-avatar {
          img {
            width: 100%;
            height: 100%;
            object-fit: cover;
          }
        }
      }
    `,
  ],
})
export class PullRequestTableComponent {
  dateService = inject(DateService);
  searchTableService = inject(SearchTableService<PullRequestBaseInfoDto>);
  router = inject(Router);
  route = inject(ActivatedRoute);
  keycloak = inject(KeycloakService);

  query = injectQuery(() => getAllPullRequestsOptions());

  filteredPrs = computed(() => this.searchTableService.activeFilter().filter(this.query.data() || [], this.keycloak.decodedToken()?.preferred_username));

  openPRExternal(event: Event, pr: PullRequestInfoDto): void {
    window.open(pr.htmlUrl, '_blank');
    event.stopPropagation();
  }

  // TODO: Find a better way to handle color of labels
  getLabelClasses(color: string) {
    // Color code 'ededed' is used for labels with no color
    // In this case, we don't want to transparency effect
    // If we add transparency, then it's hard to read the text
    // Also text color of black is also for better readability
    return {
      'border-color': `#${color}`,
      color: '#000000',
      'background-color': color === 'ededed' ? `#${color}` : `#${color}75`,
    };
  }

  getAvatarBorderClass(login: string) {
    return this.keycloak.isCurrentUser(login) ? 'border-2 border-primary-400 rounded-full' : '';
  }

  openPR(pr: PullRequestInfoDto): void {
    this.router.navigate([pr.number], {
      relativeTo: this.route.parent,
    });
  }
}
