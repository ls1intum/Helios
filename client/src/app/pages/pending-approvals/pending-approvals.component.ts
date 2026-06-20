import { Component, effect, ElementRef, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { SkeletonModule } from 'primeng/skeleton';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { TextareaModule } from 'primeng/textarea';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { FormsModule } from '@angular/forms';
import { injectQuery, injectMutation } from '@tanstack/angular-query-experimental';
import { myPendingApprovalsOptions, myPendingApprovalsQueryKey } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { approve, decline, PendingApprovalDto } from '@app/core/modules/openapi';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconCheck, IconX, IconClock, IconGitBranch, IconExternalLink } from 'angular-tabler-icons/icons';
import { QueryClient } from '@tanstack/angular-query-experimental';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';

/**
 * Cross-repo list of deployments waiting on the current user as a required reviewer. Each row
 * carries Approve and Decline actions that call into the server's impersonation path so the
 * reviewer never has to bounce through the GitHub UI.
 *
 * <p>Polls every 30 s so a reviewer who keeps this tab open sees newly-pending deployments
 * (e.g. when someone else clicks Deploy on a protected env and the workflow reaches the gate).
 */
@Component({
  selector: 'app-pending-approvals',
  imports: [
    CommonModule,
    RouterLink,
    FormsModule,
    TableModule,
    ButtonModule,
    TooltipModule,
    SkeletonModule,
    TagModule,
    DialogModule,
    TextareaModule,
    ConfirmDialogModule,
    ToastModule,
    TablerIconComponent,
    PageHeadingComponent,
  ],
  providers: [ConfirmationService, MessageService, provideTablerIcons({ IconCheck, IconX, IconClock, IconGitBranch, IconExternalLink })],
  templateUrl: './pending-approvals.component.html',
  styleUrl: './pending-approvals.component.css',
})
export class PendingApprovalsComponent {
  private queryClient = inject(QueryClient);
  private confirmationService = inject(ConfirmationService);
  private messageService = inject(MessageService);
  private hostEl: ElementRef<HTMLElement> = inject(ElementRef);

  /** Source of truth for the list — polled so the badge & table stay fresh while the tab is open. */
  pendingQuery = injectQuery(() => ({
    ...myPendingApprovalsOptions(),
    refetchInterval: 30_000,
  }));

  /**
   * Deep-link target from the approval-request email (`/pending-approvals?focus=<id>`). When set,
   * the matching row scrolls into view and pulses briefly so the reviewer's eye lands on it. The
   * auth flow can interleave a Keycloak redirect between the email click and the page actually
   * mounting; both before and after-login arrivals are handled because the focus signal is
   * driven by the query-param observable, not a one-shot init read.
   */
  private focusDeploymentId = toSignal(
    inject(ActivatedRoute).queryParamMap.pipe(
      map(params => {
        const raw = params.get('focus');
        if (!raw) return null;
        const n = Number(raw);
        return Number.isFinite(n) && n > 0 ? n : null;
      })
    ),
    { initialValue: null as number | null }
  );

  /** Row id we've already focused (so refetches don't re-scroll the user mid-read). */
  private alreadyFocusedId: number | null = null;

  constructor() {
    // When both the focus param and the row data are present, scroll once and highlight.
    effect(() => {
      const target = this.focusDeploymentId();
      const rows = this.pendingQuery.data() ?? [];
      if (target == null || target === this.alreadyFocusedId) return;
      const row = rows.find(r => r.deploymentId === target);
      if (!row) return;
      this.alreadyFocusedId = target;
      // Defer to next paint so the table has rendered the row.
      queueMicrotask(() => {
        const el = this.hostEl.nativeElement.querySelector(`tr[data-deployment-id="${target}"]`) as HTMLElement | null;
        if (!el) return;
        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        el.classList.add('row-focused');
        setTimeout(() => el.classList.remove('row-focused'), 2400);
      });
    });
  }

  declineDialogVisible = signal(false);
  declineTarget = signal<PendingApprovalDto | null>(null);
  declineComment = signal('');

  /** Tracks which row is mid-flight so we can disable buttons + show a spinner per row. */
  inFlightDeploymentId = signal<number | null>(null);

  approveMutation = injectMutation(() => ({
    mutationFn: (deploymentId: number) => approve({ path: { deploymentId }, throwOnError: true }).then(r => r.data!),
    onSuccess: row => {
      this.messageService.add({
        severity: 'success',
        summary: 'Approved',
        detail: `Deployment ${row.deploymentId} approved via Helios.`,
        life: 4000,
      });
    },
    onError: (err: unknown) => this.showServerError('Approve failed', err),
    onSettled: () => {
      this.inFlightDeploymentId.set(null);
      this.refreshList();
    },
  }));

  declineMutation = injectMutation(() => ({
    mutationFn: (params: { deploymentId: number; comment: string | null }) =>
      decline({
        path: { deploymentId: params.deploymentId },
        body: params.comment ? { comment: params.comment } : {},
        throwOnError: true,
      }).then(r => r.data!),
    onSuccess: row => {
      this.messageService.add({
        severity: 'warn',
        summary: 'Declined',
        detail: `Deployment ${row.deploymentId} declined — workflow run rejected on GitHub.`,
        life: 5000,
      });
    },
    onError: (err: unknown) => this.showServerError('Decline failed', err),
    onSettled: () => {
      this.inFlightDeploymentId.set(null);
      this.refreshList();
    },
  }));

  onApprove(row: PendingApprovalDto) {
    if (!row.deploymentId) return;
    const deploymentId = row.deploymentId;
    // PrimeNG renders the confirm message via [innerHTML] and this version has no `escape`
    // option, so the GitHub-controlled environment/repo names must be HTML-escaped before they
    // are interpolated — an environment named `<img src=x onerror=...>` would otherwise run
    // script in the reviewer's session. (The decline copy in the template uses {{ }}, which
    // Angular escapes automatically.)
    const env = this.escapeHtml(row.environmentName);
    const repo = this.escapeHtml(row.repositoryNameWithOwner);
    this.confirmationService.confirm({
      header: 'Approve this deployment?',
      message: `Helios will approve deployment to <b>${env}</b> on <b>${repo}</b> as you.`,
      accept: () => {
        this.inFlightDeploymentId.set(deploymentId);
        this.approveMutation.mutate(deploymentId);
      },
    });
  }

  openDeclineDialog(row: PendingApprovalDto) {
    this.declineTarget.set(row);
    this.declineComment.set('');
    this.declineDialogVisible.set(true);
  }

  confirmDecline() {
    const target = this.declineTarget();
    if (!target || !target.deploymentId) return;
    const deploymentId = target.deploymentId;
    const comment = this.declineComment().trim();
    this.declineDialogVisible.set(false);
    this.inFlightDeploymentId.set(deploymentId);
    this.declineMutation.mutate({ deploymentId, comment: comment.length ? comment : null });
  }

  private refreshList() {
    this.queryClient.invalidateQueries({ queryKey: myPendingApprovalsQueryKey() });
  }

  /** Escapes HTML metacharacters so GitHub-controlled strings are safe inside a PrimeNG
   * confirm message (rendered via [innerHTML], which does not escape). */
  private escapeHtml(value: string | undefined | null): string {
    return (value ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  private showServerError(summary: string, err: unknown) {
    const message =
      typeof err === 'object' && err !== null && 'message' in err && typeof (err as { message: unknown }).message === 'string'
        ? (err as { message: string }).message
        : 'See server logs for details.';
    this.messageService.add({ severity: 'error', summary, detail: message, life: 8000 });
  }
}
