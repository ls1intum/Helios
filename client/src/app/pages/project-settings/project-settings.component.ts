import { Component, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { ConfirmationService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { DividerModule } from 'primeng/divider';
import { DragDropModule } from 'primeng/dragdrop';
import { InputTextModule } from 'primeng/inputtext';
import { PanelModule } from 'primeng/panel';
import { TableModule } from 'primeng/table';
import { LockingThresholdsComponent } from '@app/components/locking-thresholds/locking-thresholds.component';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';
import { RotateSecretResponses, TestTypeDto, WorkflowDto, WorkflowGroupDto, WorkflowMembershipDto } from '@app/core/modules/openapi';
import {
  createWorkflowGroupMutation,
  deleteWorkflowGroupMutation,
  getGitRepoSettingsOptions,
  getGroupsWithWorkflowsOptions,
  getGroupsWithWorkflowsQueryKey,
  getWorkflowsByRepositoryIdOptions,
  getWorkflowsByRepositoryIdQueryKey,
  updateWorkflowLabelMutation,
  updateWorkflowGroupsMutation,
  syncWorkflowsByRepositoryIdMutation,
  updateTestTypeMutation,
  createTestTypeMutation,
  deleteTestTypeMutation,
  getAllTestTypesQueryKey,
  getAllTestTypesOptions,
  updateGitRepoSettingsMutation,
  rotateSecretMutation,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { WorkflowDtoSchema } from '@app/core/modules/openapi/schemas.gen';
import { MessageService } from 'primeng/api';
import { SelectModule } from 'primeng/select';
import { TooltipModule } from 'primeng/tooltip';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ButtonGroupModule } from 'primeng/buttongroup';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconCircleCheck, IconPencil, IconPlus, IconTrash } from 'angular-tabler-icons/icons';

@Component({
  selector: 'app-project-settings',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    ButtonGroupModule,
    InputTextModule,
    PageHeadingComponent,
    LockingThresholdsComponent,
    PanelModule,
    DialogModule,
    TooltipModule,
    SelectModule,
    InputTextModule,
    TablerIconComponent,
    DragDropModule,
    DividerModule,
    TagModule,
    MessageModule,
  ],
  providers: [
    provideTablerIcons({
      IconCircleCheck,
      IconPlus,
      IconPencil,
      IconTrash,
    }),
  ],
  templateUrl: './project-settings.component.html',
})
export class ProjectSettingsComponent {
  private messageService = inject(MessageService);
  private confirmationService = inject(ConfirmationService);
  queryClient = inject(QueryClient);

  // Signals for repository ID, workflows, and workflow groups
  repositoryId = input.required({ transform: numberAttribute });

  workflowGroups = signal<WorkflowGroupDto[]>([]);
  isPending = computed(() => this.fetchWorkflowsQuery.isPending() || this.groupsQuery.isPending() || this.gitRepoSettingsQuery.isPending());
  isError = computed(() => this.fetchWorkflowsQuery.isError() || this.groupsQuery.isError() || this.gitRepoSettingsQuery.isError());

  // Package name signals
  packageName = signal<string>('');

  // --- Shared‑secret state
  secret = signal<string | null>(null);
  secretDisplay = computed(() => (this.secret() ? this.secret() : '************'));

  // For creating a new group
  showAddGroupDialog = false;
  newGroupName = '';
  // Store the previous label temporarily for the confirmation dialog
  private previousLabel: WorkflowDto['label'] = 'NONE';

  // Drag & Drop logic for groupedWorkflowsArray
  private dragIndex: number | null = null;

  // Local mapping from workflowId -> groupName (to support dropdown logic)
  // This is recalculated from server data in handleDataMerge()
  // This is recalculated from dropdown selection/assignment  in onChangeGroup()
  // This is recalculated from drag&drop logic in updateGroups()
  workflowGroupsMap = signal<Record<number, string>>({});

  constructor() {
    effect(() => {
      const workflowGroups = this.groupsQuery.data() || [];
      this.workflowGroups.set(workflowGroups);
      // Clear previous map
      const newMap: Record<number, string> = {};

      // Loop each group
      workflowGroups.forEach(group => {
        const memberships = group.memberships ?? [];
        memberships.forEach((mem: WorkflowMembershipDto) => {
          newMap[mem.workflowId] = group.name; // map workflowId -> groupName
        });
      });

      // Set the signal
      this.workflowGroupsMap.set(newMap);
    });

    effect(
      () => {
        const gitRepoSettings = this.gitRepoSettingsQuery.data();
        if (gitRepoSettings) {
          this.packageName.set(gitRepoSettings.packageName || '');
        }
      },
      { allowSignalWrites: true }
    );
  }

