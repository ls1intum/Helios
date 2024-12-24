import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'projects',
  },
  {
    path: 'projects',
    loadComponent: () => import('./pages/project-overview/project-overview.component').then(m => m.ProjectOverviewComponent),
  },
  {
    path: 'repo/:repositoryId',
    loadComponent: () => import('./pages/main-layout/main-layout.component').then(m => m.MainLayoutComponent),
    children: [
      { path: '', loadComponent: () => import('./pages/ci-cd/ci-cd.component').then(m => m.CiCdComponent) },
      {
        path: '',
        redirectTo: 'ci-cd',
        pathMatch: 'full',
      },
      {
        path: 'environment',
        children: [
          { path: '', redirectTo: 'list', pathMatch: 'full' },
          { path: 'list', loadComponent: () => import('./pages/environment-list/environment-list.component').then(m => m.EnvironmentListComponent) },
          { path: ':id/edit', loadComponent: () => import('./pages/environment-edit/environment-edit.component').then(m => m.EnvironmentEditComponent) },
          {
            path: ':environmentId/history',
            loadComponent: () => import('./pages/environment-deployment-history/environment-deployment-history.component').then(m => m.EnvironmentDeploymentHistoryComponent),
          },
        ],
      },
      {
        path: 'release',
        loadComponent: () => import('./pages/release/release.component').then(m => m.ReleaseComponent),
      },
      {
        path: 'ci-cd',
        loadComponent: () => import('./pages/ci-cd/ci-cd.component').then(m => m.CiCdComponent),
        children: [
          {
            path: '',
            redirectTo: 'pr',
            pathMatch: 'full',
          },
          {
            path: 'pr',
            children: [
              { path: '', loadComponent: () => import('./pages/pull-request-list/pull-request-list.component').then(m => m.PullRequestListComponent) },
              { path: ':pullRequestNumber', loadComponent: () => import('./pages/pull-request-details/pull-request-details.component').then(m => m.PullRequestDetailsComponent) },
            ],
          },
          {
            path: 'branch',
            children: [
              { path: '', loadComponent: () => import('./pages/branch-list/branch-list.component').then(m => m.BranchListComponent) },
              { path: ':branchName', loadComponent: () => import('./pages/branch-details/branch-details.component').then(m => m.BranchDetailsComponent) },
            ],
          },
        ],
      },
      {
        path: 'settings',
        loadComponent: () => import('./pages/project-settings/project-settings.component').then(m => m.ProjectSettingsComponent),
      },
    ],
  },
  {
    path: '**',
    loadComponent: () => import('./pages/page-not-found/page-not-found.component').then(m => m.PageNotFoundComponent),
  },
];
