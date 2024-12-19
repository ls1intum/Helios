import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EnvironmentDeploymentInfoComponent } from './environment-deployment-info.component';

describe('EnvironmentDeploymentInfoComponent', () => {
  let component: EnvironmentDeploymentInfoComponent;
  let fixture: ComponentFixture<EnvironmentDeploymentInfoComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EnvironmentDeploymentInfoComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(EnvironmentDeploymentInfoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