  // Computed property to show 'Grouped Workflows' in the UI
  readonly localGroupedWorkflowsArray = computed(() => {
    // Full list of groups from the server
    const serverGroups = this.workflowGroups();
    // All workflows from the server
    const allWfs = this.workflows();
    // The local map for workflow -> groupName
    const localMap = this.workflowGroupsMap();

    const record: Record<string, WorkflowDto[]> = {};
    serverGroups.forEach(serverGroup => {
      // ensures empty groups appear in UI
      record[serverGroup.name] = [];
    });

    // Distribute each workflow
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

    // Convert record to an array for @for loops
    return Object.entries(record).map(([groupName, workflows]) => ({
      groupName,
      workflows,
    }));
  });

  // Computed property for grouping logic
  readonly groupedWorkflowsArray = computed(() => {
    // Start with an array from the actual `workflowGroups` signal
    const allGroups = this.workflowGroups();
    const record: { groupName: string; workflows: WorkflowDto[] }[] = [];

    allGroups.forEach(group => {
      const groupName = group.name;
      const workflowsForGroup: WorkflowDto[] = [];

      (group.memberships || []).forEach(mem => {
        const wf = this.workflows().find(w => w.id === mem.workflowId);
        if (wf) workflowsForGroup.push(wf);
      });

      record.push({ groupName, workflows: workflowsForGroup });
    });

    // (Optional) If you also want "Ungrouped" logic, handle it here
    return record;
  });

  // Git Repo Settings query for package name
  gitRepoSettingsQuery = injectQuery(() => ({
    ...getGitRepoSettingsOptions({ path: { repositoryId: this.repositoryId() } }),
    enabled: () => !!this.repositoryId(),
  }));

  updatePackageName() {
    this.updateGitRepoSettingsMutation.mutate({
      path: { repositoryId: this.repositoryId() },
      body: { packageName: this.packageName() },
    });
  }

  updateGitRepoSettingsMutation = injectMutation(() => ({
    ...updateGitRepoSettingsMutation(),
    onSuccess: () => {
      this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Package name updated successfully' });
      this.queryClient.invalidateQueries({ queryKey: ['getGitRepoSettings', { path: { repositoryId: this.repositoryId() } }] });
    },
  }));

  fetchWorkflowsQuery = injectQuery(() => ({
    ...getWorkflowsByRepositoryIdOptions({ path: { repositoryId: this.repositoryId() } }),
    enabled: () => !!this.repositoryId(),
  }));

  workflows = computed(() => {
    // Sort the workflows
    // Show "Active" workflows first, then everything else, then alphabetically by fileNameWithExtension, then by ID
    const workflows = this.fetchWorkflowsQuery.data() || [];
    workflows.sort((a, b) => {
      if (a.state === 'ACTIVE' && b.state !== 'ACTIVE') return -1;
      if (a.state !== 'ACTIVE' && b.state === 'ACTIVE') return 1;
      if (a.fileNameWithExtension !== b.fileNameWithExtension) {
        return (a.fileNameWithExtension ?? '').localeCompare(b.fileNameWithExtension ?? '');
      }
      return a.id - b.id;
    });
    return workflows;
  });

  groupsQuery = injectQuery(() => ({
    ...getGroupsWithWorkflowsOptions({ path: { repositoryId: this.repositoryId() } }),
    enabled: () => !!this.repositoryId(),
  }));

