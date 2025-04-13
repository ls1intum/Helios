import { Component, OnInit, signal } from '@angular/core';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';
import { NgClass, NgOptimizedImage } from '@angular/common';
import { UserInfoDto } from '@app/core/modules/openapi';
import { UserAvatarComponent } from '@app/components/user-avatar/user-avatar.component';
import { ButtonModule } from 'primeng/button';

interface Feature {
  icon: string;
  title: string;
  description: string;
}

export type GitHubContributorInfo = {
  id: number;
  login: string;
  avatar_url: string;
  html_url: string;
  contributions: string;
};

export type ExtendedUserInfoDto = UserInfoDto & {
  contributions: string;
};

@Component({
  selector: 'app-about',
  imports: [PageHeadingComponent, ButtonModule, NgOptimizedImage, NgClass, UserAvatarComponent],
  templateUrl: './about.component.html',
})
export class AboutComponent implements OnInit {
  projectAdvisors = [
    {
      name: 'Prof. Dr. Stephan Krusche',
      description: 'Project Advisor',
      website: 'https://ase.cit.tum.de/people/krusche/',
      githubHandle: 'krusche',
      githubId: 744067,
    },
  ];

  contributors = [
    { name: 'Galiiabanu Bakirova', description: "Master's Thesis", website: null, githubHandle: 'gbanu', githubId: 56745022 },
    { name: 'Ege Kocabaş', description: "Master's Thesis", website: null, githubHandle: 'egekocabas', githubId: 48245934 },
    { name: 'Turker Koç', description: "Master's Thesis", website: null, githubHandle: 'TurkerKoc', githubId: 39654393 },
    { name: 'Paul Thiel', description: 'Bachelor’s Thesis', website: null, githubHandle: 'thielpa', githubId: 10695477 },
    { name: 'Stefan Németh', description: 'Bachelor’s Thesis', website: null, githubHandle: 'StefanNemeth', githubId: 5003405 },
  ];

  // Fetched contributors from GitHub
  githubContributors = signal<ExtendedUserInfoDto[]>([]);

  // ============== FEATURES ==============
  coreFeatures: Feature[] = [
    {
      icon: 'pi pi-sitemap',
      title: 'Visualization of GitHub Actions pipelines',
      description: 'See how your CI/CD pipelines connect and interact with a clear, visual diagram.',
    },
    {
      icon: 'pi pi-lock',
      title: 'Environment Locking/Unlocking',
      description: 'Manage environment availability to prevent conflicts during critical deploys.',
    },
    {
      icon: 'pi pi-send',
      title: 'Deployment Management',
      description: 'Track and control deployments through a user-friendly interface.',
    },
    {
      icon: 'pi pi-chart-bar',
      title: 'Test Analytics (Coming Soon)',
      description: 'Detailed analytics on test results, coverage, and performance trends.',
    },
  ];

  ngOnInit(): void {
    fetch('https://api.github.com/repos/ls1intum/Helios/contributors')
      .then(response => response.json())
      .then((data: GitHubContributorInfo[]) => {
        this.githubContributors.set(
          data.map(contributor => ({
            id: contributor.id,
            login: contributor.login,
            avatarUrl: contributor.avatar_url,
            name: contributor.login,
            htmlUrl: contributor.html_url,
            contributions: contributor.contributions,
          }))
        );
      })
      .catch(error => console.error('Error fetching contributors:', error));
  }

  getGithubProfilePictureUrl(userId: number): string {
    return `https://avatars.githubusercontent.com/u/${userId}`;
  }

  getGithubProfileUrl(handle: string): string {
    return `https://github.com/${handle}`;
  }
}
