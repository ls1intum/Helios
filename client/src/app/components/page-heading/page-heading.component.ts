import { Component, input } from '@angular/core';
import { getRepositoryByIdOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { DividerModule } from 'primeng/divider';

@Component({
  selector: 'app-page-heading',
  imports: [DividerModule],
  templateUrl: './page-heading.component.html',
})
export class PageHeadingComponent {
  repositoryId = input.required<number>();

  repositoryQuery = injectQuery(() => ({
    ...getRepositoryByIdOptions({ path: { id: this.repositoryId() } }),
    enabled: () => !!this.repositoryId(),
  }));
}