  workflowLabelMutation = injectMutation(() => ({
    ...updateWorkflowLabelMutation(),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: getWorkflowsByRepositoryIdQueryKey({ path: { repositoryId: this.repositoryId() } }) });
      this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Workflow Label updated successfully' });
    },
  }));
  deleteWorkflowGroupMutation = injectMutation(() => ({
    ...deleteWorkflowGroupMutation(),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: getGroupsWithWorkflowsQueryKey({ path: { repositoryId: this.repositoryId() } }) });
      this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Workflow group deleted successfully' });
    },
  }));
  createWorkflowGroupMutation = injectMutation(() => ({
    ...createWorkflowGroupMutation(),
    onSuccess: () => {
      this.showAddGroupDialog = false;
      this.newGroupName = '';
      this.queryClient.invalidateQueries({ queryKey: getGroupsWithWorkflowsQueryKey({ path: { repositoryId: this.repositoryId() } }) });
      this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Workflow group created successfully' });
    },
  }));
  updateWorkflowGroupMutation = injectMutation(() => ({
    ...updateWorkflowGroupsMutation(),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: getGroupsWithWorkflowsQueryKey({ path: { repositoryId: this.repositoryId() } }) });
      this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Workflow groups updated successfully' });
    },
  }));
  syncWorkflowsMutation = injectMutation(() => ({
    ...syncWorkflowsByRepositoryIdMutation(),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: getWorkflowsByRepositoryIdQueryKey({ path: { repositoryId: this.repositoryId() } }) });
      this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Workflows synced successfully' });
    },
  }));

  workflowLabelOptions = Object.values(WorkflowDtoSchema.properties.label.enum);

  storePreviousLabel(workflow: WorkflowDto) {
    // Store the current label before change
    this.previousLabel = workflow.label;
  }

  onChangeLabel(workflow: WorkflowDto) {
    const label = workflow.label;
    this.confirmationService.confirm({
      header: 'Change Label',
      message: `
        <div class="max-w-xl w-full">
          <p class="text-base font-medium mb-4">
            Are you sure you want to change the workflow label to <strong>${label}</strong>?
          </p>
          <div class="flex items-start gap-2 p-3 bg-orange-50 dark:bg-orange-300 border border-orange-500 text-orange-500 rounded-md">
            <div>
              <p class="font-semibold">Note:</p>
              <ul class="list-disc list-inside text-sm">
                <li><strong>TEST</strong>: This label sets the workflow to be searched for test artifacts.</li>
                <li><strong>NONE</strong>: No label is set for this workflow.</li>
              </ul>
            </div>
          </div>
        </div>
      `,
      accept: () => {
        this.workflowLabelMutation.mutate({ path: { workflowId: workflow.id }, body: label });
      },
      reject: () => {
        // Restore the previous label if the user cancels
        workflow.label = this.previousLabel;
      },
    });
  }

  // Distinguish the actual server groups for the dropdown
  get groupDropdownOptions() {
    const groups = this.workflowGroups().map(g => ({ label: g.name, value: g.name }));
    // Add the "Ungrouped" option
    return [{ label: 'Ungrouped', value: 'Ungrouped' }, ...groups];
  }
  deleteGroup(group: { groupName: string; workflows: WorkflowDto[] }) {
    // Find the actual WorkflowGroupDTO by name (or store ID in your record)
    const foundGroup = this.workflowGroups().find(g => g.name === group.groupName);
    if (!foundGroup || !(foundGroup.id > 0)) {
      console.error('No matching group found or invalid ID.');
      return;
    }

    // Ensure the group is empty
    if (foundGroup.memberships && foundGroup.memberships.length > 0) {
      console.warn('Group is not empty, cannot delete.');
      return;
    }

    this.deleteWorkflowGroupMutation.mutate({ path: { repositoryId: this.repositoryId(), groupId: foundGroup.id } });
  }

  // Handle group assignment via dropdown
  onChangeGroup(workflow: WorkflowDto, newGroup: string) {
    if (newGroup === 'Ungrouped') {
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      const { [workflow.id]: _, ...rest } = this.workflowGroupsMap();
      // new object => rerender
      this.workflowGroupsMap.set(rest);
    } else {
      const oldMap = this.workflowGroupsMap();
      const updatedMap = { ...oldMap, [workflow.id]: newGroup };
      // new object => rerender
      this.workflowGroupsMap.set(updatedMap);
    }
    this.updateGroups();
  }

  // Create a new empty group
  addNewGroup() {
    if (!this.repositoryId() || !this.newGroupName.trim()) return;

    const newGroup: WorkflowGroupDto = {
      id: 0, // Doesn't matter, will be calculated on the server
      name: this.newGroupName.trim(),
      orderIndex: this.workflowGroups().length, // Doesn't matter, will be calculated on the server
      memberships: [], // Empty group
    };

    this.createWorkflowGroupMutation.mutate({ path: { repositoryId: this.repositoryId() }, body: newGroup });
  }

  groupNameExists = () => {
    const newGroupNameTrimmed = this.newGroupName.trim();
    return this.workflowGroups().some(group => group.name.toLowerCase() === newGroupNameTrimmed.toLowerCase());
  };

  // Reset the dialog state
  resetDialog() {
    this.showAddGroupDialog = false;
    this.newGroupName = '';
  }

  syncWorkflows() {
    const repositoryId = this.repositoryId();
    if (!repositoryId) return;
    this.syncWorkflowsMutation.mutate({ path: { repositoryId } });
  }

  // Update the groups on the server
  updateGroups() {
    if (!this.repositoryId()) return;

    const finalGroups = this.workflowGroups().map((group, idx) => {
      // Rebuild the memberships array
      const membershipArr: WorkflowMembershipDto[] = [];

      for (const [wfIdStr, groupName] of Object.entries(this.workflowGroupsMap())) {
        if (groupName === group.name) {
          const wfId = +wfIdStr;
          membershipArr.push({
            workflowId: wfId,
            orderIndex: membershipArr.length,
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
    this.updateWorkflowGroupMutation.mutate({ path: { repositoryId: this.repositoryId() }, body: finalGroups });
  }

  dragStart(fromIndex: number) {
    this.dragIndex = fromIndex;
  }

  drop(targetIndex: number) {
    if (this.dragIndex === null || this.dragIndex === targetIndex) return;

    // Get the current workflowGroups signal as a copy
    const workflowGroupsArray = [...this.workflowGroups()];

    // Remove the dragged group and reinsert at the target position
    const [draggedGroup] = workflowGroupsArray.splice(this.dragIndex, 1);
    workflowGroupsArray.splice(targetIndex, 0, draggedGroup);

    // Update orderIndex for each group
    const updatedGroups = workflowGroupsArray.map((group, index) => ({
      ...group,
      orderIndex: index, // Set the new orderIndex
    }));

    // Update the workflowGroups signal immutably to trigger reactivity
    this.workflowGroups.set([...updatedGroups]);

    // Reset drag state
    this.dragIndex = null;
    this.updateGroups();
  }

  showAddTestTypeDialog = false;
  editingTestType: TestTypeDto | null = null;
  testTypeForm: Partial<TestTypeDto> = {
    name: '',
    artifactName: '',
    workflowId: undefined,
  };

  testTypesQuery = injectQuery(() => ({
    ...getAllTestTypesOptions(),
    enabled: () => !!this.repositoryId(),
  }));

  testTypes = computed(() => this.testTypesQuery.data() || []);

  createTestTypeMutation = injectMutation(() => ({
    ...createTestTypeMutation(),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: getAllTestTypesQueryKey() });
      this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Test type created successfully' });
      this.resetTestTypeDialog();
    },
  }));

  updateTestTypeMutation = injectMutation(() => ({
    ...updateTestTypeMutation(),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: getAllTestTypesQueryKey() });
      this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Test type updated successfully' });
      this.resetTestTypeDialog();
    },
  }));

  deleteTestTypeMutation = injectMutation(() => ({
    ...deleteTestTypeMutation(),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: getAllTestTypesQueryKey() });
      this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Test type deleted successfully' });
    },
  }));

  editTestType(testType: TestTypeDto) {
    this.editingTestType = testType;
    this.testTypeForm = { ...testType };
    this.showAddTestTypeDialog = true;
  }

  resetTestTypeDialog() {
    this.showAddTestTypeDialog = false;
    this.editingTestType = null;
    this.testTypeForm = {
      name: '',
      artifactName: '',
      workflowId: undefined,
    };
  }

  saveTestType() {
    if (!this.testTypeForm.name || !this.testTypeForm.artifactName || !this.testTypeForm.workflowId) {
      this.messageService.add({
        severity: 'error',
        summary: 'Validation Error',
        detail: 'Please fill in all required fields',
      });
      return;
    }

    if (this.editingTestType) {
      this.updateTestTypeMutation.mutate({
        path: { testTypeId: this.editingTestType.id! },
        body: this.testTypeForm as TestTypeDto,
      });
    } else {
      this.createTestTypeMutation.mutate({
        body: this.testTypeForm as TestTypeDto,
      });
    }
  }

  updateTestType(testType: TestTypeDto) {
    this.updateTestTypeMutation.mutate({
      path: { testTypeId: testType.id! },
      body: testType,
    });
  }

  confirmDeleteTestType(testType: TestTypeDto) {
    this.confirmationService.confirm({
      header: 'Delete Test Type',
      message: `Are you sure you want to delete the test type "${testType.name}"?`,
      accept: () => {
        this.deleteTestTypeMutation.mutate({
          path: { testTypeId: testType.id! },
        });
      },
    });
  }

  // --- mutation
  rotateSecret = injectMutation(() => ({
    ...rotateSecretMutation({ path: { repositoryId: this.repositoryId() } }),
    onSuccess: (data: RotateSecretResponses[200]) => {
      this.secret.set(data);
      this.messageService.add({
        severity: 'success',
        summary: 'Secret regenerated',
        detail: 'Copy the token now.',
      });
    },
  }));

  regenerateSecret() {
    this.secret.set(null);
    this.rotateSecret.mutate({ path: { repositoryId: this.repositoryId() } });
  }

  copySecret() {
    if (!this.secret()) return;
    navigator.clipboard
      .writeText(this.secret()!)
      .then(() => {
        this.messageService.add({ severity: 'info', summary: 'Copied', detail: 'Secret copied to clipboard' });
      })
      .catch(err => {
        this.messageService.add({ severity: 'warn', summary: 'Copy failed', detail: 'Could not access clipboard' });
        console.error(err);
      });
  }
}
