import { Component, computed, Injectable, signal } from '@angular/core';
import { BranchTableComponent } from '@app/components/branches-table/branches-table.component';
import { PullRequestTableComponent } from '@app/components/pull-request-table/pull-request-table.component';
import { MenuItem } from 'primeng/api';
import { TabMenuModule } from 'primeng/tabmenu';
@Component({
  selector: 'app-ci-cd',
  imports: [PullRequestTableComponent, BranchTableComponent, TabMenuModule],
  templateUrl: './ci-cd.component.html',
})
export class CiCdComponent {
  tabs = signal<MenuItem[]>([
    { label: 'Pull Requests', id: 'pr' },
    { label: 'Branches', id: 'branches' }
  ]);

  activeTab = computed(() => {
    const activeTabId = this.stateService.getActiveTab()();
    return this.tabs().find(tab => tab.id === activeTabId) || this.tabs()[0];
  });

  constructor(private stateService: CiCdStateService) { }

  onTabChange(event: MenuItem) {
    if (event.id) {
      this.stateService.setActiveTab(event.id);
    }
  }

  isTabActive(tabId: string): boolean {
    return this.activeTab()?.id === tabId;
  }
}


@Injectable({
  providedIn: 'root'
})
export class CiCdStateService {
  private activeTabId = signal<string>('pr');

  setActiveTab(tabId: string) {
    this.activeTabId.set(tabId);
  }

  getActiveTab() {
    return this.activeTabId;
  }
}
