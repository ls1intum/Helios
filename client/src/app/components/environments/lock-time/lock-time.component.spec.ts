import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LockTimeComponent } from './lock-time.component';
import { provideExperimentalZonelessChangeDetection } from '@angular/core';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

describe('LockTimeComponent', () => {
  let component: LockTimeComponent;
  let fixture: ComponentFixture<LockTimeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LockTimeComponent],
      providers: [provideExperimentalZonelessChangeDetection(), provideQueryClient(new QueryClient()), provideNoopAnimations()],
    }).compileComponents();

    fixture = TestBed.createComponent(LockTimeComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
