import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BranchTableComponent } from './branches-table.component';
import { provideExperimentalZonelessChangeDetection } from '@angular/core';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';

describe('BranchTableComponent', () => {
  let component: BranchTableComponent;
  let fixture: ComponentFixture<BranchTableComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BranchTableComponent],
      providers: [provideExperimentalZonelessChangeDetection(), provideQueryClient(new QueryClient())],
    }).compileComponents();

    fixture = TestBed.createComponent(BranchTableComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
