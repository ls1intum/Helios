import { CommonModule } from '@angular/common';
import { Component, Input, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { IconsModule } from 'icons.module';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { HeliosIconComponent } from '../../helios-icon/helios-icon.component';
import { DividerModule } from 'primeng/divider';
import { AvatarModule } from 'primeng/avatar';
import { ToastModule } from 'primeng/toast';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, ToastModule, RouterLinkActive, IconsModule, ButtonModule, CommonModule, TooltipModule, HeliosIconComponent, DividerModule, AvatarModule],
  template: `
        <main>
            <div class="flex h-screen py-3 pl-3 w-screen">
                <div class="rounded-2xl p-5 mr-3 bg-slate-100 flex flex-col items-center justify-between h-full">
                    <app-helios-icon routerLink="/" size="3rem" class="rounded-xl w-12 cursor-pointer" />
                    <p-divider />
                    <div class="text-center">
                        <p-avatar [label]="getInitial(repoName)" [pTooltip]="repoName" size="large" />
                    </div>
                    <p-divider />
                    <div class="flex flex-col gap-3">
                        @for (item of items; track item.label) {
                            <a
                                class="rounded-xl p-2 flex items-center text-slate-500 hover:text-slate-700"
                                [routerLink]="getRouterLink(item)"
                                [routerLinkActive]="'!bg-slate-800 !text-slate-100 !hover:text-slate-100'"
                                [pTooltip]="item.label"
                                [routerLinkActiveOptions]="{exact: item.path === ''}"
                            >
                                <i-tabler [name]="item.icon" class="!size-10 !stroke-1" />
                            </a>
                        }
                    </div>
                    <span class="flex-grow"></span>
                    <i-tabler name="settings" pTooltip="Settings" class="!size-10 text-slate-500 hover:text-slate-700 !stroke-1" />
                    <p-divider />
                    <p-avatar label="U" size="large" />
                </div>
                <div class="flex-grow">
                    <router-outlet />
                </div>
                <p-toast></p-toast>
            </div>
        </main>
    `
})
export class MainLayoutComponent implements OnInit {
  @Input()
  set orgName(value: string) {
    this._orgName = value;
    this.updateBaseUrl();
  }
  get orgName(): string {
    return this._orgName;
  }

  @Input()
  set repoName(value: string) {
    this._repoName = value;
    this.updateBaseUrl();
  }
  get repoName(): string {
    return this._repoName;
  }

  private _orgName: string = '';
  private _repoName: string = '';
  baseUrl: string = '';
  items!: { label: string; icon: string; path: string }[];

  ngOnInit() {
    this.items = [
      {
        label: 'CI/CD',
        icon: 'arrow-guide',
        path: '',
      },
      {
        label: 'Release Management',
        icon: 'rocket',
        path: 'release',
      },
      {
        label: 'Environments',
        icon: 'server-cog',
        path: 'environments',
      },
    ];
  }

  private updateBaseUrl() {
    this.baseUrl = `/${this._orgName}/${this._repoName}`;
  }

  getRouterLink(item: { path: string }): string[] {
    return item.path ? [this.baseUrl, item.path] : [this.baseUrl];
  }


  getInitial(name: string): string {
    return name ? name.charAt(0).toUpperCase() : 'A';
  }
}