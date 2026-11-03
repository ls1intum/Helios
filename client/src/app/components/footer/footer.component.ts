import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { version } from 'environments/version';
import { ButtonModule } from 'primeng/button';

@Component({
  selector: 'app-footer',
  imports: [RouterLink, ButtonModule],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './footer.component.html',
})
export class FooterComponent {
  deployed_commit_sha = version.deployed_commit_sha;
  deployed_version = version.deployed_version;
}
