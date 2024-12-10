import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';

@Component({
  selector: 'app-connect-repo',
  imports: [CommonModule, ButtonModule, DialogModule],
  templateUrl: './connect-repo.component.html',
  styleUrl: './connect-repo.component.css'
})
export class ConnectRepoComponent {
  visible = false;

  show() {
    this.visible = true;
  }

  hide() {
    this.visible = false;
  }

  connectGithub() {
    // Implement GitHub connection logic
    console.log('Connecting to GitHub...');
  }

  connectGitlab() {
    // Implement GitLab connection logic
    console.log('Connecting to GitLab...');
  }
}
