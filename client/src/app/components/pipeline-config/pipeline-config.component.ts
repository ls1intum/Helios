import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { DividerModule } from 'primeng/divider';
import { InputTextModule } from 'primeng/inputtext';
import { PanelModule } from 'primeng/panel';
import { SkeletonModule } from 'primeng/skeleton';
import { TooltipModule } from 'primeng/tooltip';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconArrowDown, IconArrowUp, IconPencil, IconPlus, IconTrash, IconWand } from 'angular-tabler-icons/icons';
import {
  getPipelineConfigOptions,
  getPipelineConfigQueryKey,
  getPipelineConfigSuggestionsOptions,
  updatePipelineConfigMutation,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { PipelineConfigDto } from '@app/core/modules/openapi';

/** Local editable node/category shape (all fields required, unlike the optional generated DTOs). */
export interface EditableNode {
  key: string;
  label: string;
  jobNameMatchers: string[];
  workflowNameMatcher: string | null;
}
export interface EditableCategory {
  name: string;
  nodes: EditableNode[];
}

@Component({
  selector: 'app-pipeline-config',
  imports: [FormsModule, ButtonModule, DialogModule, DividerModule, InputTextModule, PanelModule, SkeletonModule, TooltipModule, TablerIconComponent],
  providers: [provideTablerIcons({ IconPlus, IconTrash, IconPencil, IconArrowUp, IconArrowDown, IconWand })],
  templateUrl: './pipeline-config.component.html',
})
export class PipelineConfigComponent {
  private readonly queryClient = inject(QueryClient);
  private readonly messageService = inject(MessageService);

  repositoryId = input.required<number>();

  // Local editable copy of the config; seeded from the query, edited immutably (zoneless).
  categories = signal<EditableCategory[]>([]);

  configQuery = injectQuery(() => ({
    ...getPipelineConfigOptions({ path: { repositoryId: this.repositoryId() } }),
    enabled: () => !!this.repositoryId(),
  }));

  updateConfigMutation = injectMutation(() => ({
    ...updatePipelineConfigMutation(),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: getPipelineConfigQueryKey({ path: { repositoryId: this.repositoryId() } }) });
      this.messageService.add({ severity: 'success', summary: 'Saved', detail: 'Pipeline configuration updated.' });
    },
    onError: () => this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to save pipeline configuration.' }),
  }));

  isPending = computed(() => this.configQuery.isPending());
  isSaving = computed(() => this.updateConfigMutation.isPending());

  // Node edit dialog state
  dialogVisible = signal(false);
  private editingCategoryIndex = signal<number | null>(null);
  private editingNodeIndex = signal<number | null>(null);
  draftLabel = signal('');
  draftKey = signal('');
  draftMatchers = signal('');
  draftWorkflowMatcher = signal('');

  constructor() {
    // Seed the editable copy whenever the server config arrives (and on refetch after save).
    effect(
      () => {
        const data = this.configQuery.data();
        if (data) {
          this.categories.set(toEditable(data));
        }
      },
      { allowSignalWrites: true }
    );
  }

  // --- Category ops ---------------------------------------------------------------------------

  addCategory(): void {
    this.categories.update(cats => [...cats, { name: 'New stage', nodes: [] }]);
  }

  removeCategory(index: number): void {
    this.categories.update(cats => cats.filter((_, i) => i !== index));
  }

  renameCategory(index: number, name: string): void {
    this.categories.update(cats => cats.map((c, i) => (i === index ? { ...c, name } : c)));
  }

  moveCategory(index: number, delta: number): void {
    this.categories.update(cats => move(cats, index, delta));
  }

  // --- Node ops -------------------------------------------------------------------------------

  moveNode(categoryIndex: number, nodeIndex: number, delta: number): void {
    this.categories.update(cats => cats.map((c, i) => (i === categoryIndex ? { ...c, nodes: move(c.nodes, nodeIndex, delta) } : c)));
  }

  removeNode(categoryIndex: number, nodeIndex: number): void {
    this.categories.update(cats => cats.map((c, i) => (i === categoryIndex ? { ...c, nodes: c.nodes.filter((_, n) => n !== nodeIndex) } : c)));
  }

  openAddNode(categoryIndex: number): void {
    this.editingCategoryIndex.set(categoryIndex);
    this.editingNodeIndex.set(null);
    this.draftLabel.set('');
    this.draftKey.set('');
    this.draftMatchers.set('');
    this.draftWorkflowMatcher.set('');
    this.dialogVisible.set(true);
  }

  openEditNode(categoryIndex: number, nodeIndex: number): void {
    const node = this.categories()[categoryIndex]?.nodes[nodeIndex];
    if (!node) return;
    this.editingCategoryIndex.set(categoryIndex);
    this.editingNodeIndex.set(nodeIndex);
    this.draftLabel.set(node.label);
    this.draftKey.set(node.key);
    this.draftMatchers.set(node.jobNameMatchers.join(', '));
    this.draftWorkflowMatcher.set(node.workflowNameMatcher ?? '');
    this.dialogVisible.set(true);
  }

  saveNode(): void {
    const categoryIndex = this.editingCategoryIndex();
    if (categoryIndex === null) return;
    const label = this.draftLabel().trim();
    if (!label) return;
    const node: EditableNode = {
      key: this.draftKey().trim() || slug(label),
      label,
      jobNameMatchers: this.draftMatchers()
        .split(',')
        .map(s => s.trim())
        .filter(s => s.length > 0),
      workflowNameMatcher: this.draftWorkflowMatcher().trim() || null,
    };
    const nodeIndex = this.editingNodeIndex();
    this.categories.update(cats =>
      cats.map((c, i) => {
        if (i !== categoryIndex) return c;
        const nodes = nodeIndex === null ? [...c.nodes, node] : c.nodes.map((n, ni) => (ni === nodeIndex ? node : n));
        return { ...c, nodes };
      })
    );
    this.dialogVisible.set(false);
  }

  // --- Auto-detect + save ---------------------------------------------------------------------

  suggestionsQuery = injectQuery(() => ({
    ...getPipelineConfigSuggestionsOptions({ path: { repositoryId: this.repositoryId() } }),
    enabled: false, // manual: only when the user clicks "Auto-detect"
  }));

  isDetecting = computed(() => this.suggestionsQuery.isFetching());

  async autoDetect(): Promise<void> {
    const result = await this.suggestionsQuery.refetch();
    if (result.data) {
      // Replaces the editable copy with the suggestion; nothing persists until Save.
      this.categories.set(toEditable(result.data));
      this.messageService.add({ severity: 'info', summary: 'Auto-detected', detail: 'Review the suggested nodes, then Save to apply.' });
    }
  }

  save(): void {
    this.updateConfigMutation.mutate({
      path: { repositoryId: this.repositoryId() },
      body: { categories: this.categories() } satisfies PipelineConfigDto,
    });
  }
}

function toEditable(dto: PipelineConfigDto): EditableCategory[] {
  return (dto.categories ?? []).map(c => ({
    name: c.name ?? '',
    nodes: (c.nodes ?? []).map(n => ({
      key: n.key ?? '',
      label: n.label ?? '',
      jobNameMatchers: n.jobNameMatchers ?? [],
      workflowNameMatcher: n.workflowNameMatcher ?? null,
    })),
  }));
}

function move<T>(items: T[], index: number, delta: number): T[] {
  const target = index + delta;
  if (target < 0 || target >= items.length) return items;
  const copy = [...items];
  const [item] = copy.splice(index, 1);
  copy.splice(target, 0, item);
  return copy;
}

function slug(value: string): string {
  return (
    value
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/(^-+|-+$)/g, '') || 'node'
  );
}
