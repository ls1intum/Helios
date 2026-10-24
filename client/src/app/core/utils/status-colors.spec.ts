import { getStatusColors, getStatusIconClasses, getStatusKey } from './status-colors';

describe('workflow status presentation', () => {
  it.each([
    ['SUCCESS', undefined, 'success'],
    ['failure', undefined, 'failure'],
    ['STARTUP_FAILURE', undefined, 'failure'],
    ['TIMED_OUT', undefined, 'failure'],
    ['ACTION_REQUIRED', undefined, 'warning'],
    [undefined, 'ACTION_REQUIRED', 'warning'],
    ['CANCELLED', undefined, 'neutral'],
    ['SKIPPED', undefined, 'neutral'],
    [undefined, 'IN_PROGRESS', 'in_progress'],
    [undefined, 'QUEUED', 'in_progress'],
    [undefined, 'WAITING', 'in_progress'],
    [undefined, 'PENDING', 'in_progress'],
    [undefined, 'REQUESTED', 'in_progress'],
    [null, null, 'unknown'],
    ['UNRECOGNIZED', 'COMPLETED', 'unknown'],
  ] as const)('presents conclusion %s and status %s as %s', (conclusion, status, expected) => {
    expect(getStatusKey(conclusion, status)).toBe(expected);
  });

  it('uses distinct success, failure and action-required colors in light and dark mode', () => {
    expect(getStatusColors('SUCCESS').icon).toContain('text-green-600');
    expect(getStatusColors('SUCCESS').icon).toContain('dark:text-green-400');
    expect(getStatusColors('FAILURE').badge).toContain('bg-red-100');
    expect(getStatusColors('ACTION_REQUIRED').indicator).toContain('bg-orange-500');
    expect(getStatusColors().badge).toBe('text-muted-color');
  });

  it('animates running jobs but keeps queued and completed status icons still', () => {
    expect(getStatusIconClasses(undefined, 'IN_PROGRESS')).toContain('animate-spin');
    expect(getStatusIconClasses(undefined, 'in_progress')).toContain('animate-spin');
    expect(getStatusIconClasses(undefined, 'QUEUED')).not.toContain('animate-spin');
    expect(getStatusIconClasses('SUCCESS', 'COMPLETED')).not.toContain('animate-spin');
    expect(getStatusIconClasses()).toBe('text-muted-color');
  });
});
