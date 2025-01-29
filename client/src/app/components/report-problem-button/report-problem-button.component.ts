import { Component, signal } from '@angular/core';
import { IconsModule } from 'icons.module';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';

@Component({
  selector: 'app-report-problem-button',
  standalone: true,
  imports: [ButtonModule, TooltipModule, IconsModule],
  templateUrl: './report-problem-button.component.html',
})
export class ReportProblemButtonComponent {
  isExpanded = signal(false);

  reportIssue(): void {
    window.open('https://github.com/ls1intum/Helios/issues/new/choose', '_blank');
  }
}
