import { Component, inject, input, OnInit } from '@angular/core';
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
import { IconsModule } from 'icons.module';
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
import { InplaceModule } from 'primeng/inplace';
import { TextareaModule } from 'primeng/textarea';
import { InputTextModule } from 'primeng/inputtext';
import { SlicePipe } from '@angular/common';

@Component({
  selector: 'app-release-candidate-details',
  imports: [
    SkeletonModule,
    ButtonModule,
    MarkdownPipe,
    ReleaseCandidateDeploymentTableComponent,
    ButtonGroupModule,
    AvatarModule,
    IconsModule,
    TimeAgoPipe,
    TooltipModule,
    SlicePipe,
    TagModule,
    ReactiveFormsModule,
    InplaceModule,
    TextareaModule,
    InputTextModule,
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
  releaseCandidateQuery = injectQuery(() => ({ ...getReleaseInfoByNameOptions({ path: { name: this.name() } }), refetchInterval: 3000 }));

  releaseNotesForm = new FormGroup({
    releaseNotes: new FormControl(''),
  });

  inplaceActive = false;

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
      this.inplaceActive = true; // Open the editor
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
      this.inplaceActive = false; // Close the editor after saving
      this.queryClient.invalidateQueries({ queryKey: getReleaseInfoByNameQueryKey({ path: { name: this.name() } }) });
    },
    onError: error => {
      this.messageService.add({ severity: 'error', summary: 'Release Notes Update Failed', detail: error.message });
    },
  }));

  ngOnInit() {
    // Initialize with existing release notes if available
    const releaseCandidate = this.releaseCandidateQuery.data();
    if (releaseCandidate?.release?.body) {
      this.releaseNotesForm.get('releaseNotes')?.setValue(releaseCandidate.release.body);
    }
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
    this.generateReleaseNotesMutation.mutate({
      path: { tagName: this.name() },
    });
  }

  saveReleaseNotes() {
    const markdownContent = this.releaseNotesForm.get('releaseNotes')?.value || '';

    this.updateReleaseNotesMutation.mutate({
      path: { name: this.name() },
      body: { body: markdownContent },
    });
  }

  closeInplace() {
    this.inplaceActive = false;
  }
}
