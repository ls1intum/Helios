import { Component, signal, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink, RouterOutlet } from '@angular/router';
import { TabMenuModule } from 'primeng/tabmenu';
import { TabsModule } from 'primeng/tabs';
@Component({
  selector: 'app-ci-cd',
  imports: [RouterLink, RouterOutlet, TabMenuModule, TabsModule],
  templateUrl: './ci-cd.component.html',
})
export class CiCdComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  tabs = signal<{ label: string; id: string }[]>([
    { label: 'Pull Requests', id: 'pr' },
    { label: 'Branches', id: 'branch' },
  ]);

  activeTabId = this.tabs()[0].id;

  ngOnInit() {
    this.activeTabId = this.route.snapshot.firstChild?.url[0].path || this.tabs()[0].id;
  }
}
