import {Component, signal, computed, effect, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ActivatedRoute} from '@angular/router';
import {injectQuery} from '@tanstack/angular-query-experimental';
import {firstValueFrom} from 'rxjs';

import {TableModule} from 'primeng/table';
import {DropdownModule} from 'primeng/dropdown';
import {ButtonModule} from 'primeng/button';
import {PanelModule} from 'primeng/panel';
import {DialogModule} from 'primeng/dialog';
import {InputTextModule} from 'primeng/inputtext';
import {IconsModule} from 'icons.module';
import {DragDropModule} from 'primeng/dragdrop';

import {GitRepoSettingsControllerService} from '@app/core/modules/openapi';
import {WorkflowControllerService} from '@app/core/modules/openapi/api/workflow-controller.service';
import {WorkflowDTO} from '@app/core/modules/openapi/model/workflow-dto';
import {WorkflowGroupDTO} from '@app/core/modules/openapi/model/workflow-group-dto';
import {WorkflowMembershipDTO} from '@app/core/modules/openapi/model/workflow-membership-dto';

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
    IconsModule,
    DragDropModule,
  ],
  templateUrl: './project-settings.component.html',
  styleUrls: ['./project-settings.component.css'],
})
export class ProjectSettingsComponent {
  private route = inject(ActivatedRoute);
  private settingsService = inject(GitRepoSettingsControllerService);
  private workflowService = inject(WorkflowControllerService);

  // Signals for repository ID, workflows, and workflow groups
  repositoryId = signal<number | null>(null);

  workflows = signal<WorkflowDTO[]>([]);
  workflowGroups = signal<WorkflowGroupDTO[]>([]);

  // For UI state
  isLoading = signal(false);
  isError = signal(false);
  errorMessage = signal('');

  // For creating a new group
  showAddGroupDialog = false;
  newGroupName = '';

  // Local mapping from workflowId -> groupName (to support your dropdown logic)
  // This is recalculated from server data in handleDataMerge()
  workflowGroupsMap = signal<Record<number, string>>({});

  // A new computed property for your front-end grouping logic
  readonly localGroupedWorkflowsArray = computed(() => {
    // Full list of groups from the server
    const serverGroups = this.workflowGroups();
    // All workflows from the server
    const allWfs = this.workflows();
    // The local map for workflow -> groupName
    const localMap = this.workflowGroupsMap();

    // Build a record that starts with each server group as an empty array
    const record: Record<string, WorkflowDTO[]> = {};
    serverGroups.forEach(serverGroup => {
      record[serverGroup.name] = []; // ensures empty groups appear in UI
    });

    // Distribute each workflow into the local group from localMap
    allWfs.forEach(wf => {
      const assignedGroupName = localMap[wf.id] ?? 'Ungrouped';

      // Skip if the group name is 'Ungrouped'
      if (assignedGroupName === 'Ungrouped') {
        return;
      }

      if (!record[assignedGroupName]) {
        record[assignedGroupName] = [];
      }
      record[assignedGroupName].push(wf);
    });

    // Convert the final record to an array for your HTML @for loops
    return Object.entries(record).map(([groupName, workflows]) => ({
      groupName,
      workflows
    }));
  });



  constructor() {
    // Monitor changes
    effect(() => {
      console.log('Repository ID changed:', this.repositoryId());
    });
  }

  ngOnInit(): void {
    this.route.parent?.paramMap.subscribe((params) => {
      const repoId = params.get('projectId');
      if (repoId) {
        this.repositoryId.set(+repoId);
        this.fetchWorkflowsQuery.refetch();
        this.fetchGroupsQuery.refetch();
      }
    });
  }

