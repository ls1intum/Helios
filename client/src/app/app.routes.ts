import { Routes } from '@angular/router';
import { MainLayoutComponent } from './pages/main-layout/main-layout.component';
import { CiCdComponent } from './pages/ci-cd/ci-cd.component';
import { EnvironmentEditComponent } from './pages/environment-edit/environment-edit.component';
import { EnvironmentListComponent } from './pages/environment-list/environment-list.component';
import { PageNotFoundComponent } from './pages/page-not-found/page-not-found.component';
import { ReleaseComponent } from './pages/release/release.component';
import { PullRequestPipelineComponent } from './pages/pull-request-pipeline/pull-request-pipeline.component';
import {
  EnvironmentDeploymentHistoryComponent
} from '@app/pages/environment-deployment-history/environment-deployment-history.component';
import { ProjectOverviewComponent } from './pages/project-overview/project-overview.component';
import { ProjectSettingsComponent } from '@app/pages/project-settings/project-settings.component';

export const routes: Routes = [
  {
    path: '',
    component: ProjectOverviewComponent
  },
  {
    path: ':orgName/:repoName',
    component: MainLayoutComponent,
    children: [
      {
        path: '',
        component: CiCdComponent
      },
      {
        path: 'branch/:branchName',
        component: PullRequestPipelineComponent,
        data: { type: 'branch' }
      },
      {
        path: 'pr/:prNumber',
        component: PullRequestPipelineComponent,
        data: { type: 'pr' }
      },
      {
        path: 'release',
        component: ReleaseComponent
      },
      {
        path: 'environments',
        children: [
          { path: '', component: EnvironmentListComponent },
          { path: ':id/edit', component: EnvironmentEditComponent },
          { path: ':id/history', component: EnvironmentDeploymentHistoryComponent },
        ]
      },
      {
        path: 'pipeline',
        children: [
          { path: 'pr/:pullRequestId', component: PullRequestPipelineComponent },
        ]
      },
      {
        path: 'settings',
        component: ProjectSettingsComponent
      },
    ]
  },
  {
    path: '**',
    component: PageNotFoundComponent,
  },
];
