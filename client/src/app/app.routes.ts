
import { Routes } from '@angular/router';
import { MainLayoutComponent } from './pages/main-layout/main-layout.component';
import { CiCdComponent } from './pages/ci-cd/ci-cd.component';
import { EnvironmentEditComponent } from './pages/environment-edit/environment-edit.component';
import { EnvironmentListComponent } from './pages/environment-list/environment-list.component';
import { PageNotFoundComponent } from './pages/page-not-found/page-not-found.component';
import { ReleaseComponent } from './pages/release/release.component';
import {
  EnvironmentDeploymentHistoryComponent
} from '@app/pages/environment-deployment-history/environment-deployment-history.component';
import { ProjectOverviewComponent } from './pages/project-overview/project-overview.component';
import { PullRequestDetailsComponent } from './pages/pull-request-details/pull-request-details.component';
import { BranchDetailsComponent } from './pages/branch-details/branch-details.component';
import {ProjectSettingsComponent} from '@app/pages/project-settings/project-settings.component';


export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'projects'
  },
  {
    path: 'projects',
    component: ProjectOverviewComponent
  },
  {
    path: 'repo/:repositoryId',
    component: MainLayoutComponent,
    children: [
      { path: '', component: CiCdComponent },
      {
        path: 'pr',
        children: [
          { path: ':pullRequestNumber', component: PullRequestDetailsComponent },
        ]
      },
      {
        path: 'branch',
        children: [
          { path: ':branchName', component: BranchDetailsComponent },
        ]
      },
      {
        path: '',
        redirectTo: 'ci-cd',
        pathMatch: 'full'
      },
      {
        path: 'environment',
        children: [
          { path: '', redirectTo: 'list', pathMatch: 'full' },
          { path: 'list', component: EnvironmentListComponent },
          { path: ':id/edit', component: EnvironmentEditComponent },
          { path: ':id/history', component: EnvironmentDeploymentHistoryComponent },
        ],
      },
      {
        path: 'release',
        component: ReleaseComponent
      },
      {
        path: 'ci-cd',
        component: CiCdComponent
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
