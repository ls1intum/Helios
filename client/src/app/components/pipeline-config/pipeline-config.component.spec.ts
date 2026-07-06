import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

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
    Object.defineProperty(component, 'isPending', { configurable: true, value: () => false });
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
          isSuccess: true,
          data: { categories: [{ name: 'Test', nodes: [{ key: 'client', label: 'Client', jobNameMatchers: ['Test / Client'], workflowNameMatcher: null }] }] },
        }),
      },
    });

    await component.autoDetect();

    expect(component.categories().map(c => c.name)).toEqual(['Test']);
    expect(component.categories()[0].nodes[0].label).toBe('Client');
  });

  it('autoDetect() keeps the current config and surfaces an error when detection fails', async () => {
    const existing = [{ name: 'Build', nodes: [] }];
    component.categories.set(existing);
    const errors: string[] = [];
    const messageService = TestBed.inject(MessageService);
    vi.spyOn(messageService, 'add').mockImplementation((m: { severity?: string }) => errors.push(m.severity ?? ''));
    // refetch reports failure while still returning the last successful data — we must not re-apply it.
    Object.defineProperty(component, 'suggestionsQuery', {
      configurable: true,
      value: {
        isFetching: () => false,
        refetch: async () => ({ isSuccess: false, data: { categories: [{ name: 'Stale', nodes: [] }] } }),
      },
    });

    await component.autoDetect();

    expect(component.categories().map(c => c.name)).toEqual(['Build']); // unchanged, not "Stale"
    expect(errors).toEqual(['error']);
  });

  it('save() is a no-op while the initial config is still loading', () => {
    let mutated = false;
    Object.defineProperty(component, 'updateConfigMutation', {
      configurable: true,
      value: { isPending: () => false, mutate: () => (mutated = true) },
    });
    Object.defineProperty(component, 'isPending', { configurable: true, value: () => true });

    component.save();

    expect(mutated).toBe(false); // never PUT an empty config over the saved one during load
  });

  it('saveNode() gives a duplicate label a distinct, unique key', () => {
    component.categories.set([{ name: 'Build', nodes: [] }]);

    component.openAddNode(0);
    component.draftLabel.set('Native');
    component.saveNode();
    component.openAddNode(0);
    component.draftLabel.set('Native');
    component.saveNode();

    const keys = component.categories()[0].nodes.map(n => n.key);
    expect(keys).toEqual(['native', 'native-2']);
  });
});
