import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PullRequestListComponent } from './pull-request-list.component';

describe('PullRequestListComponent', () => {
  let component: PullRequestListComponent;
  let fixture: ComponentFixture<PullRequestListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PullRequestListComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PullRequestListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
