import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router } from '@angular/router';
import { EnvironmentDto, WorkflowDto } from '@app/core/modules/openapi';
import { client } from '@app/core/modules/openapi/client.gen';
import {
  getAllEnabledEnvironmentsQueryKey,
  getAllEnvironmentsQueryKey,
  getEnvironmentByIdQueryKey,
  getEnvironmentsByUserLockingQueryKey,
  getWorkflowsByRepositoryIdQueryKey,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { MessageService } from 'primeng/api';
import { EnvironmentEditFormComponent } from './environment-edit-form.component';

describe('EnvironmentEditFormComponent', () => {
  let fixture: ComponentFixture<EnvironmentEditFormComponent>;
  let queryClient: QueryClient;
  const updateEnvironment = vi.fn();
  const originalConfig = client.getConfig();
  const navigate = vi.fn();
  const addMessage = vi.fn();
  const parentRoute = {};
  const workflow = (id: number, name: string, overrides: Partial<WorkflowDto> = {}): WorkflowDto => ({
    id,
    name,
    path: `.github/workflows/${name}`,
    fileNameWithExtension: name,
    state: 'ACTIVE',
    label: 'NONE',
    ...overrides,
  });
  const deploymentWorkflow = workflow(4, 'deploy.yml');
  const environment: EnvironmentDto = {
    id: 7,
    name: 'staging',
    displayName: 'Staging server',
    type: 'STAGING',
    enabled: false,
    locked: false,
    serverUrl: 'https://staging.example.org',
    description: 'Preview changes',
    installedApps: ['Helios'],
    requiredPreDeploymentWorkflows: [],
    deploymentWorkflowBranch: 'main',
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    updateEnvironment.mockImplementation(async () => new Response(JSON.stringify(environment), { headers: { 'Content-Type': 'application/json' } }));
    client.setConfig({
      baseUrl: 'http://localhost',
      fetch: async request => {
        if ((request as Request).method === 'PUT') return updateEnvironment(request);
        return new Response(JSON.stringify(environment), { headers: { 'Content-Type': 'application/json' } });
      },
    });
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity }, mutations: { retry: false } } });
    queryClient.setQueryData(getEnvironmentByIdQueryKey({ path: { id: 7 } }), environment);
    queryClient.setQueryData(getWorkflowsByRepositoryIdQueryKey({ path: { repositoryId: 3 } }), [deploymentWorkflow]);
    await TestBed.configureTestingModule({
      imports: [EnvironmentEditFormComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideNoopAnimations(),
        provideQueryClient(queryClient),
        { provide: Router, useValue: { navigate } },
        { provide: ActivatedRoute, useValue: { parent: parentRoute } },
        { provide: MessageService, useValue: { add: addMessage } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(EnvironmentEditFormComponent);
    fixture.componentRef.setInput('repositoryId', 3);
    fixture.componentRef.setInput('environmentId', 7);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => {
    queryClient.clear();
    client.setConfig({ baseUrl: originalConfig.baseUrl, fetch: originalConfig.fetch });
  });

  const host = () => fixture.nativeElement as HTMLElement;
  const saveButton = () => Array.from(host().querySelectorAll('button')).find(button => button.textContent?.includes('Update'))!;
  const submit = () =>
    host()
      .querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

  it('loads fetched configuration into the rendered fields', () => {
    expect((host().querySelector('#display-name') as HTMLInputElement).value).toBe('Staging server');
    expect((host().querySelector('#server-url') as HTMLInputElement).value).toBe('https://staging.example.org');
    expect((host().querySelector('#description') as HTMLInputElement).value).toBe('Preview changes');
    expect((host().querySelector('#deployment-workflow-branch') as HTMLInputElement).value).toBe('main');
    expect(saveButton().disabled).toBe(false);
  });

  it('prevents enabling an environment without a workflow and explains how to fix it', async () => {
    (host().querySelector('#enabled input') as HTMLInputElement).click();
    await fixture.whenStable();
    expect(saveButton().disabled).toBe(true);
    expect(host().textContent).toContain('Enabled environments must have a deployment workflow specified.');
    submit();
    expect(updateEnvironment).not.toHaveBeenCalled();
  });

  it('allows enabling once a deployment workflow is selected', async () => {
    (host().querySelector('#enabled input') as HTMLInputElement).click();
    await fixture.whenStable();
    (host().querySelector('#deployment-workflow [role="combobox"]') as HTMLElement).click();
    await fixture.whenStable();
    const option = Array.from(document.querySelectorAll('.p-select-option')).find(item => item.textContent?.includes('deploy.yml')) as HTMLElement;
    expect(option).toBeTruthy();
    option.click();
    await fixture.whenStable();
    expect(saveButton().disabled).toBe(false);
    expect(host().querySelector('p-message')).toBeNull();
    expect(fixture.componentInstance.environmentForm.value.deploymentWorkflow).toEqual(deploymentWorkflow);
  });

  it('keeps the form valid when disabling an environment without a workflow', async () => {
    const toggle = host().querySelector('#enabled input') as HTMLInputElement;
    toggle.click();
    await fixture.whenStable();
    toggle.click();
    await fixture.whenStable();
    expect(saveButton().disabled).toBe(false);
    expect(host().querySelector('p-message')).toBeNull();
  });

  it('submits edited fields with the selected environment id', async () => {
    const displayName = host().querySelector('#display-name') as HTMLInputElement;
    displayName.value = 'Shared staging';
    displayName.dispatchEvent(new Event('input'));
    submit();
    await vi.waitFor(() => expect(updateEnvironment).toHaveBeenCalled());
    const request = updateEnvironment.mock.calls[0][0] as Request;
    expect(request.url).toBe('http://localhost/api/environments/7');
    expect(await request.json()).toEqual(expect.objectContaining({ displayName: 'Shared staging', type: 'STAGING', enabled: false, deploymentWorkflowBranch: 'main' }));
  });

  it('refreshes environment lists, announces success, and returns to the list after saving', async () => {
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue();
    submit();
    await vi.waitFor(() => expect(navigate).toHaveBeenCalled());
    for (const queryKey of [
      getEnvironmentByIdQueryKey({ path: { id: 7 } }),
      getAllEnvironmentsQueryKey(),
      getAllEnabledEnvironmentsQueryKey(),
      getEnvironmentsByUserLockingQueryKey(),
    ]) {
      expect(invalidate).toHaveBeenCalledWith({ queryKey });
    }
    expect(addMessage).toHaveBeenCalledWith({ severity: 'success', summary: 'Success', detail: 'Environment updated successfully' });
    expect(navigate).toHaveBeenCalledWith(['list'], { relativeTo: parentRoute });
  });

  it.each([
    [new Error('Permission denied'), 'Permission denied'],
    [new Error(''), 'Failed to update environment'],
  ])('reports a save failure without navigating away', async (error, detail) => {
    updateEnvironment.mockRejectedValue(error);
    submit();
    await vi.waitFor(() => expect(addMessage).toHaveBeenCalledWith({ severity: 'error', summary: 'Error', detail }));
    expect(navigate).not.toHaveBeenCalled();
  });

  it.each([
    ['HTTP_STATUS', true, 'expecting a 200 OK response'],
    ['ARTEMIS_INFO', true, '/management/info'],
    ['PUSH_UPDATE', false, 'No polling is done'],
    [null, false, null],
  ])('shows the relevant status configuration for %s', async (type, hasUrl, explanation) => {
    fixture.componentInstance.environmentForm.get('statusCheckType')!.setValue(type);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(!!host().querySelector('#status-check-url')).toBe(hasUrl);
    if (explanation) expect(host().textContent).toContain(explanation);
  });

  it('offers only active workflows in filename order, falling back to the workflow name', async () => {
    queryClient.setQueryData(getWorkflowsByRepositoryIdQueryKey({ path: { repositoryId: 3 } }), [
      workflow(1, 'Zebra', { fileNameWithExtension: undefined }),
      workflow(2, 'Build', { fileNameWithExtension: 'aaa.yml' }),
      workflow(3, 'Disabled', { state: 'DISABLED_MANUALLY' }),
    ]);
    await fixture.whenStable();
    (host().querySelector('#deployment-workflow [role="combobox"]') as HTMLElement).click();
    await fixture.whenStable();
    const options = Array.from(document.querySelectorAll('.p-select-option')).map(option => option.textContent?.trim());
    expect(options).toEqual(['aaa.ymlBuild', 'ZebraZebra']);
  });

  it('updates the form when refreshed environment data arrives', async () => {
    queryClient.setQueryData(getEnvironmentByIdQueryKey({ path: { id: 7 } }), { ...environment, displayName: 'Renamed staging' });
    await fixture.whenStable();
    await vi.waitFor(() => expect((host().querySelector('#display-name') as HTMLInputElement).value).toBe('Renamed staging'));
  });
});
