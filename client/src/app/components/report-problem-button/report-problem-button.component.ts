import { Component, signal } from '@angular/core';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconBug } from 'angular-tabler-icons/icons';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';

@Component({
  selector: 'app-report-problem-button',
  standalone: true,
  imports: [ButtonModule, TooltipModule, TablerIconComponent],
  providers: [
    provideTablerIcons({
      IconBug,
    }),
  ],
  templateUrl: './report-problem-button.component.html',
})
export class ReportProblemButtonComponent {
  isExpanded = signal(false);

  reportIssue(): void {
    window.open('https://github.com/ls1intum/Helios/issues/new/choose', '_blank');
  }
}
