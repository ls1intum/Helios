import { Routes } from '@angular/router';
import { RepositoryFilterGuard } from './core/middlewares/repository-filter.guard';
import { adminGuard } from './core/routeGuards/admin.guard';
import { maintainerGuard } from './core/routeGuards/maintainer.guard';
import { loggedInGuard } from '@app/core/routeGuards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/main-layout/main-layout.component').then(m => m.MainLayoutComponent),
    canActivateChild: [RepositoryFilterGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('./pages/repository-overview/repository-overview.component').then(m => m.RepositoryOverviewComponent),
      },
      {
        path: 'about',
        loadComponent: () => import('./pages/about/about.component').then(m => m.AboutComponent),
      },
      {
        path: 'privacy',
        loadComponent: () => import('./pages/privacy/privacy.component').then(m => m.PrivacyComponent),
      },
      {
        path: 'imprint',
        loadComponent: () => import('./pages/imprint/imprint.component').then(m => m.ImprintComponent),
      },
      {
        path: 'settings',
        canActivate: [loggedInGuard],
        loadComponent: () => import('./pages/user-settings/user-settings.component').then(m => m.UserSettingsComponent),
      },
      {
        path: 'repo/:repositoryId',
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
              {
                path: ':id/edit',
                loadComponent: () => import('./pages/environment-edit/environment-edit.component').then(m => m.EnvironmentEditComponent),
                canActivate: [adminGuard],
              },
              {
                path: ':environmentId/history',
                loadComponent: () => import('./pages/environment-deployment-history/environment-deployment-history.component').then(m => m.EnvironmentDeploymentHistoryComponent),
              },
            ],
          },
          {
            path: 'release',
            loadComponent: () => import('./pages/release/release.component').then(m => m.ReleaseComponent),
            canActivate: [maintainerGuard],
            children: [
              {
                path: '',
                redirectTo: 'list',
                pathMatch: 'full',
              },
              { path: 'list', loadComponent: () => import('./pages/release-candidate-list/release-candidate-list.component').then(m => m.ReleaseCanidateListComponent) },
              {
                path: ':name',
                loadComponent: () => import('./pages/release-candidate-details/release-candidate-details.component').then(m => m.ReleaseCandidateDetailsComponent),
              },
            ],
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
                  {
                    path: ':pullRequestNumber',
                    loadComponent: () => import('./pages/pull-request-details/pull-request-details.component').then(m => m.PullRequestDetailsComponent),
                  },
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
            canActivate: [maintainerGuard],
          },
        ],
      },
    ],
  },
  {
    path: 'unauthorized',
    loadComponent: () => import('./pages/unauthorized-page/unauthorized-page.component').then(m => m.UnauthorizedPageComponent),
  },
  {
    path: '**',
    loadComponent: () => import('./pages/page-not-found/page-not-found.component').then(m => m.PageNotFoundComponent),
  },
];
