import { TestBed } from '@angular/core/testing';
import { CanActivateFn } from '@angular/router';

import { maintainerGuard } from './maintainer.guard';

describe('maintainerGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) => TestBed.runInInjectionContext(() => maintainerGuard(...guardParameters));

  beforeEach(() => {
    TestBed.configureTestingModule({});
  });

  it('should be created', () => {
    expect(executeGuard).toBeTruthy();
  });
});
