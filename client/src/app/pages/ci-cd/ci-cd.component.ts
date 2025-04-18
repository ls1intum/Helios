import { Component, signal, inject, OnInit, input, numberAttribute, OnDestroy } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';
import { TabMenuModule } from 'primeng/tabmenu';
import { TabsModule } from 'primeng/tabs';
import { filter, Subscription } from 'rxjs';
@Component({
  selector: 'app-ci-cd',
  imports: [RouterLink, RouterOutlet, TabMenuModule, TabsModule, PageHeadingComponent],
  templateUrl: './ci-cd.component.html',
})
export class CiCdComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private subscription = new Subscription();

  repositoryId = input.required({ transform: numberAttribute });

  tabs = signal<{ label: string; id: string }[]>([
    { label: 'Pull Requests', id: 'pr' },
    { label: 'Branches', id: 'branch' },
  ]);
  activeTabId = signal(this.tabs()[0].id);

  ngOnInit() {
    this.updateActiveTab();

    // Subscribe to router events to update the active tab when navigation occurs
    this.subscription.add(
      this.router.events.pipe(filter(event => event instanceof NavigationEnd)).subscribe(() => {
        this.updateActiveTab();
      })
    );
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  private updateActiveTab() {
    const path = this.route.snapshot.firstChild?.url[0]?.path;
    if (path) {
      this.activeTabId.set(path);
    }
  }
}
