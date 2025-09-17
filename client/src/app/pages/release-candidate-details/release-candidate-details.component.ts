import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import {
  deleteReleaseCandidateByNameMutation,
  evaluateMutation,
  publishReleaseDraftMutation,
  updateReleaseNotesMutation,
  generateReleaseNotesMutation,
  updateReleaseNameMutation,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { getReleaseInfoByName } from '@app/core/modules/openapi';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { ButtonModule } from 'primeng/button';
import { ButtonGroupModule } from 'primeng/buttongroup';
import { SkeletonModule } from 'primeng/skeleton';
import { ReleaseCandidateDeploymentTableComponent } from '@app/components/release-candidate-deployment-table/release-candidate-deployment-table.component';
import { AvatarModule } from 'primeng/avatar';
import { TimeAgoPipe } from '@app/pipes/time-ago.pipe';
import { TooltipModule } from 'primeng/tooltip';
import { TagModule } from 'primeng/tag';
import { ConfirmationService, MessageService } from 'primeng/api';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { PermissionService } from '@app/core/services/permission.service';
import { ActivatedRoute, Router } from '@angular/router';
import { ReleaseCandidateEvaluationDto, ReleaseInfoDetailsDto } from '@app/core/modules/openapi';
import { MarkdownPipe } from '@app/core/modules/markdown/markdown.pipe';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TextareaModule } from 'primeng/textarea';
import { InputTextModule } from 'primeng/inputtext';
import { NgClass, SlicePipe } from '@angular/common';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import {
  IconBrandGithub,
  IconCheck,
  IconUpload,
  IconExternalLink,
  IconGitCommit,
  IconMessageCircle,
  IconPencil,
  IconPlus,
  IconTrash,
  IconUser,
  IconX,
} from 'angular-tabler-icons/icons';
import { DialogService } from 'primeng/dynamicdialog';
import {
  ReleaseEvaluationDialogComponent,
  ReleaseEvaluationDialogData,
  ReleaseEvaluationDialogResult,
} from '@app/components/dialogs/release-evaluation-dialog/release-evaluation-dialog.component';
import { PublishDraftReleaseConfirmationComponent } from '@app/components/dialogs/publish-draft-release-confirmation/publish-draft-release-confirmation.component';

const releaseInfoByNameQueryKey = (name: string) => ['getReleaseInfoByName', name] as const;

@Component({
  selector: 'app-release-candidate-details',
  imports: [
    SkeletonModule,
    ButtonModule,
    MarkdownPipe,
    ReleaseCandidateDeploymentTableComponent,
    ButtonGroupModule,
    AvatarModule,
    TablerIconComponent,
    TimeAgoPipe,
    TooltipModule,
    SlicePipe,
    TagModule,
    ReactiveFormsModule,
    TextareaModule,
    PublishDraftReleaseConfirmationComponent,
    InputTextModule,
    NgClass,
  ],
  providers: [
    provideTablerIcons({
      IconGitCommit,
      IconTrash,
      IconUser,
      IconExternalLink,
      IconUpload,
      IconCheck,
      IconX,
      IconPlus,
      IconPencil,
      IconBrandGithub,
      IconMessageCircle,
    }),
    DialogService,
  ],
  templateUrl: './release-candidate-details.component.html',
})
export class ReleaseCandidateDetailsComponent implements OnInit {
  private messageService = inject(MessageService);
  private keycloakService = inject(KeycloakService);
  permissionService = inject(PermissionService);
  private confirmationService = inject(ConfirmationService);
  private queryClient = inject(QueryClient);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private dialogService = inject(DialogService);

  name = input.required<string>();
  releaseCandidateQuery = injectQuery(() => ({
    queryKey: releaseInfoByNameQueryKey(this.name()),
    queryFn: async ({ signal }) => {
      const { data } = await getReleaseInfoByName({
        body: { name: this.name() },
        signal,
        throwOnError: true,
      });
      return data;
    },
    onSuccess: () => {
      this.releaseNotesForm.get('releaseNotes')?.setValue(this.releaseNotes());
    },
  }));

  releaseNameForm = new FormGroup({
    releaseName: new FormControl(''),
  });

  isEditingName = signal(false);

  releaseNotesForm = new FormGroup({
    releaseNotes: new FormControl(''),
  });

  // Publish to GitHub
  publishDialogVisible = signal(false);
  releaseName = signal<string | undefined>(undefined);

