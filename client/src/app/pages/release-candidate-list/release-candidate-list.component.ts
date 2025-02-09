import { Component } from '@angular/core';
import { ReleaseCandidateTableComponent } from '@app/components/release-candidate-table/release-candidate-table.component';

@Component({
  selector: 'app-release-candidate-list',
  imports: [ReleaseCandidateTableComponent],
  templateUrl: './release-candidate-list.component.html',
})
export class ReleaseCanidateListComponent {}
