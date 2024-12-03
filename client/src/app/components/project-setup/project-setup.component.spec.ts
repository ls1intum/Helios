import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProjectSetupComponent } from './project-setup.component';

describe('ProjectSetupComponent', () => {
  let component: ProjectSetupComponent;
  let fixture: ComponentFixture<ProjectSetupComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProjectSetupComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ProjectSetupComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
