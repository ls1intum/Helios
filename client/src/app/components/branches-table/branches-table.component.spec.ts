import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BranchTableComponent } from './branches-table.component';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { describe, it, expect, beforeEach } from 'vitest';
import { MessageService } from 'primeng/api';

describe('BranchTableComponent', () => {
  let component: BranchTableComponent;
  let fixture: ComponentFixture<BranchTableComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BranchTableComponent],
      providers: [provideZonelessChangeDetection(), provideQueryClient(new QueryClient()), MessageService],
    }).compileComponents();

    fixture = TestBed.createComponent(BranchTableComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('sort signals initialization', () => {
    it('should default sortField to updatedAt', () => {
      expect(component.sortField()).toBe('updatedAt');
    });

    it('should default sortDirection to desc', () => {
      expect(component.sortDirection()).toBe('desc');
    });
  });

  describe('onSort (TreeTable sort event handler)', () => {
    it('should update sortField from sort event', () => {
      component.onSort({ field: 'name', order: 1 });

      expect(component.sortField()).toBe('name');
    });

    it('should set sortDirection to asc when order is 1', () => {
      component.onSort({ field: 'updatedAt', order: 1 });

      expect(component.sortDirection()).toBe('asc');
    });

    it('should set sortDirection to desc when order is -1', () => {
      component.onSort({ field: 'updatedAt', order: -1 });

      expect(component.sortDirection()).toBe('desc');
    });

    it('should not update sortField when field is undefined', () => {
      component.sortField.set('updatedAt');

      component.onSort({ order: 1 });

      expect(component.sortField()).toBe('updatedAt');
    });

    it('should not update sortDirection when order is undefined', () => {
      component.sortDirection.set('desc');

      component.onSort({ field: 'name' });

      expect(component.sortDirection()).toBe('desc');
    });
  });

  describe('queryOptions', () => {
    it('should include sortField in query options', () => {
      component.sortField.set('name');

      const options = component.queryOptions();

      expect(options.queryKey).toContainEqual(
        expect.objectContaining({
          query: expect.objectContaining({ sortField: 'name' }),
        })
      );
    });

    it('should include sortDirection in query options', () => {
      component.sortDirection.set('asc');

      const options = component.queryOptions();

      expect(options.queryKey).toContainEqual(
        expect.objectContaining({
          query: expect.objectContaining({ sortDirection: 'asc' }),
        })
      );
    });

    it('should reflect updated signals in query key', () => {
      component.sortField.set('updatedAt');
      component.sortDirection.set('desc');
      const optionsBefore = component.queryOptions();

      component.onSort({ field: 'name', order: 1 });
      const optionsAfter = component.queryOptions();

      expect(optionsBefore.queryKey).not.toEqual(optionsAfter.queryKey);
    });
  });
});
