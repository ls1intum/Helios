import { Routes } from '@angular/router';

export const queueRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('./queue-overview.component').then(m => m.QueueOverviewComponent),
  },
  {
    path: 'runners',
    loadComponent: () => import('./runner-list/runner-list.component').then(m => m.RunnerListComponent),
  },
  {
    path: 'stats',
    loadComponent: () => import('./queue-stats/queue-stats.component').then(m => m.QueueStatsComponent),
  },
  {
    path: 'alerts',
    loadComponent: () => import('./queue-alerts/queue-alerts.component').then(m => m.QueueAlertsComponent),
  },
];
