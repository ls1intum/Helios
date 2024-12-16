// project-settings.component.ts

import { Component, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { injectQuery } from '@tanstack/angular-query-experimental';

import { TableModule } from 'primeng/table';
import { DropdownModule } from 'primeng/dropdown';
import { ButtonModule } from 'primeng/button';
import { PanelModule } from 'primeng/panel';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
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
    DragDropModule,
    DialogModule,
    InputTextModule,
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

  showAddGroupDialog = false;
  newGroupName = '';

  get groupDropdownOptions() {
    return this.availableGroups().map(g => ({ label: g, value: g }));
  }

  // Sort and group logic
  readonly groupedWorkflowsArray = computed<GroupedWorkflows[]>(() => {
    const map = this.workflowGroupsMap();
    const wfs = this.workflows();
    const record: Record<string, WorkflowDTO[]> = {};

    wfs.forEach(wf => {
      const group = map[wf.id] ?? 'Ungrouped';
      if (!record[group]) record[group] = [];
      record[group].push(wf);
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
        // Example custom sorting logic
        data.sort((a, b) => {
          // "Active" on top, then alphabetical by state, then by ID
          if (a.state === WorkflowDTO.StateEnum.Active && b.state !== WorkflowDTO.StateEnum.Active) return -1;
          if (a.state !== WorkflowDTO.StateEnum.Active && b.state === WorkflowDTO.StateEnum.Active) return 1;
          if (a.state !== b.state) return a.state.localeCompare(b.state);
          return a.id - b.id;
        });
        this.workflows.set(data);

        // Default each workflow to 'Frontend'
        const newMap: Record<number, string> = {};
        data.forEach(wf => { newMap[wf.id] = 'Frontend'; });
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

    if (!this.availableGroups().includes(newGroup)) {
      this.availableGroups.update(groups => [...groups, newGroup]);
      this.groupOrder.update(order => [...order, newGroup]);
    }
  }

  addNewGroup() {
    // If empty or whitespace, just close
    if (!this.newGroupName.trim()) {
      this.resetDialog();
      return;
    }

    const g = this.newGroupName.trim();
    if (!this.availableGroups().includes(g)) {
      this.availableGroups.update(arr => [...arr, g]);
      this.groupOrder.update(order => [...order, g]);
    }
    this.resetDialog();
  }

  // Clear the newGroupName when p-dialog is hidden or the user presses Cancel
  resetDialog() {
    this.newGroupName = '';
    this.showAddGroupDialog = false;
  }

  // Drag & drop logic
  private dragIndex: number | null = null;

  dragStart(fromIndex: number) {
    this.dragIndex = fromIndex;
  }

  drop(targetIndex: number) {
    if (this.dragIndex === null || this.dragIndex === targetIndex) return;

    const current = this.groupedWorkflowsArray();
    if (!current) return;

    const newArray = [...current];
    const [dragged] = newArray.splice(this.dragIndex, 1);
    newArray.splice(targetIndex, 0, dragged);

    const newOrder = newArray.map(g => g.groupName);
    this.groupOrder.set(newOrder);

    this.dragIndex = null;
  }
}
