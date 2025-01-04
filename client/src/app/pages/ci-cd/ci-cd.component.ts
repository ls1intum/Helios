import { Component, signal, inject, OnInit, input, numberAttribute } from '@angular/core';
import { ActivatedRoute, RouterLink, RouterOutlet } from '@angular/router';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';
import { TabMenuModule } from 'primeng/tabmenu';
import { TabsModule } from 'primeng/tabs';
@Component({
  selector: 'app-ci-cd',
  imports: [RouterLink, RouterOutlet, TabMenuModule, TabsModule, PageHeadingComponent],
  templateUrl: './ci-cd.component.html',
})
export class CiCdComponent implements OnInit {
  private route = inject(ActivatedRoute);

  repositoryId = input.required({ transform: numberAttribute });

  tabs = signal<{ label: string; id: string }[]>([
    { label: 'Pull Requests', id: 'pr' },
    { label: 'Branches', id: 'branch' },
  ]);

  activeTabId = this.tabs()[0].id;

  ngOnInit() {
    this.activeTabId = this.route.snapshot.firstChild?.url[0].path || this.tabs()[0].id;
  }
}
