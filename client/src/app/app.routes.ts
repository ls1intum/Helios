import { Routes } from '@angular/router';
import { EnvironmentListComponent } from './pages/environment-list/environment-list.component';
import { EnvironmentEditComponent } from './pages/environment-edit/environment-edit.component';
import { PrListComponent } from './pages/pr-list/pr-list.component';
import { ReleaseComponent } from './pages/release/release.component';
import { PageNotFoundComponent } from './pages/page-not-found/page-not-found.component';

export const routes: Routes = [
  { path: '', redirectTo: 'pr/list', pathMatch: 'full' },
  {
    path: 'environment',
    children: [
      { path: '', redirectTo: 'list', pathMatch: 'full' },
      { path: 'list', component: EnvironmentListComponent },
      { path: ':id/edit', component: EnvironmentEditComponent },
    ],
  },
  {
    path: 'release',
    children: [{ path: '', component: ReleaseComponent }],
  },
  {
    path: 'pr',
    children: [
      { path: '', redirectTo: 'list', pathMatch: 'full' },
      { path: 'list', component: PrListComponent },
    ],
  },
  {
    path: '**',
    component: PageNotFoundComponent,
  },
];
