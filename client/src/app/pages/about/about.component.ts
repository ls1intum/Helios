import { Component } from '@angular/core';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';
import { NgOptimizedImage } from '@angular/common';

@Component({
  selector: 'app-about',
  imports: [PageHeadingComponent, NgOptimizedImage],
  templateUrl: './about.component.html',
})
export class AboutComponent {
  contributors = [
    { name: 'Galiiabanu Bakirova', description: "Master's Thesis", photo: 'bakirova.jpeg', github: 'gbanu' },
    { name: 'Ege Kocabaş', description: "Master's Thesis", photo: 'kocabas.jpeg', github: 'egekocabas' },
    { name: 'Turker Koç', description: "Master's Thesis", photo: 'koc.jpeg', github: 'TurkerKoc' },
    { name: 'Paul Thiel', description: 'Bachelor Thesis', photo: 'thiel.jpeg', github: 'thielpa' },
    { name: 'Stefan Németh', description: 'Bachelor Thesis', photo: 'nemeth.jpeg', github: 'StefanNemeth' },
  ];
}