  // Angular Query to fetch workflows by repository ID
  fetchWorkflowsQuery = injectQuery(() => ({
    queryKey: ['workflows', this.repositoryId],
    queryFn: async () => {
      if (!this.repositoryId()) return [];
      this.isLoading.set(true);
      this.isError.set(false);

      try {
        // Fetch all workflows for this repository
        const data = await firstValueFrom(
          this.workflowService.getWorkflowsByRepositoryId(this.repositoryId()!)
        );

        // Sort the workflows
        // Show "Active" workflows first, then by state alphabetically, withing the same state by ID
        data.sort((a, b) => {
          // Example: "Active" workflows first, then by state alphabetically, then by ID
          if (a.state === WorkflowDTO.StateEnum.Active && b.state !== WorkflowDTO.StateEnum.Active) return -1;
          if (a.state !== WorkflowDTO.StateEnum.Active && b.state === WorkflowDTO.StateEnum.Active) return 1;
          if (a.state !== b.state) return a.state.localeCompare(b.state);
          return a.id - b.id;
        });

        this.workflows.set(data);
        this.isLoading.set(false);
        return data;
      } catch (error: any) {
        this.isLoading.set(false);
        this.isError.set(true);
        this.errorMessage.set(error.message || 'Error fetching workflows');
        throw error;
      }
    },
    enabled: () => !!this.repositoryId(),
    refetchOnWindowFocus: false,
  }));

  // Angular Query to fetch workflow groups (with memberships)
  fetchGroupsQuery = injectQuery(() => ({
    queryKey: ['workflowGroups', this.repositoryId],
    queryFn: async () => {
      if (!this.repositoryId()) return [];
      this.isLoading.set(true);
      this.isError.set(false);

      try {
        const groupsData = await firstValueFrom(
          this.settingsService.getGroupsWithWorkflows(this.repositoryId()!)
        );
        this.workflowGroups.set(groupsData);
        this.isLoading.set(false);
        // Recalculate workflow->group mapping
        this.handleDataMerge(groupsData, this.workflows());
        return groupsData;
      } catch (error: any) {
        this.isLoading.set(false);
        this.isError.set(true);
        this.errorMessage.set(error.message || 'Error fetching groups');
        throw error;
      }
    },
    enabled: () => !!this.repositoryId(),
    refetchOnWindowFocus: false,
  }));

  // Merges workflow membership data with the local workflow map
  private handleDataMerge(groups: WorkflowGroupDTO[], workflows: WorkflowDTO[]) {
    // Clear previous map
    const newMap: Record<number, string> = {};

    // Loop each group
    groups.forEach((group) => {
      const memberships = group.memberships ?? [];
      memberships.forEach((mem: WorkflowMembershipDTO) => {
        newMap[mem.workflowId] = group.name; // map workflowId -> groupName
      });
    });

    // Set the signal
    this.workflowGroupsMap.set(newMap);
  }

  // Computed property for grouping logic
  readonly groupedWorkflowsArray = computed(() => {
    // Start with an array from the actual `workflowGroups` signal
    const allGroups = this.workflowGroups();
    const record: { groupName: string; workflows: WorkflowDTO[] }[] = [];

    allGroups.forEach(group => {
      const groupName = group.name;
      const workflowsForGroup: WorkflowDTO[] = [];

      (group.memberships || []).forEach(mem => {
        const wf = this.workflows().find(w => w.id === mem.workflowId);
        if (wf) workflowsForGroup.push(wf);
      });

      record.push({ groupName, workflows: workflowsForGroup });
    });

    // (Optional) If you also want "Ungrouped" logic, handle it here
    return record;
  });

  // Distinguish the actual server groups for the dropdown
  get groupDropdownOptions() {
    // we keep "Ungrouped" as a fallback
    const groups = this.workflowGroups().map(g => ({label: g.name, value: g.name}));
    return [
      {label: 'Ungrouped', value: 'Ungrouped'},
      ...groups,
    ];
  }

  deleteGroup(group: { groupName: string; workflows: WorkflowDTO[] }) {
    this.isLoading.set(true);

    // Find the actual WorkflowGroupDTO by name (or store ID in your record)
    const foundGroup = this.workflowGroups().find(g => g.name === group.groupName);
    if (!foundGroup || !(foundGroup.id > 0)) {
      console.error('No matching group found or invalid ID.');
      this.isLoading.set(false);
      return;
    }

    // Ensure the group is empty
    if (foundGroup.memberships && foundGroup.memberships.length > 0) {
      console.warn('Group is not empty, cannot delete.');
      this.isLoading.set(false);
      return;
    }

    this.settingsService.deleteWorkflowGroup(this.repositoryId()!, foundGroup.id).subscribe({
      next: () => {
        console.log('Group deleted:', foundGroup.name);
        this.fetchGroupsQuery.refetch();
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error('Error deleting group', err);
        this.isLoading.set(false);
      },
    });
  }


