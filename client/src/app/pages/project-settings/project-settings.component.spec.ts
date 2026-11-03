import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProjectSettingsComponent } from './project-settings.component';
import { importProvidersFrom, signal } from '@angular/core';
import { TestModule } from '@app/test.module';
import { ConfirmationService, MessageService } from 'primeng/api';
import { DeploymentWorkflowConfigDto, TestTypeDto, WorkflowDto, WorkflowGroupDto } from '@app/core/modules/openapi';

describe('Project settings behavior', () => {
  let component: ProjectSettingsComponent;
  let fixture: ComponentFixture<ProjectSettingsComponent>;
  let confirmation: ConfirmationService;
  let messages: MessageService;
  let workflows: ReturnType<typeof signal<WorkflowDto[]>>;
  let groups: ReturnType<typeof signal<WorkflowGroupDto[]>>;
  let pending: ReturnType<typeof signal<boolean>>;
  let error: ReturnType<typeof signal<boolean>>;
  let deploymentConfig: ReturnType<typeof signal<DeploymentWorkflowConfigDto | undefined>>;
  const workflow = (id: number, fileNameWithExtension: string, state: WorkflowDto['state'] = 'ACTIVE'): WorkflowDto =>
    ({ id, name: fileNameWithExtension, fileNameWithExtension, state, label: 'NONE' }) as WorkflowDto;
  const group = (id: number, name: string, ids: number[] = []): WorkflowGroupDto => ({
    id,
    name,
    orderIndex: id - 1,
    memberships: ids.map((workflowId, orderIndex) => ({ workflowId, orderIndex })),
  });
  const type: TestTypeDto = { id: 5, name: 'Unit tests', artifactName: 'unit-results', workflowId: 3 };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ProjectSettingsComponent], providers: [importProvidersFrom(TestModule)] }).compileComponents();
    fixture = TestBed.createComponent(ProjectSettingsComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('repositoryId', '42');
    confirmation = TestBed.inject(ConfirmationService);
    messages = TestBed.inject(MessageService);
    workflows = signal([]);
    groups = signal([]);
    pending = signal(true);
    error = signal(false);
    deploymentConfig = signal(undefined);
    component.fetchWorkflowsQuery = { ...component.fetchWorkflowsQuery, data: workflows, isPending: pending, isError: error } as unknown as typeof component.fetchWorkflowsQuery;
    component.groupsQuery = { ...component.groupsQuery, data: groups, isPending: signal(false), isError: signal(false) } as unknown as typeof component.groupsQuery;
    component.gitRepoSettingsQuery = {
      ...component.gitRepoSettingsQuery,
      data: signal({ packageName: 'de.tum.helios' }),
      isPending: signal(false),
      isError: signal(false),
    } as unknown as typeof component.gitRepoSettingsQuery;
    component.deploymentWorkflowConfigQuery = { ...component.deploymentWorkflowConfigQuery, data: deploymentConfig } as unknown as typeof component.deploymentWorkflowConfigQuery;
    fixture.detectChanges();
    await fixture.whenStable();
  });
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('renders a loading message while settings are pending', () => {
    expect(fixture.nativeElement.textContent).toContain('Loading settings...');
    expect(fixture.nativeElement.querySelector('#packageName')).toBeNull();
    expect(component.repositoryId()).toBe(42);
  });
  it('combines query errors and hides settings controls on failure', () => {
    pending.set(false);
    error.set(true);
    fixture.detectChanges();
    expect(component.isError()).toBe(true);
    expect(fixture.nativeElement.querySelector('#packageName')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Loading settings...');
  });
  it('initializes the package name from repository settings and submits the current value', () => {
    const mutate = vi.spyOn(component.updateGitRepoSettingsMutation, 'mutate').mockImplementation(() => {});
    expect(component.packageName()).toBe('de.tum.helios');
    component.packageName.set('org.example.changed');
    component.updatePackageName();
    expect(mutate).toHaveBeenCalledExactlyOnceWith({ path: { repositoryId: 42 }, body: { packageName: 'org.example.changed' } });
  });
  it('renders loaded settings and saves an edited package name through its Update button', async () => {
    const mutate = vi.spyOn(component.updateGitRepoSettingsMutation, 'mutate').mockImplementation(() => {});
    pending.set(false);
    fixture.detectChanges();
    await fixture.whenStable();
    const input = fixture.nativeElement.querySelector('#packageName') as HTMLInputElement;
    expect(input.value).toBe('de.tum.helios');
    expect(fixture.nativeElement.textContent).toContain('No Groups Available');
    expect(fixture.nativeElement.querySelector('input[type="password"]').value).toBe('************');
    input.value = 'org.example.client';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    const button = Array.from(fixture.nativeElement.querySelectorAll('button')).find(element => (element as HTMLElement).textContent?.trim() === 'Update') as HTMLButtonElement;
    button.click();
    expect(mutate).toHaveBeenCalledExactlyOnceWith({ path: { repositoryId: 42 }, body: { packageName: 'org.example.client' } });
    component.secret.set('generated-secret');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('never');
    expect(fixture.nativeElement.querySelector('input[readonly]').type).toBe('text');
  });

  it('lists active workflows first, sorts filenames, and breaks ties by ID', () => {
    workflows.set([workflow(5, 'a.yml', 'DISABLED_MANUALLY'), workflow(4, 'b.yml'), workflow(3, 'a.yml'), workflow(2, 'a.yml'), workflow(1, 'z.yml', 'DISABLED_MANUALLY')]);
    expect(component.workflows().map(wf => wf.id)).toEqual([2, 3, 4, 5, 1]);
  });
  it('keeps empty groups visible and ignores unknown workflow memberships', () => {
    workflows.set([workflow(1, 'build.yml'), workflow(2, 'test.yml')]);
    groups.set([group(1, 'CI', [1, 99]), group(2, 'Empty')]);
    fixture.detectChanges();
    expect(component.groupedWorkflowsArray().map(entry => ({ name: entry.groupName, ids: entry.workflows.map(wf => wf.id) }))).toEqual([
      { name: 'CI', ids: [1] },
      { name: 'Empty', ids: [] },
    ]);
    expect(component.localGroupedWorkflowsArray().map(entry => ({ name: entry.groupName, ids: entry.workflows.map(wf => wf.id) }))).toEqual([
      { name: 'CI', ids: [1] },
      { name: 'Empty', ids: [] },
    ]);
    expect(component.groupDropdownOptions).toEqual([
      { label: 'Ungrouped', value: 'Ungrouped' },
      { label: 'CI', value: 'CI' },
      { label: 'Empty', value: 'Empty' },
    ]);
  });
  it('moves a workflow between groups and persists rebuilt memberships', () => {
    groups.set([group(1, 'CI', [1]), group(2, 'Deploy', [2])]);
    fixture.detectChanges();
    const mutate = vi.spyOn(component.updateWorkflowGroupMutation, 'mutate').mockImplementation(() => {});
    component.onChangeGroup(workflow(1, 'test.yml'), 'Deploy');
    expect(component.workflowGroupsMap()).toEqual({ 1: 'Deploy', 2: 'Deploy' });
    expect(mutate).toHaveBeenCalledExactlyOnceWith({
      path: { repositoryId: 42 },
      body: [
        group(1, 'CI'),
        {
          ...group(2, 'Deploy'),
          memberships: [
            { workflowId: 1, orderIndex: 0 },
            { workflowId: 2, orderIndex: 1 },
          ],
        },
      ],
    });
  });
  it('removes a workflow assignment when Ungrouped is selected', () => {
    groups.set([group(1, 'CI', [1, 2])]);
    fixture.detectChanges();
    const mutate = vi.spyOn(component.updateWorkflowGroupMutation, 'mutate').mockImplementation(() => {});
    component.onChangeGroup(workflow(1, 'test.yml'), 'Ungrouped');
    expect(component.workflowGroupsMap()).toEqual({ 2: 'CI' });
    expect(mutate).toHaveBeenCalledWith({ path: { repositoryId: 42 }, body: [group(1, 'CI', [2])] });
  });
  it('reorders groups by drag and drop and persists their new order', () => {
    groups.set([group(1, 'CI'), group(2, 'Deploy')]);
    fixture.detectChanges();
    const mutate = vi.spyOn(component.updateWorkflowGroupMutation, 'mutate').mockImplementation(() => {});
    component.drop(1);
    expect(mutate).not.toHaveBeenCalled();
    component.dragStart(0);
    component.drop(0);
    expect(mutate).not.toHaveBeenCalled();
    component.drop(1);
    expect(mutate).toHaveBeenCalledExactlyOnceWith({
      path: { repositoryId: 42 },
      body: [
        { ...group(2, 'Deploy'), orderIndex: 0 },
        { ...group(1, 'CI'), orderIndex: 1 },
      ],
    });
    component.drop(0);
    expect(mutate).toHaveBeenCalledTimes(1);
  });
  it('trims new group names and rejects empty names', () => {
    const mutate = vi.spyOn(component.createWorkflowGroupMutation, 'mutate').mockImplementation(() => {});
    component.newGroupName = '   ';
    component.addNewGroup();
    expect(mutate).not.toHaveBeenCalled();
    component.newGroupName = '  Build  ';
    component.addNewGroup();
    expect(mutate).toHaveBeenCalledExactlyOnceWith({ path: { repositoryId: 42 }, body: { id: 0, name: 'Build', orderIndex: 0, memberships: [] } });
  });
  it('detects duplicate group names ignoring whitespace and casing and resets the dialog', () => {
    groups.set([group(1, 'Build')]);
    fixture.detectChanges();
    component.newGroupName = '  bUiLd ';
    component.showAddGroupDialog = true;
    expect(component.groupNameExists()).toBe(true);
    component.resetDialog();
    expect(component.newGroupName).toBe('');
    expect(component.showAddGroupDialog).toBe(false);
  });
  it('deletes only existing empty groups', () => {
    groups.set([group(1, 'Empty'), group(2, 'Busy', [3])]);
    fixture.detectChanges();
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    vi.spyOn(console, 'error').mockImplementation(() => {});
    const mutate = vi.spyOn(component.deleteWorkflowGroupMutation, 'mutate').mockImplementation(() => {});
    component.deleteGroup({ groupName: 'Missing', workflows: [] });
    component.deleteGroup({ groupName: 'Busy', workflows: [] });
    expect(mutate).not.toHaveBeenCalled();
    component.deleteGroup({ groupName: 'Empty', workflows: [] });
    expect(mutate).toHaveBeenCalledExactlyOnceWith({ path: { repositoryId: 42, groupId: 1 } });
  });
  it('restores the original workflow label when a change is rejected and saves after acceptance', () => {
    const wf = workflow(3, 'test.yml');
    const confirm = vi.spyOn(confirmation, 'confirm');
    const mutate = vi.spyOn(component.workflowLabelMutation, 'mutate').mockImplementation(() => {});
    component.storePreviousLabel(wf);
    wf.label = 'TEST';
    component.onChangeLabel(wf);
    expect(mutate).not.toHaveBeenCalled();
    confirm.mock.calls[0][0].reject!();
    expect(wf.label).toBe('NONE');
    wf.label = 'TEST';
    component.onChangeLabel(wf);
    confirm.mock.calls[1][0].accept!();
    expect(mutate).toHaveBeenCalledExactlyOnceWith({ path: { workflowId: 3 }, body: 'TEST' });
  });
  it('opens deployment configuration with defaults and applies returned configuration', () => {
    component.openDeploymentConfig(workflow(3, 'deploy.yml'));
    expect(component.deploymentConfigDialogVisible).toBe(true);
    expect(component.deploymentConfigWorkflowId()).toBe(3);
    expect(component.deploymentConfigForm()).toEqual({ deployJobName: '' });
    deploymentConfig.set({ deployJobName: 'deploy-production' });
    fixture.detectChanges();
    expect(component.deploymentConfigForm()).toEqual({ deployJobName: 'deploy-production' });
  });
  it('saves deployment configuration only after a workflow is selected', () => {
    const mutate = vi.spyOn(component.upsertDeploymentWorkflowConfigMutation, 'mutate').mockImplementation(() => {});
    component.saveDeploymentConfig();
    expect(mutate).not.toHaveBeenCalled();
    component.openDeploymentConfig(workflow(3, 'deploy.yml'));
    component.setDeploymentConfigField('deployJobName', 'deploy');
    component.saveDeploymentConfig();
    expect(mutate).toHaveBeenCalledExactlyOnceWith({ path: { repositoryId: 42, workflowId: 3 }, body: { workflowId: 3, deployJobName: 'deploy' } });
  });
  it.each(['name', 'artifactName', 'workflowId'] as const)('rejects test types missing %s', field => {
    const add = vi.spyOn(messages, 'add');
    const mutate = vi.spyOn(component.createTestTypeMutation, 'mutate').mockImplementation(() => {});
    component.testTypeForm = { ...type, [field]: undefined };
    component.saveTestType();
    expect(mutate).not.toHaveBeenCalled();
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error', detail: 'Please fill in all required fields' }));
  });
  it('creates new test types and updates existing types without mutating the original', () => {
    const create = vi.spyOn(component.createTestTypeMutation, 'mutate').mockImplementation(() => {});
    const update = vi.spyOn(component.updateTestTypeMutation, 'mutate').mockImplementation(() => {});
    component.testTypeForm = { ...type };
    component.saveTestType();
    expect(create).toHaveBeenCalledExactlyOnceWith({ body: type });
    component.editTestType(type);
    component.testTypeForm.name = 'Integration tests';
    expect(type.name).toBe('Unit tests');
    component.saveTestType();
    expect(update).toHaveBeenCalledExactlyOnceWith({ path: { testTypeId: 5 }, body: { ...type, name: 'Integration tests' } });
    component.resetTestTypeDialog();
    expect(component.showAddTestTypeDialog).toBe(false);
    expect(component.editingTestType).toBeNull();
    expect(component.testTypeForm).toEqual({ name: '', artifactName: '', workflowId: undefined });
  });
  it('requires confirmation to delete a test type', () => {
    const confirm = vi.spyOn(confirmation, 'confirm');
    const mutate = vi.spyOn(component.deleteTestTypeMutation, 'mutate').mockImplementation(() => {});
    component.confirmDeleteTestType(type);
    expect(mutate).not.toHaveBeenCalled();
    expect(confirm).toHaveBeenCalledWith(expect.objectContaining({ message: expect.stringContaining('Unit tests') }));
    confirm.mock.calls[0][0].accept!();
    expect(mutate).toHaveBeenCalledExactlyOnceWith({ path: { testTypeId: 5 } });
  });
  it('masks absent secrets and rotates only after confirmation', () => {
    const mutate = vi.spyOn(component.rotateSecret, 'mutate').mockImplementation(() => {});
    expect(component.secretDisplay()).toBe('************');
    component.secret.set('existing-secret');
    component.generateSecret();
    expect(component.secretReGenerateDialogVisible()).toBe(true);
    expect(mutate).not.toHaveBeenCalled();
    component.onGenerateSecretConfirmed(false);
    expect(component.secretDisplay()).toBe('existing-secret');
    expect(mutate).not.toHaveBeenCalled();
    component.onGenerateSecretConfirmed(true);
    expect(component.secret()).toBeNull();
    expect(mutate).toHaveBeenCalledExactlyOnceWith({ path: { repositoryId: 42 } });
  });
  it('copies a generated secret and reports success', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', { clipboard: { writeText } });
    const add = vi.spyOn(messages, 'add');
    component.copySecret();
    expect(writeText).not.toHaveBeenCalled();
    component.secret.set('new-secret');
    component.copySecret();
    await Promise.resolve();
    expect(writeText).toHaveBeenCalledExactlyOnceWith('new-secret');
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'info', summary: 'Copied' }));
    vi.unstubAllGlobals();
  });
  it('reports clipboard access failures', async () => {
    vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn().mockRejectedValue(new Error('Denied')) } });
    vi.spyOn(console, 'error').mockImplementation(() => {});
    const add = vi.spyOn(messages, 'add');
    component.secret.set('new-secret');
    component.copySecret();
    await Promise.resolve();
    await Promise.resolve();
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'warn', detail: 'Could not access clipboard' }));
    vi.unstubAllGlobals();
  });
});
