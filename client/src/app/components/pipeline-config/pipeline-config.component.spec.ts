import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PipelineConfigComponent } from './pipeline-config.component';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MessageService } from 'primeng/api';

describe('PipelineConfigComponent', () => {
  let component: PipelineConfigComponent;
  let fixture: ComponentFixture<PipelineConfigComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PipelineConfigComponent],
      providers: [provideZonelessChangeDetection(), provideQueryClient(new QueryClient()), provideNoopAnimations(), MessageService],
    }).compileComponents();

    fixture = TestBed.createComponent(PipelineConfigComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('repositoryId', 7);
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('adds, edits, moves and removes nodes immutably', () => {
    component.categories.set([{ name: 'Build', nodes: [] }]);

    // add a node via the dialog draft
    component.openAddNode(0);
    component.draftLabel.set('Native');
    component.draftMatchers.set('Build / native, Build / war');
    component.saveNode();

    let build = component.categories()[0];
    expect(build.nodes.length).toBe(1);
    expect(build.nodes[0].label).toBe('Native');
    expect(build.nodes[0].jobNameMatchers).toEqual(['Build / native', 'Build / war']);
    expect(build.nodes[0].key).toBe('native'); // slug from label

    // add a second node, then move it up
    component.openAddNode(0);
    component.draftLabel.set('Docker');
    component.saveNode();
    component.moveNode(0, 1, -1);
    expect(component.categories()[0].nodes.map(n => n.label)).toEqual(['Docker', 'Native']);

    // remove one
    component.removeNode(0, 0);
    expect(component.categories()[0].nodes.map(n => n.label)).toEqual(['Native']);
  });

  it('adds and removes categories', () => {
    component.categories.set([]);
    component.addCategory();
    expect(component.categories().length).toBe(1);
    component.removeCategory(0);
    expect(component.categories().length).toBe(0);
  });

  it('save() sends the current categories to the update mutation', () => {
    let captured: unknown = null;
    Object.defineProperty(component, 'updateConfigMutation', {
      configurable: true,
      value: { isPending: () => false, mutate: (arg: unknown) => (captured = arg) },
    });
    component.categories.set([{ name: 'Build', nodes: [{ key: 'native', label: 'Native', jobNameMatchers: ['Build / native'], workflowNameMatcher: null }] }]);

    component.save();

    expect(captured).toEqual({
      path: { repositoryId: 7 },
      body: { categories: [{ name: 'Build', nodes: [{ key: 'native', label: 'Native', jobNameMatchers: ['Build / native'], workflowNameMatcher: null }] }] },
    });
  });

  it('autoDetect() replaces the editable config with the suggestion', async () => {
    Object.defineProperty(component, 'suggestionsQuery', {
      configurable: true,
      value: {
        isFetching: () => false,
        refetch: async () => ({
          data: { categories: [{ name: 'Test', nodes: [{ key: 'client', label: 'Client', jobNameMatchers: ['Test / Client'], workflowNameMatcher: null }] }] },
        }),
      },
    });

    await component.autoDetect();

    expect(component.categories().map(c => c.name)).toEqual(['Test']);
    expect(component.categories()[0].nodes[0].label).toBe('Client');
  });
});
