import { Component, computed, Injectable, signal, inject } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { TabMenuModule } from 'primeng/tabmenu';
import { TabsModule } from 'primeng/tabs';
import { filter, last, map } from 'rxjs';
@Component({
  selector: 'app-ci-cd',
  imports: [RouterLink, RouterOutlet, TabMenuModule, TabsModule],
  templateUrl: './ci-cd.component.html',
})
export class CiCdComponent {
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  tabs = signal<{label: string, id: string}[]>([
    { label: 'Pull Requests', id: 'pr' },
    { label: 'Branches', id: 'branch' }
  ]);

  activeTabId = this.tabs()[0].id;

  ngOnInit() {
    this.activeTabId = this.route.snapshot.firstChild?.url[0].path || this.tabs()[0].id;
  }
}
