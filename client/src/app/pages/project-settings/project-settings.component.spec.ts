import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProjectSettingsComponent } from './project-settings.component';
import { CUSTOM_ELEMENTS_SCHEMA, provideExperimentalZonelessChangeDetection } from '@angular/core';
import { ConfirmationService, MessageService } from 'primeng/api';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { FormsModule } from '@angular/forms';
import { vi } from 'vitest';

describe('ProjectSettingsComponent', () => {
  let component: ProjectSettingsComponent;
  let fixture: ComponentFixture<ProjectSettingsComponent>;

  beforeEach(async () => {
    vi.mock('primeng/api');

    await TestBed.configureTestingModule({
      imports: [ProjectSettingsComponent],
      providers: [provideExperimentalZonelessChangeDetection(), MessageService, ConfirmationService, provideQueryClient(new QueryClient())],
    })
      .overrideComponent(ProjectSettingsComponent, {
        set: { imports: [FormsModule], schemas: [CUSTOM_ELEMENTS_SCHEMA] },
      })
      .compileComponents();

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
