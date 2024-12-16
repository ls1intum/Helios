import { Component, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { injectQuery } from '@tanstack/angular-query-experimental';

import { TableModule } from 'primeng/table';
import { DropdownModule } from 'primeng/dropdown';
import { ButtonModule } from 'primeng/button';
import { PanelModule } from 'primeng/panel';
import { IconsModule } from 'icons.module';
import { DragDropModule } from 'primeng/dragdrop';

import { WorkflowControllerService } from '@app/core/modules/openapi/api/workflow-controller.service';
import { WorkflowDTO } from '@app/core/modules/openapi/model/workflow-dto';
import { firstValueFrom } from 'rxjs';

interface GroupedWorkflows {
  groupName: string;
  workflows: WorkflowDTO[];
}

@Component({
  selector: 'app-project-settings',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TableModule,
    DropdownModule,
    ButtonModule,
    PanelModule,
    IconsModule,
    DragDropModule, // <-- PrimeNG dragdrop
  ],
  templateUrl: './project-settings.component.html',
  styleUrls: ['./project-settings.component.css'],
})
export class ProjectSettingsComponent {
  private workflowService = inject(WorkflowControllerService);

  workflows = signal<WorkflowDTO[]>([]);
  isLoading = signal(false);
  isError = signal(false);

  workflowGroupsMap = signal<Record<number, string>>({});
  groupOrder = signal<string[]>(['Frontend', 'Backend', 'Database', 'DevOps', 'Ungrouped']);
  availableGroups = signal<string[]>(['Frontend', 'Backend', 'Database', 'DevOps']);

  get groupDropdownOptions() {
    return this.availableGroups().map((g) => ({ label: g, value: g }));
  }

  readonly groupedWorkflowsArray = computed<GroupedWorkflows[]>(() => {
    const map = this.workflowGroupsMap();
    const wfs = this.workflows();
    const record: Record<string, WorkflowDTO[]> = {};

    wfs.forEach((wf) => {
      const groupName = map[wf.id] ?? 'Ungrouped';
      if (!record[groupName]) record[groupName] = [];
      record[groupName].push(wf);
    });

    let entries = Object.entries(record).map(([groupName, workflows]) => ({
      groupName,
      workflows,
    }));

    // Sort by groupOrder
    const orderArr = this.groupOrder();
    entries.sort((a, b) => {
      const idxA = orderArr.indexOf(a.groupName);
      const idxB = orderArr.indexOf(b.groupName);
      if (idxA !== -1 && idxB !== -1) return idxA - idxB;
      if (idxA === -1 && idxB !== -1) return 1;
      if (idxA !== -1 && idxB === -1) return -1;
      return a.groupName.localeCompare(b.groupName);
    });

    return entries;
  });

  query = injectQuery(() => ({
    queryKey: ['allWorkflows'],
    queryFn: async () => {
      this.isLoading.set(true);
      try {
        const data = await firstValueFrom(this.workflowService.getAllWorkflows());
        // group data by WorkflowDTO.StateEnum
        // WorkflowDTO.StateEnum.Active should come first, then rest of the states
        // within the same group sort them by id
        data.sort((a, b) => {
          if (a.state === WorkflowDTO.StateEnum.Active && b.state !== WorkflowDTO.StateEnum.Active) return -1;
          if (a.state !== WorkflowDTO.StateEnum.Active && b.state === WorkflowDTO.StateEnum.Active) return 1;
          if (a.state !== b.state) return a.state.localeCompare(b.state);
          return a.id - b.id;
        });
        this.workflows.set(data);

        const newMap: Record<number, string> = {};
        data.forEach((wf) => {
          newMap[wf.id] = 'Frontend';
        });
        this.workflowGroupsMap.set(newMap);

        this.isLoading.set(false);
        return data;
      } catch (err) {
        console.error('Error fetching workflows', err);
        this.isError.set(true);
        this.isLoading.set(false);
        throw err;
      }
    },
    refetchOnMount: true,
    refetchOnWindowFocus: false,
    staleTime: 0,
    cacheTime: 60_000,
  }));

  onChangeGroup(workflow: WorkflowDTO, newGroup: string) {
    const oldMap = this.workflowGroupsMap();
    const updatedMap = { ...oldMap, [workflow.id]: newGroup };
    this.workflowGroupsMap.set(updatedMap);

    // If newGroup not in groupOrder/available, add it
    if (!this.availableGroups().includes(newGroup)) {
      this.availableGroups.update((g) => [...g, newGroup]);
      this.groupOrder.update((order) => [...order, newGroup]);
    }
  }

  addNewGroup() {
    const groupName = prompt('Enter a new group name:');
    if (!groupName) return;

    if (!this.availableGroups().includes(groupName)) {
      this.availableGroups.update((groups) => [...groups, groupName]);
      this.groupOrder.update((order) => [...order, groupName]);
    }
  }

  private dragIndex: number | null = null;

  /**
   * Called when user starts dragging a panel
   */
  dragStart(fromIndex: number) {
    this.dragIndex = fromIndex;
  }

  /**
   * Called when dropping onto a new position
   */
  drop(targetIndex: number) {
    if (this.dragIndex === null || this.dragIndex === targetIndex) {
      return;
    }

    const current = this.groupedWorkflowsArray();
    if (!current) return;

    const newArray = [...current];
    // remove the dragged item
    const [dragged] = newArray.splice(this.dragIndex, 1);
    // insert at new position
    newArray.splice(targetIndex, 0, dragged);

    // update groupOrder
    const newOrder = newArray.map((group) => group.groupName);
    this.groupOrder.set(newOrder);

    this.dragIndex = null;
  }
}
