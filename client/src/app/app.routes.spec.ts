import { describe, expect, it } from 'vitest';
import { Route } from '@angular/router';
import { routes } from './app.routes';
import { maintainerGuard } from './core/routeGuards/maintainer.guard';

function collectPaths(list: Route[], prefix = ''): string[] {
  return list.flatMap(route => {
    const path = [prefix, route.path].filter(Boolean).join('/');
    return [path, ...collectPaths(route.children ?? [], path)];
  });
}

function findRoute(list: Route[], path: string): Route | undefined {
  for (const route of list) {
    if (route.path === path) return route;
    const nested = findRoute(route.children ?? [], path);
    if (nested) return nested;
  }
  return undefined;
}

describe('app routes', () => {
  const paths = collectPaths(routes);

  it.each(['release', 'flaky-tests', 'logs'])('does not register any route containing "%s"', fragment => {
    expect(paths.filter(path => path.includes(fragment))).toEqual([]);
  });

  it('keeps the repository pages reachable', () => {
    expect(paths).toEqual(expect.arrayContaining(['repo/:repositoryId/environment', 'repo/:repositoryId/ci-cd', 'repo/:repositoryId/settings']));
  });

  it('keeps the repository settings page restricted to maintainers', () => {
    const repositoryRoute = findRoute(routes, 'repo/:repositoryId');
    expect(findRoute(repositoryRoute?.children ?? [], 'settings')?.canActivate).toEqual([maintainerGuard]);
  });

  it('falls back to the not-found page for removed URLs', () => {
    expect(routes[routes.length - 1].path).toBe('**');
  });
});
