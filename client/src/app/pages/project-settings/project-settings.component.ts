import {Component, signal, computed, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {injectQuery} from '@tanstack/angular-query-experimental';

import {TableModule} from 'primeng/table';
import {DropdownModule} from 'primeng/dropdown';
import {ButtonModule} from 'primeng/button';
import {PanelModule} from 'primeng/panel';
import {DialogModule} from 'primeng/dialog';
import {InputTextModule} from 'primeng/inputtext';
import {CheckboxModule} from 'primeng/checkbox';
import {IconsModule} from 'icons.module';
import {DragDropModule} from 'primeng/dragdrop';

import {WorkflowControllerService} from '@app/core/modules/openapi/api/workflow-controller.service';
import {WorkflowDTO} from '@app/core/modules/openapi/model/workflow-dto';
import {firstValueFrom} from 'rxjs';

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
    DialogModule,
    InputTextModule,
    CheckboxModule,
    IconsModule,
    DragDropModule,
  ],
  templateUrl: './project-settings.component.html',
  styleUrls: ['./project-settings.component.css'],
})
export class ProjectSettingsComponent {
  private workflowService = inject(WorkflowControllerService);

  // State management
  workflows = signal<WorkflowDTO[]>([]);
  isLoading = signal(false);
  isError = signal(false);

  // Initially, no groups assigned
  workflowGroupsMap = signal<Record<number, string>>({});
  // Define your group order
  groupOrder = signal<string[]>(['Frontend', 'Backend', 'Database', 'DevOps']);
  // Available groups
  availableGroups = signal<string[]>(['Frontend', 'Backend', 'Database', 'DevOps']);

  // UI Controls
  showAddGroupDialog = false;
  newGroupName = '';

  // Dropdown options including 'Ungrouped'
  get groupDropdownOptions() {
    return [
      {label: 'Ungrouped', value: 'Ungrouped'}, // Option to ungroup
      ...this.availableGroups().map(g => ({label: g, value: g})),
    ];
  }

  // Computed property to group workflows
  readonly groupedWorkflowsArray = computed<GroupedWorkflows[]>(() => {
    const map = this.workflowGroupsMap();
    const wfs = this.workflows();
    const record: Record<string, WorkflowDTO[]> = {};

    wfs.forEach(wf => {
      const groupName = map[wf.id] ?? 'Ungrouped';

      // If 'Ungrouped' workflows should not be shown in grouped panels
      if (groupName === 'Ungrouped') {
        return;
      }

      if (!record[groupName]) {
        record[groupName] = [];
      }
      record[groupName].push(wf);
    });

    let entries = Object.entries(record).map(([groupName, workflows]) => ({
      groupName,
      workflows,
    }));

    // Sort groups based on groupOrder
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

  // Data fetching with Angular Query
  query = injectQuery(() => ({
    queryKey: ['allWorkflows'],
    queryFn: async () => {
      this.isLoading.set(true);
      try {
        const data = await firstValueFrom(this.workflowService.getAllWorkflows());

        // Custom sorting logic (optional)
        data.sort((a, b) => {
          // Example: "Active" workflows first, then by state alphabetically, then by ID
          if (a.state === WorkflowDTO.StateEnum.Active && b.state !== WorkflowDTO.StateEnum.Active) return -1;
          if (a.state !== WorkflowDTO.StateEnum.Active && b.state === WorkflowDTO.StateEnum.Active) return 1;
          if (a.state !== b.state) return a.state.localeCompare(b.state);
          return a.id - b.id;
        });

        this.workflows.set(data);

        // Initially, no groups assigned; all workflows are ungrouped
        this.workflowGroupsMap.set({});

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

  // Handle group changes from the dropdown
  onChangeGroup(workflow: WorkflowDTO, newGroup: string) {
    if (newGroup === 'Ungrouped') {
      // Remove the workflow from any group
      const {[workflow.id]: _, ...newMap} = this.workflowGroupsMap();
      this.workflowGroupsMap.set(newMap);
    } else {
      // Assign to a new group
      const oldMap = this.workflowGroupsMap();
      const updatedMap = {...oldMap, [workflow.id]: newGroup};
      this.workflowGroupsMap.set(updatedMap);

      // If the group is new, add it to availableGroups and groupOrder
      if (!this.availableGroups().includes(newGroup)) {
        this.availableGroups.update(groups => [...groups, newGroup]);
        this.groupOrder.update(order => [...order, newGroup]);
      }
    }
  }

  // Add a new group using PrimeNG dialog
  addNewGroup() {
    const groupName = this.newGroupName.trim();
    if (!groupName) {
      return;
    }

    if (!this.availableGroups().includes(groupName)) {
      this.availableGroups.update(groups => [...groups, groupName]);
      this.groupOrder.update(order => [...order, groupName]);
    }

    this.resetDialog();
  }

  // Reset dialog state
  resetDialog() {
    this.newGroupName = '';
    this.showAddGroupDialog = false;
  }

  // Drag & Drop logic
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
    // Remove the dragged group
    const [dragged] = newArray.splice(this.dragIndex, 1);
    // Insert at the target position
    newArray.splice(targetIndex, 0, dragged);

    // Update groupOrder based on new array
    const newOrder = newArray.map(group => group.groupName);
    this.groupOrder.set(newOrder);

    this.dragIndex = null;
  }

  private dragWorkflow: { id: number; groupName: string } | null = null;


  dragWorkflowStart(groupIndex: number, workflowIndex: number) {
    const group = this.groupedWorkflowsArray()[groupIndex];
    const workflow = group.workflows[workflowIndex];
    this.dragWorkflow = { id: workflow.id, groupName: group.groupName };
  }

  dropWorkflow(targetGroupIndex: number, targetWorkflowIndex: number) {
    if (!this.dragWorkflow) return;

    const { id: draggedWorkflowId, groupName: draggedGroupName } = this.dragWorkflow;
    const groupedWorkflows = this.groupedWorkflowsArray();

    // Get the target group and workflow IDs
    const targetGroup = groupedWorkflows[targetGroupIndex];
    if (!targetGroup || targetGroup.groupName !== draggedGroupName) {
      console.warn('Dragging between groups is not supported in this implementation.');
      return;
    }

    const workflows = [...targetGroup.workflows];
    const draggedIndex = workflows.findIndex(wf => wf.id === draggedWorkflowId);
    if (draggedIndex === -1) return;

    // Reorder within the group
    const [draggedWorkflow] = workflows.splice(draggedIndex, 1);
    workflows.splice(targetWorkflowIndex, 0, draggedWorkflow);

    // Update workflows order
    const updatedWorkflows = this.workflows().map(wf => {
      if (workflows.some(w => w.id === wf.id)) {
        return workflows.find(w => w.id === wf.id)!; // Preserve the updated order
      }
      return wf;
    });

    this.workflows.set(updatedWorkflows);

    this.dragWorkflow = null; // Clear drag state
  }


}
