import { Routes } from '@angular/router';
import { RepositoryFilterGuard } from './core/middlewares/repository-filter.guard';
import { adminGuard } from './core/routeGuards/admin.guard';
import { maintainerGuard } from './core/routeGuards/maintainer.guard';
import { loggedInGuard } from '@app/core/routeGuards/auth.guard';
import { writePermissionGuard } from '@app/core/routeGuards/write-permission.guard';

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
        // Cross-repo list of deployments waiting on the current user as a required reviewer.
        // Intentionally not nested under /repo/:id — a reviewer's queue spans every repo.
        path: 'pending-approvals',
        canActivate: [loggedInGuard],
        loadComponent: () => import('./pages/pending-approvals/pending-approvals.component').then(m => m.PendingApprovalsComponent),
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
            path: 'flaky-tests',
            loadComponent: () => import('./pages/flaky-tests-overview/flaky-tests-overview.component').then(m => m.FlakyTestsOverviewComponent),
          },
          {
            path: 'ci-cd/runs/:workflowRunId/logs',
            canActivate: [loggedInGuard, writePermissionGuard],
            loadComponent: () => import('./pages/workflow-run-logs/workflow-run-logs.component').then(m => m.WorkflowRunLogsComponent),
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
                path: 'runs/:runId',
                loadComponent: () => import('@app/pages/workflow-run-details/workflow-run-details.component').then(m => m.WorkflowRunDetailsComponent),
              },
              {
                path: 'runs',
                loadComponent: () => import('@app/pages/workflow-run-list/workflow-run-list.component').then(m => m.WorkflowRunListComponent),
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
              {
                path: 'queue',
                loadChildren: () => import('./pages/queue/queue.routes').then(m => m.queueRoutes),
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
    // Admin org-wide overview only. Repo-scoped stats/alerts have no meaning here so are not
    // exposed at the top level — those live under /repo/:repositoryId/ci-cd/queue/*.
    path: 'queue',
    canActivate: [adminGuard],
    loadComponent: () => import('./pages/queue/queue-overview.component').then(m => m.QueueOverviewComponent),
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
