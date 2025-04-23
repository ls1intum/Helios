import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import {
  deleteReleaseCandidateByNameMutation,
  evaluateMutation,
  getReleaseInfoByNameOptions,
  getReleaseInfoByNameQueryKey,
  publishReleaseDraftMutation,
  updateReleaseNotesMutation,
  generateReleaseNotesMutation,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
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
import { ReleaseInfoDetailsDto } from '@app/core/modules/openapi';
import { MarkdownPipe } from '@app/core/modules/markdown/markdown.pipe';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TextareaModule } from 'primeng/textarea';
import { SlicePipe } from '@angular/common';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconCheck, IconCloudUpload, IconExternalLink, IconGitCommit, IconPencil, IconPlus, IconTrash, IconUser, IconX } from 'angular-tabler-icons/icons';

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
  ],
  providers: [
    provideTablerIcons({
      IconGitCommit,
      IconTrash,
      IconUser,
      IconExternalLink,
      IconCloudUpload,
      IconCheck,
      IconX,
      IconPlus,
      IconPencil,
    }),
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

  name = input.required<string>();
  releaseCandidateQuery = injectQuery(() => ({
    ...getReleaseInfoByNameOptions({ path: { name: this.name() } }),
    onSuccess: () => {
      this.releaseNotesForm.get('releaseNotes')?.setValue(this.releaseNotes());
    },
  }));

  releaseNotesForm = new FormGroup({
    releaseNotes: new FormControl(''),
  });

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

  evaluateReleaseCandidateMutation = injectMutation(() => ({
    ...evaluateMutation(),
    onSuccess: () => {
      this.messageService.add({ severity: 'success', summary: 'Release Candidate Evaluation', detail: 'Your evaluation has been saved successfully' });
      this.queryClient.invalidateQueries({ queryKey: getReleaseInfoByNameQueryKey({ path: { name: this.name() } }) });
    },
  }));

  publishReleaseDraftMutation = injectMutation(() => ({
    ...publishReleaseDraftMutation(),
    onSuccess: () => {
      this.messageService.add({ severity: 'success', summary: 'Release Draft Published', detail: 'Release draft has been published to GitHub successfully' });
      this.queryClient.invalidateQueries({ queryKey: getReleaseInfoByNameQueryKey({ path: { name: this.name() } }) });
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
      this.queryClient.invalidateQueries({ queryKey: getReleaseInfoByNameQueryKey({ path: { name: this.name() } }) });
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
    this.evaluateReleaseCandidateMutation.mutate({ path: { name: this.name(), isWorking } });
  };

  deleteReleaseCandidate = (rc: ReleaseInfoDetailsDto) => {
    this.confirmationService.confirm({
      header: 'Delete Release Candidate',
      message: `Are you sure you want to delete release candidate ${rc.name}? This cannot be undone.`,
      accept: () => {
        this.deleteReleaseCandidateMutation.mutate({ path: { name: rc.name } });
      },
    });
  };

  hasUserEvaluatedTo(isWorking: boolean) {
    const evaluations = this.releaseCandidateQuery.data()?.evaluations;
    if (!evaluations) return false;
    const userEvaluation = evaluations.find(evaluation => evaluation.user.login.toLowerCase() === this.keycloakService.getPreferredUsername()?.toLowerCase());
    return userEvaluation?.isWorking === isWorking;
  }

  publishReleaseDraft() {
    const rc = this.releaseCandidateQuery.data();
    if (!rc) return;
    this.confirmationService.confirm({
      header: 'Publish Release Candidate',
      message: `Are you sure you want to publish release candidate ${rc.name} as a draft to GitHub? This can only be undone in GitHub itself.`,
      accept: () => {
        this.publishReleaseDraftMutation.mutate({ path: { name: rc.name } });
      },
    });
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
      path: { tagName: this.name() },
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
      path: { name: this.name() },
      body: { body: markdownContent },
    });
  }

  cancelEditing() {
    // Reset to original value from the computed property
    this.releaseNotesForm.get('releaseNotes')?.setValue(this.releaseNotes());
    this.isEditingReleaseNotes.set(false);
  }
}
