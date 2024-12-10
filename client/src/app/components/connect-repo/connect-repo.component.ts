import { CommonModule } from '@angular/common';
import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GithubOrg, GithubRepo, GithubService } from '@app/core/services/github.service';
import { RepositoryService } from '@app/core/services/repository.service';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { finalize } from 'rxjs';

@Component({
  selector: 'app-connect-repo',
  imports: [
    CommonModule,
    ButtonModule,
    DialogModule,
    DropdownModule,
    CardModule,
    FormsModule],
  templateUrl: './connect-repo.component.html',
  styleUrl: './connect-repo.component.css'
})
export class ConnectRepoComponent {
  visible = false;
  loading = signal(false);
  currentStep = signal<'org_selection' | 'repo_selection'>('org_selection');

  organizations = signal<GithubOrg[]>([]);
  repositories = signal<GithubRepo[]>([]);
  selectedOrg = signal<GithubOrg | null>(null);
  connectingRepoId = signal<number | null>(null);

  constructor(
    private githubService: GithubService,
    private repositoryService: RepositoryService
  ) { }

  show() {
    this.visible = true;
    this.loadOrganizations();
  }

  hide() {
    this.visible = false;
    this.reset();
  }

  private reset() {
    this.currentStep.set('org_selection');
    this.organizations.set([]);
    this.repositories.set([]);
    this.selectedOrg.set(null);
    this.loading.set(false);
  }

  private loadOrganizations() {
    this.loading.set(true);
    this.githubService.getOrganizations().pipe(
      finalize(() => this.loading.set(false))
    ).subscribe({
      next: (orgs) => {
        this.organizations.set(orgs);
      },
      error: (error) => {
        console.error('Failed to fetch organizations:', error);
        // Handle error (show toast or message)
      }
    });
  }

  selectOrganization(org: GithubOrg) {
    this.selectedOrg.set(org);
    this.loading.set(true);

    this.githubService.getOrgRepositories(org.login).pipe(
      finalize(() => this.loading.set(false))
    ).subscribe({
      next: (repos) => {
        this.repositories.set(repos);
        this.currentStep.set('repo_selection');
      },
      error: (error) => {
        console.error('Failed to fetch repositories:', error);
        // Handle error (show toast or message)
      }
    });
  }

  selectRepository(repo: GithubRepo) {
    this.connectingRepoId.set(repo.id);

    this.repositoryService.connectRepository({
      name: repo.name,
      id: repo.id,
      description: repo.description || undefined,
      nameWithOwner: repo.full_name,
      htmlUrl: repo.html_url
    }).pipe(
      finalize(() => this.connectingRepoId.set(repo.id))
    ).subscribe({
      next: () => {
        // Handle success (show toast or message)
        this.hide();
      },
      error: (error) => {
        console.error('Failed to connect repository:', error);
        // Handle error (show toast or message)
      }
    });
  }
}
