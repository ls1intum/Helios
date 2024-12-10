import {CommonModule} from '@angular/common';
import {Component, input, Signal} from '@angular/core';
import {PipelineComponent} from '@app/components/pipeline/pipeline.component';
import {FetchEnvironmentService} from '@app/core/services/fetch/environment';
import {AccordionModule} from 'primeng/accordion';
import {RouterLink} from '@angular/router';
import {TagModule} from 'primeng/tag';
import {IconsModule} from 'icons.module';
import {ButtonModule} from 'primeng/button';
import {InputTextModule} from 'primeng/inputtext';
import {EnvironmentListComponent} from '@app/pages/environment-list/environment-list.component';

@Component({
  selector: 'app-pull-request-pipeline',
  imports: [InputTextModule, AccordionModule, CommonModule, RouterLink, TagModule, IconsModule, ButtonModule, PipelineComponent, EnvironmentListComponent],
  providers: [FetchEnvironmentService],
  templateUrl: './pull-request-pipeline.component.html',
})
export class PullRequestPipelineComponent {
  pullRequestId: Signal<number> = input.required();
}
