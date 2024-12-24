import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EnvironmentDeploymentHistoryComponent } from './environment-deployment-history.component';

describe('EnvironmentDeploymentHistoryComponent', () => {
  let component: EnvironmentDeploymentHistoryComponent;
  let fixture: ComponentFixture<EnvironmentDeploymentHistoryComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EnvironmentDeploymentHistoryComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(EnvironmentDeploymentHistoryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
