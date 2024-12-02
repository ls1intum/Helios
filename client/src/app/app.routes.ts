import { Routes } from '@angular/router';
import { CiCdComponent } from './pages/ci-cd/ci-cd.component';
import { EnvironmentEditComponent } from './pages/environment-edit/environment-edit.component';
import { EnvironmentListComponent } from './pages/environment-list/environment-list.component';
import { PageNotFoundComponent } from './pages/page-not-found/page-not-found.component';
import { ReleaseComponent } from './pages/release/release.component';

export const routes: Routes = [
  { path: '', redirectTo: 'ci-cd', pathMatch: 'full' },
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
    path: 'ci-cd',
    children: [
      { path: '', component: CiCdComponent },
    ],
  },
  {
    path: '**',
    component: PageNotFoundComponent,
  },
];
