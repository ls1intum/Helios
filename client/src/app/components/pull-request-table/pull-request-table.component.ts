import { MarkdownPipe } from '@app/core/modules/markdown/markdown.pipe';
import { AvatarGroupModule } from 'primeng/avatargroup';
import { TooltipModule } from 'primeng/tooltip';
import { Component, computed, inject, signal } from '@angular/core';
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
    DividerModule,
  ],
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
  router = inject(Router);
  route = inject(ActivatedRoute);
  keycloak = inject(KeycloakService);

  filterOptions = [
    { name: 'All pull requests', filter: (prs: PullRequestBaseInfoDto[]) => prs },
    { name: 'Open pull requests', filter: (prs: PullRequestBaseInfoDto[]) => prs.filter(pr => pr.state === 'OPEN') },
    { name: 'Your pull requests', filter: (prs: PullRequestBaseInfoDto[]) => prs.filter(pr => pr.author?.name === this.keycloak.profile?.username) },
    {
      name: 'Everything assigned to you',
      filter: (prs: PullRequestBaseInfoDto[]) => prs.filter(pr => pr.assignees?.some(assignee => assignee.name === this.keycloak.profile?.username)),
    },
    {
      name: 'Everything that requests a review by you',
      filter: (prs: PullRequestBaseInfoDto[]) => prs.filter(pr => pr.reviewers?.some(reviewer => reviewer.name === this.keycloak.profile?.username)),
    },
  ];

  activeFilter = signal<{ name: string; filter: (prs: PullRequestBaseInfoDto[]) => PullRequestBaseInfoDto[] }>(this.filterOptions[0]);

  query = injectQuery(() => getAllPullRequestsOptions());

  getPrIconInfo(pr: PullRequestInfoDto): { icon: string; color: string } {
    if (pr.isMerged) {
      return { icon: 'git-merge', color: 'text' };
    } else if (pr.state === 'CLOSED') {
      return { icon: 'git-pull-request-closed', color: 'text-red-500' };
    } else if (pr.isDraft) {
      return { icon: 'git-pull-request-draft', color: 'text-gray-600' };
    } else {
      return { icon: 'git-pull-request', color: 'text-green-600' };
    }
  }

  filteredPrs = computed(() => this.activeFilter().filter(this.query.data() || []));

  openPRExternal(pr: PullRequestInfoDto): void {
    window.open(pr.htmlUrl, '_blank');
  }

  labels = [
    { id: 1, description: '', name: 'todo', color: '00ff00' },
    { id: 3, description: 'Something is not working', name: 'bug', color: 'ff0000' },
  ];

  getLabelClasses(color: string) {
    return {
      'border-color': '#' + color,
      color: '#' + color,
      'background-color': '#' + color + '33',
    };
  }

  clearFilter(): void {
    this.activeFilter.set(this.filterOptions[0]);
  }

  openPR(pr: PullRequestInfoDto): void {
    this.router.navigate([pr.number], {
      relativeTo: this.route.parent,
    });
  }
}