  // Handle group assignment via dropdown
  onChangeGroup(workflow: WorkflowDTO, newGroup: string) {
    if (newGroup === 'Ungrouped') {
      const { [workflow.id]: _, ...rest } = this.workflowGroupsMap();
      this.workflowGroupsMap.set(rest); // new object => rerender
    } else {
      const oldMap = this.workflowGroupsMap();
      const updatedMap = { ...oldMap, [workflow.id]: newGroup };
      this.workflowGroupsMap.set(updatedMap); // new object => rerender
    }
  }

  // Create a new group via server
  addNewGroup() {
    if (!this.repositoryId() || !this.newGroupName.trim()) return;

    const newGroup: WorkflowGroupDTO = {
      id: 0,
      name: this.newGroupName.trim(),
      orderIndex: this.workflowGroups().length, // put it at the end
      memberships: [],
    };

    this.settingsService.createWorkflowGroup(this.repositoryId()!, newGroup).subscribe({
      next: (created) => {
        console.log('Created group:', created);
        this.showAddGroupDialog = false;
        this.newGroupName = '';
        this.fetchGroupsQuery.refetch();
      },
      error: (err) => {
        console.error('Error creating group', err);
      },
    });
  }

  resetDialog() {
    this.showAddGroupDialog = false;
    this.newGroupName = '';
  }

  updateGroups() {
    if (!this.repositoryId()) return;
    this.isLoading.set(true);

    const finalGroups = this.workflowGroups().map((group, idx) => {
      // Rebuild the memberships array
      const membershipArr: WorkflowMembershipDTO[] = [];

      for (const [wfIdStr, groupName] of Object.entries(this.workflowGroupsMap())) {
        if (groupName === group.name) {
          const wfId = +wfIdStr;
          membershipArr.push({
            workflowId: wfId,
            orderIndex: membershipArr.length
          });
        }
      }

      return {
        ...group,
        orderIndex: idx,
        memberships: membershipArr,
      };
    });

    // Save to server
    this.settingsService.updateWorkflowGroups(this.repositoryId()!, finalGroups).subscribe({
      next: () => {
        this.isLoading.set(false);
        console.log('Groups updated successfully.');
        this.fetchGroupsQuery.refetch();
      },
      error: (err) => {
        this.isLoading.set(false);
        console.error('Error updating groups', err);
      },
    });
  }


  // Drag & Drop logic for groupedWorkflowsArray (purely local)
  private dragIndex: number | null = null;

  dragStart(fromIndex: number) {
    this.dragIndex = fromIndex;
  }

  drop(targetIndex: number) {
    if (this.dragIndex === null || this.dragIndex === targetIndex) return;

    const current = this.groupedWorkflowsArray();
    const newArray = [...current];
    // Remove the dragged group
    const [dragged] = newArray.splice(this.dragIndex, 1);
    // Insert at target position
    newArray.splice(targetIndex, 0, dragged);

    // The array is sorted in the new order, but we don’t store it on server here
    // If you want server ordering, you need to call updateWorkflowGroups with new indexes

    this.dragIndex = null;
  }

  // Drag & Drop logic for workflows within a group
  private dragWorkflowState: { groupIndex: number; wfIndex: number } | null = null;

  dragWorkflowStart(groupIndex: number, wfIndex: number) {
    this.dragWorkflowState = {groupIndex, wfIndex};
  }

  dropWorkflow(targetGroupIndex: number, targetWfIndex: number) {
    if (!this.dragWorkflowState) return;
    const {groupIndex, wfIndex} = this.dragWorkflowState;

    // If you want to allow dragging between groups, you'd handle that logic here
    if (groupIndex !== targetGroupIndex) {
      console.warn('Dragging workflows between groups not supported in this snippet.');
    } else {
      // reorder within the same group
      const groupedArray = [...this.groupedWorkflowsArray()];
      const group = groupedArray[groupIndex];

      const wfs = [...group.workflows];
      const [dragged] = wfs.splice(wfIndex, 1);
      wfs.splice(targetWfIndex, 0, dragged);

      group.workflows = wfs;
      groupedArray[groupIndex] = group;

    }

    this.dragWorkflowState = null;
  }
}
