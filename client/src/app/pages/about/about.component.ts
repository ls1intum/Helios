import { Component } from '@angular/core';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';
import { NgOptimizedImage } from '@angular/common';

@Component({
  selector: 'app-about',
  imports: [PageHeadingComponent, NgOptimizedImage],
  templateUrl: './about.component.html',
})
export class AboutComponent {
  projectAdvisors = [
    {
      name: 'Stephan Krusche',
      description: 'Project Advisor',
      website: 'https://ase.cit.tum.de/people/krusche/',
      githubHandle: 'krusche',
      githubId: 744067,
    }
  ];

  contributors = [
    { name: 'Galiiabanu Bakirova', description: "Master's Thesis", website: null, githubHandle: 'gbanu', githubId: 56745022 },
    { name: 'Ege Kocabaş', description: "Master's Thesis", website: null, githubHandle: 'egekocabas', githubId: 48245934 },
    { name: 'Turker Koç', description: "Master's Thesis", website: null, githubHandle: 'TurkerKoc', githubId: 39654393 },
    { name: 'Paul Thiel', description: 'Bachelor Thesis', website: null, githubHandle: 'thielpa', githubId: 10695477 },
    { name: 'Stefan Németh', description: 'Bachelor Thesis', website: null, githubHandle: 'StefanNemeth', githubId: 5003405 },
  ];

  getGithubProfilePictureUrl(userId: number): string {
    return `https://avatars.githubusercontent.com/u/${userId}`;
  }

  getGithubProfileUrl(handle: string): string {
    return `https://github.com/${handle}`;
  }
}
