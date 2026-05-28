import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
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
})
export class PendingApprovalsComponent {
  private queryClient = inject(QueryClient);
  private confirmationService = inject(ConfirmationService);
  private messageService = inject(MessageService);

  /** Source of truth for the list — polled so the badge & table stay fresh while the tab is open. */
  pendingQuery = injectQuery(() => ({
    ...myPendingApprovalsOptions(),
    refetchInterval: 30_000,
  }));

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
    this.confirmationService.confirm({
      header: 'Approve this deployment?',
      message: `Helios will approve deployment to <b>${row.environmentName}</b> on <b>${row.repositoryNameWithOwner}</b> as you.`,
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

  private showServerError(summary: string, err: unknown) {
    const message =
      typeof err === 'object' && err !== null && 'message' in err && typeof (err as { message: unknown }).message === 'string'
        ? (err as { message: string }).message
        : 'See server logs for details.';
    this.messageService.add({ severity: 'error', summary, detail: message, life: 8000 });
  }
}
