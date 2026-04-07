import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PermissionService } from '../services/permission.service';
import { writePermissionGuard } from './write-permission.guard';

describe('writePermissionGuard', () => {
  const navigate = vi.fn();
  const hasWritePermission = vi.fn();

  beforeEach(() => {
    navigate.mockReset();
    hasWritePermission.mockReset();

    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), { provide: Router, useValue: { navigate } }, { provide: PermissionService, useValue: { hasWritePermission } }],
    });
  });

  it('allows navigation when the user has write permission', () => {
    hasWritePermission.mockReturnValue(true);

    const result = TestBed.runInInjectionContext(() => writePermissionGuard({} as never, {} as never));

    expect(result).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('redirects to unauthorized when the user lacks write permission', () => {
    hasWritePermission.mockReturnValue(false);

    const result = TestBed.runInInjectionContext(() => writePermissionGuard({} as never, {} as never));

    expect(result).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/unauthorized']);
  });
});
