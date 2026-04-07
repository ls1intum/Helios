import { booleanAttribute, Component, input } from '@angular/core';
import { TooltipModule } from 'primeng/tooltip';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconBrandGithub } from 'angular-tabler-icons/icons';

@Component({
  selector: 'app-github-link-button',
  standalone: true,
  imports: [TooltipModule, TablerIconComponent],
  styles: `
    .app-github-link-button {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      position: relative;
      z-index: 2;
      border: 0;
      border-radius: 0.375rem;
      background: transparent;
      min-width: 2rem;
      min-height: 2rem;
      padding: 0.5rem;
      color: var(--p-surface-700);
      cursor: pointer;
      pointer-events: auto;
      transition:
        background-color 150ms ease,
        color 150ms ease;
    }

    .app-github-link-button:hover {
      background-color: var(--p-surface-200);
    }

    .app-github-link-button:focus-visible {
      outline: 2px solid var(--p-primary-400);
      outline-offset: 2px;
    }

    .app-github-link-button:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }

    .app-github-link-button-label {
      padding-inline: 0.25rem;
    }

    :host-context(.dark-mode-enabled) .app-github-link-button {
      color: var(--p-primary-300);
    }

    :host-context(.dark-mode-enabled) .app-github-link-button:hover {
      background-color: color-mix(in srgb, var(--p-primary-500) 18%, transparent);
    }
  `,
  providers: [
    provideTablerIcons({
      IconBrandGithub,
    }),
  ],
  templateUrl: './github-link-button.component.html',
})
export class GithubLinkButtonComponent {
  private readonly defaultButtonClass = 'app-github-link-button';

  url = input<string | null | undefined>(undefined);
  label = input<string | undefined>(undefined);
  tooltip = input<string | undefined>(undefined);
  tooltipPosition = input<'top' | 'bottom' | 'left' | 'right'>('top');
  disabled = input(false, { transform: booleanAttribute });
  stopPropagation = input(true, { transform: booleanAttribute });
  buttonClass = input<string | undefined>(undefined);
  iconClass = input('!size-4');

  resolvedButtonClass(): string {
    const baseClasses = [this.defaultButtonClass];

    const customClasses = this.buttonClass();
    if (customClasses) {
      baseClasses.push(customClasses);
    }

    return baseClasses.join(' ');
  }

  open(event: Event): void {
    if (this.stopPropagation()) {
      event.stopPropagation();
    }

    const url = this.url();
    if (!url || this.disabled()) {
      return;
    }

    window.open(url, '_blank');
  }
}
