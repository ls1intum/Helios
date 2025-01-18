import { Component, inject, input, numberAttribute } from '@angular/core';
import { EnvironmentListViewComponent } from '@app/components/environments/environment-list/environment-list-view.component';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';
import { PermissionService } from '@app/core/services/permission.service';

@Component({
  selector: 'app-environment-list',
  imports: [EnvironmentListViewComponent, PageHeadingComponent],
  templateUrl: './environment-list.component.html',
})
export class EnvironmentListComponent {
  repositoryId = input.required({ transform: numberAttribute });
  permissionService = inject(PermissionService);
}
