
import { Routes } from '@angular/router';











export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'projects'
  },
  {
    path: 'projects',
    loadComponent: () => import('./pages/project-overview/project-overview.component').then(m => m.ProjectOverviewComponent)
  },
  {
    path: 'project/:projectId',
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
          { path: 'list', loadComponent: () => import('./pages/environment-list/environment-list.component').then(m => m.EnvironmentListComponent) },
          { path: ':id/edit', loadComponent: () => import('./pages/environment-edit/environment-edit.component').then(m => m.EnvironmentEditComponent) },
          { path: ':environmentId/history', loadComponent: () => import('./pages/environment-deployment-history/environment-deployment-history.component').then(m => m.EnvironmentDeploymentHistoryComponent) },
        ],
      },
      {
        path: 'release',
        loadComponent: () => import('./pages/release/release.component').then(m => m.ReleaseComponent)
      },
      {
        path: 'ci-cd',
        loadComponent: () => import('./pages/ci-cd/ci-cd.component').then(m => m.CiCdComponent)
      },
      {
        path: 'pipeline',
        children: [
          { path: 'pr/:pullRequestId', loadComponent: () => import('./pages/pull-request-pipeline/pull-request-pipeline.component').then(m => m.PullRequestPipelineComponent) },
        ]
      },
      {
        path: 'settings',
        loadComponent: () => import('./pages/project-settings/project-settings.component').then(m => m.ProjectSettingsComponent)
      },
    ]
  },
  {
    path: '**',
    loadComponent: () => import('./pages/page-not-found/page-not-found.component').then(m => m.PageNotFoundComponent),
  },
];
