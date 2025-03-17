import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EnvironmentDeploymentHistoryComponent } from './environment-deployment-history.component';
import { importProvidersFrom } from '@angular/core';
import { TestModule } from '@app/test.module';

describe('Integration Test Environment Deployment History Page', () => {
  let component: EnvironmentDeploymentHistoryComponent;
  let fixture: ComponentFixture<EnvironmentDeploymentHistoryComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EnvironmentDeploymentHistoryComponent],
      // Todo: figure out how to remove query client provider
      providers: [importProvidersFrom(TestModule)],
    }).compileComponents();

    fixture = TestBed.createComponent(EnvironmentDeploymentHistoryComponent);
    component = fixture.componentInstance;

    // Set input properties
    fixture.componentRef.setInput('repositoryId', 1);
    fixture.componentRef.setInput('environmentId', 1);

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
