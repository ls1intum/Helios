import { describe, expect, it } from 'vitest';
import { routes } from './app.routes';
import { loggedInGuard } from './core/routeGuards/auth.guard';
import { writePermissionGuard } from './core/routeGuards/write-permission.guard';

describe('app routes', () => {
  it('protects the workflow run logs page with login and write-permission guards', () => {
    const mainLayoutRoute = routes.find(route => route.path === '');
    const repositoryRoute = mainLayoutRoute?.children?.find(route => route.path === 'repo/:repositoryId');
    const workflowRunLogsRoute = repositoryRoute?.children?.find(route => route.path === 'ci-cd/runs/:workflowRunId/logs');

    expect(workflowRunLogsRoute?.canActivate).toEqual([loggedInGuard, writePermissionGuard]);
  });
});
