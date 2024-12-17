import { Component, input } from "@angular/core";
import { IconsModule } from "icons.module";
import { TagModule } from "primeng/tag";

@Component({
  selector: 'app-deployment-state-tag',
  imports: [TagModule, IconsModule],
  providers: [],
  templateUrl: './deployment-state-tag.component.html',
})
export class DeploymentStateTagComponent {
  state = input.required<string>();
}
