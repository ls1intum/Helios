import { Component, signal } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { FloatLabelModule } from 'primeng/floatlabel';
import { FormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';


@Component({
  selector: 'app-project-setup',
  imports: [CommonModule, CardModule, ButtonModule, FormsModule, InputTextModule, FloatLabelModule],
  templateUrl: './project-setup.component.html',
  styleUrl: './project-setup.component.css'
})
export class ProjectSetupComponent {
  token = signal('');
  repoLink = signal('');
  repositoryInfo = signal<any>(null);

  constructor(private http: HttpClient) { }

  fetchRepositoryInfo() {
    const [owner, repo] = this.extractOwnerAndRepo();

    const headers = new HttpHeaders({
      'Accept': 'application/vnd.github+json',
      'Authorization': `Bearer ${this.token()}`,
      'X-GitHub-Api-Version': '2022-11-28'
    });

    const url = `https://api.github.com/repos/${owner}/${repo}`;

    this.http.get(url, { headers }).subscribe(
      (data: any) => {
        this.repositoryInfo.set(data);
      },
      (error) => {
        console.error('Failed to fetch repository information:', error);
      }
    );
  }

  extractOwnerAndRepo(): [string, string] {
    const repoLink = this.repoLink();
    const path = repoLink.split('github.com');
    const parts = path[1].split('/');
    const owner = parts[1];
    const repo = parts[2];
    return [owner, repo];
  }
}
