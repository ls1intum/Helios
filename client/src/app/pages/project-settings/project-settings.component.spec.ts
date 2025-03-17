import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProjectSettingsComponent } from './project-settings.component';
import { importProvidersFrom } from '@angular/core';
import { TestModule } from '@app/test.module';

describe('Integration Test Pull Request Project Settings Page', () => {
  let component: ProjectSettingsComponent;
  let fixture: ComponentFixture<ProjectSettingsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProjectSettingsComponent],
      providers: [importProvidersFrom(TestModule)],
    }).compileComponents();

    fixture = TestBed.createComponent(ProjectSettingsComponent);
    component = fixture.componentInstance;

    // Set input properties
    fixture.componentRef.setInput('repositoryId', 1);

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
