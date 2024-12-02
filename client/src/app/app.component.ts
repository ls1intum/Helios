import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { IconsModule } from 'icons.module';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { HeliosIconComponent } from './helios-icon/helios-icon.component';
import { DividerModule } from 'primeng/divider';
import { AvatarModule } from 'primeng/avatar';
import { ToastModule } from 'primeng/toast';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, ToastModule, RouterLinkActive, IconsModule, ButtonModule, CommonModule, TooltipModule, HeliosIconComponent, DividerModule, AvatarModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent implements OnInit {
  title = 'Helios';

  items!: { label: string; icon: string; path: string }[];

  ngOnInit() {
    this.items = [
      {
        label: 'CI/CD',
        icon: 'arrow-guide',
        path: '/ci-cd',
      },
      {
        label: 'Release Management',
        icon: 'rocket',
        path: '/release',
      },
      {
        label: 'Environments',
        icon: 'server-cog',
        path: '/environment/list',
      },
    ];
  }
}