  // Computed property that handles the priorities as required
  releaseNotes = computed(() => {
    const releaseCandidate = this.releaseCandidateQuery.data();
    if (!releaseCandidate) return '';

    // Priority 1: If it's a full release, use release.body
    if (releaseCandidate?.release?.body) {
      return releaseCandidate.release.body;
    }
    // Priority 2: If it's a draft published to GitHub, use releaseCandidate.body
    else if (releaseCandidate?.body) {
      return releaseCandidate.body;
    }
    // Default empty if nothing is available
    else {
      return '';
    }
  });

  isEditingReleaseNotes = signal(false);

  // Check if editing is allowed (only if not published to GitHub)
  canEditReleaseNotes = computed(() => {
    const releaseCandidate = this.releaseCandidateQuery.data();
    return !releaseCandidate?.release && this.permissionService.isAtLeastMaintainer();
  });

  updateReleaseNameMutation = injectMutation(() => ({
    ...updateReleaseNameMutation(),
    onSuccess: () => {
      this.messageService.add({ severity: 'success', summary: 'Release Name', detail: 'Release name updated successfully' });
      this.isEditingName.set(false);
      this.queryClient.invalidateQueries({ queryKey: releaseInfoByNameQueryKey(this.name()) });
      // Update the URL to match the new name
      const newName = this.releaseNameForm.get('releaseName')?.value || '';
      this.router.navigate(['..', newName], { relativeTo: this.route });
    },
    onError: error => {
      this.messageService.add({ severity: 'error', summary: 'Release Name Update Failed', detail: error.message });
    },
  }));

  evaluateReleaseCandidateMutation = injectMutation(() => ({
    ...evaluateMutation(),
    onSuccess: () => {
      this.messageService.add({ severity: 'success', summary: 'Release Candidate Evaluation', detail: 'Your evaluation has been saved successfully' });
      this.queryClient.invalidateQueries({ queryKey: releaseInfoByNameQueryKey(this.name()) });
    },
  }));

  publishReleaseDraftMutation = injectMutation(() => ({
    ...publishReleaseDraftMutation(),
    onSuccess: () => {
      this.messageService.add({ severity: 'success', summary: 'Release Draft Published', detail: 'Release draft has been published to GitHub successfully' });
      this.queryClient.invalidateQueries({ queryKey: releaseInfoByNameQueryKey(this.name()) });
      // Once published, editing should be disabled
      this.isEditingReleaseNotes.set(false);
    },
  }));

  deleteReleaseCandidateMutation = injectMutation(() => ({
    ...deleteReleaseCandidateByNameMutation(),
    onSuccess: () => {
      this.messageService.add({ severity: 'success', summary: 'Release Candidate Deletion', detail: 'Release candidate has been deleted successfully' });
      this.router.navigate(['..'], { relativeTo: this.route });
    },
  }));

  generateReleaseNotesMutation = injectMutation(() => ({
    ...generateReleaseNotesMutation(),
    onSuccess: data => {
      // Set the markdown content directly
      this.releaseNotesForm.get('releaseNotes')?.setValue(data.toString());
      this.isEditingReleaseNotes.set(true);
      this.messageService.add({ severity: 'success', summary: 'Release Notes', detail: 'Release notes generated successfully' });
    },
    onError: error => {
      this.messageService.add({ severity: 'error', summary: 'Release Notes Generation Failed', detail: error.message });
    },
  }));

  updateReleaseNotesMutation = injectMutation(() => ({
    ...updateReleaseNotesMutation(),
    onSuccess: () => {
      this.messageService.add({ severity: 'success', summary: 'Release Notes', detail: 'Release notes saved successfully' });
      this.isEditingReleaseNotes.set(false);
      this.queryClient.invalidateQueries({ queryKey: releaseInfoByNameQueryKey(this.name()) });
    },
    onError: error => {
      this.messageService.add({ severity: 'error', summary: 'Release Notes Update Failed', detail: error.message });
    },
  }));

  ngOnInit() {
    // Initialize with existing release notes using the computed property
    this.releaseNotesForm.get('releaseNotes')?.setValue(this.releaseNotes());
  }

