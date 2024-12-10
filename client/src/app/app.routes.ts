
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
    path: 'project/:projectId',
    component: MainLayoutComponent,
    children: [
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
        path: 'pipeline',
        children: [
          { path: 'pr/:pullRequestId', component: PullRequestPipelineComponent },
        ]
      }
    ]
  },
  {
    path: '**',
    component: PageNotFoundComponent,
  },
];