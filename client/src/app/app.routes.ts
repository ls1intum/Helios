import { Routes } from '@angular/router';
import { CiCdComponent } from './pages/ci-cd/ci-cd.component';
import { EnvironmentEditComponent } from './pages/environment-edit/environment-edit.component';
import { PageNotFoundComponent } from './pages/page-not-found/page-not-found.component';
import { ReleaseComponent } from './pages/release/release.component';
import {
  EnvironmentDeploymentHistoryComponent
} from '@app/pages/environment-deployment-history/environment-deployment-history.component';
import { BranchDetailsComponent } from './pages/branch-details/branch-details.component';
import { EnvironmentListComponent } from './pages/environment-list/environment-list.component';
import { PullRequestDetailsComponent } from './pages/pull-request-details/pull-request-details.component';

export const routes: Routes = [
  { path: '', redirectTo: 'ci-cd', pathMatch: 'full' },
  {
    path: 'environments',
    children: [
      { path: '', component: EnvironmentListComponent },
      { path: ':id/edit', component: EnvironmentEditComponent },
      { path: ':id/history', component: EnvironmentDeploymentHistoryComponent },
    ],
  },
  {
    path: 'release',
    children: [{ path: '', component: ReleaseComponent }],
  },
  {
    path: 'ci-cd',
    children: [
      { path: '', component: CiCdComponent },
    ],
  },
  {
    path: 'repo/:repositoryId',
    children: [
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
    ],
  },
  {
    path: '**',
    component: PageNotFoundComponent,
  },
];