  evaluateReleaseCandidate = (isWorking: boolean) => {
    const release = this.releaseCandidateQuery.data();
    if (!release) {
      return;
    }

    // Find the current user’s previous evaluation (if any)
    const me = this.keycloakService.getPreferredUsername()?.toLowerCase();
    const userEvaluation = release.evaluations.find(e => e.user.login.toLowerCase() === me);

    // Reuse the comment only when the state is the same
    const comment = userEvaluation && userEvaluation.isWorking === isWorking ? (userEvaluation.comment ?? '') : '';

    const dialogData: ReleaseEvaluationDialogData = {
      releaseName: this.name(),
      isWorking: isWorking,
      comment: comment,
    };

    const ref = this.dialogService.open(ReleaseEvaluationDialogComponent, {
      width: '500px',
      data: dialogData,
    });

    ref.onClose.subscribe((result: ReleaseEvaluationDialogResult) => {
      if (result) {
        this.evaluateReleaseCandidateMutation.mutate({
          body: {
            name: this.name(),
            isWorking: result.isWorking,
            comment: result.comment,
          },
        });
      }
    });
  };

  deleteReleaseCandidate = (rc: ReleaseInfoDetailsDto) => {
    this.confirmationService.confirm({
      header: 'Delete Release Candidate',
      message: `Are you sure you want to delete release candidate ${rc.name}? This cannot be undone.`,
      accept: () => {
        this.deleteReleaseCandidateMutation.mutate({ body: { name: rc.name } });
      },
    });
  };

  hasUserEvaluatedTo(isWorking: boolean) {
    const evaluations = this.releaseCandidateQuery.data()?.evaluations;
    if (!evaluations) return false;
    const username = this.keycloakService.getPreferredUsername()?.toLowerCase();
    if (!username) return false;

    const userEvaluation = evaluations.find((evaluation: ReleaseCandidateEvaluationDto) => evaluation.user.login.toLowerCase() === username);
    return userEvaluation?.isWorking === isWorking;
  }

  publishReleaseDraft() {
    const rc = this.releaseCandidateQuery.data();
    if (!rc) return;
    this.releaseName.set(rc.name);
    this.publishDialogVisible.set(true);
  }

  onPublishReleaseDraftConfirmed(yes: boolean) {
    if (yes && this.releaseName()) {
      const rc = this.releaseCandidateQuery.data();
      if (!rc) return;
      this.publishReleaseDraftMutation.mutate({ body: { name: rc.name } });
      this.releaseName.set(undefined);
    }
  }

  openReleaseInGitHub() {
    const rc = this.releaseCandidateQuery.data();
    if (!rc || !rc.release) return;
    window.open(rc.release?.githubUrl, '_blank');
  }

  generateReleaseNotes() {
    const rc = this.releaseCandidateQuery.data();
    if (rc?.release) return; // Don't allow generation if already published

    this.generateReleaseNotesMutation.mutate({
      body: { name: this.name() },
    });
  }

  editReleaseNotes() {
    const rc = this.releaseCandidateQuery.data();
    if (rc?.release) return; // Don't allow editing if already published

    this.releaseNotesForm.get('releaseNotes')?.setValue(this.releaseNotes());
    this.isEditingReleaseNotes.set(true);
  }

  saveReleaseNotes() {
    const rc = this.releaseCandidateQuery.data();
    if (rc?.release) return; // Don't allow saving if already published

    const markdownContent = this.releaseNotesForm.get('releaseNotes')?.value || '';

    this.updateReleaseNotesMutation.mutate({
      body: { name: this.name(), notes: markdownContent },
    });
  }

  cancelEditing() {
    // Reset to original value from the computed property
    this.releaseNotesForm.get('releaseNotes')?.setValue(this.releaseNotes());
    this.isEditingReleaseNotes.set(false);
  }

  editName() {
    const rc = this.releaseCandidateQuery.data();
    if (rc?.release) return; // Don't allow editing if already published

    this.releaseNameForm.get('releaseName')?.setValue(rc?.name || '');
    this.isEditingName.set(true);
  }

  saveName() {
    const rc = this.releaseCandidateQuery.data();
    if (rc?.release) return; // Don't allow saving if already published

    const newName = this.releaseNameForm.get('releaseName')?.value || '';
    if (!newName.trim()) {
      this.messageService.add({ severity: 'error', summary: 'Invalid Name', detail: 'Release name cannot be empty' });
      return;
    }

    this.updateReleaseNameMutation.mutate({
      body: { oldName: this.name(), newName: newName },
    });
  }

  cancelEditingName() {
    this.isEditingName.set(false);
  }

  getEvaluationTooltip(evaluation: ReleaseCandidateEvaluationDto): string {
    const status = evaluation.isWorking ? 'Marked as working' : 'Marked as broken';

    if (!evaluation.comment || evaluation.comment.trim() === '') {
      return status;
    }

    return `${status}\n\nComment: ${evaluation.comment}`;
  }
}
