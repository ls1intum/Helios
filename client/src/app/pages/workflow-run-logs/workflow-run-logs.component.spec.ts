import { ComponentFixture, TestBed } from '@angular/core/testing';
import { importProvidersFrom } from '@angular/core';
import { TestModule } from '@app/test.module';
import { WorkflowRunLogsComponent } from './workflow-run-logs.component';
import { getAutoExpandedLogGroupIds } from './workflow-run-logs.utils';

describe('Integration Test Workflow Run Logs Page', () => {
  let component: WorkflowRunLogsComponent;
  let fixture: ComponentFixture<WorkflowRunLogsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkflowRunLogsComponent],
      providers: [importProvidersFrom(TestModule)],
    }).compileComponents();

    fixture = TestBed.createComponent(WorkflowRunLogsComponent);
    component = fixture.componentInstance;

    fixture.componentRef.setInput('repositoryId', 1);
    fixture.componentRef.setInput('workflowRunId', 42);

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should return a success icon for successful workflow runs', () => {
    expect(component.getWorkflowRunOutcome('SUCCESS')).toMatchObject({
      icon: 'circle-check',
      label: 'Success',
    });
  });

  it('should return a failure icon for failed workflow runs', () => {
    expect(component.getWorkflowRunOutcome('FAILURE')).toMatchObject({
      icon: 'circle-x',
      label: 'Failure',
    });
  });

  it('should return a cancelled icon for cancelled workflow runs', () => {
    expect(component.getWorkflowRunOutcome('CANCELLED')).toMatchObject({
      icon: 'circle-minus',
      label: 'Cancelled',
    });
  });

  it('should classify normalized GitHub error lines for log highlighting', () => {
    expect(component.getLineTone('[error]Process completed with exit code 1')).toBe('error');
  });

  it('should classify only explicit GitHub square bracket markers', () => {
    expect(component.getLineTone('[command]/usr/bin/git status')).toBe('command');
    expect(component.getLineTone('[warning]Using a deprecated flag')).toBe('warning');
    expect(component.getLineTone('[group]Run actions/checkout@v4')).toBe('group');
    expect(component.getLineTone('Post job cleanup.')).toBe('default');
    expect(component.getLineTone('Complete job name: build')).toBe('default');
  });

  it('should render group headers without the GitHub marker prefix', () => {
    expect(
      component.getRenderedLineContent({
        number: 1,
        content: '[group]Runner Image Provisioner',
        tone: 'group',
      })
    ).toBe('Runner Image Provisioner');
  });

  it('should render command lines without the GitHub command marker prefix', () => {
    expect(
      component.getRenderedLineContent({
        number: 2,
        content: '[command]/usr/bin/git status',
        tone: 'command',
      })
    ).toBe('/usr/bin/git status');
  });

  it('should toggle line tone filters', () => {
    expect(component.isLineToneEnabled('error')).toBe(true);
    component.toggleLineTone('error');
    expect(component.isLineToneEnabled('error')).toBe(false);
    component.toggleLineTone('error');
    expect(component.isLineToneEnabled('error')).toBe(true);
  });

  it('should keep log groups collapsed by default and toggle them on demand', () => {
    expect(component.isLogGroupExpanded('group-12')).toBe(false);
    component.toggleLogGroup('group-12');
    expect(component.isLogGroupExpanded('group-12')).toBe(true);
    component.toggleLogGroup('group-12');
    expect(component.isLogGroupExpanded('group-12')).toBe(false);
  });

  it('should auto-expand log groups that contain error lines', () => {
    const autoExpandedGroups = getAutoExpandedLogGroupIds(
      ['[group]Build', 'setup', '[group]Compile', '[error]Process completed with exit code 1', '[endgroup]', '[endgroup]'].join('\n'),
      'error'
    );

    expect(autoExpandedGroups).toEqual({
      'group-1': true,
      'group-3': true,
    });
  });
});
