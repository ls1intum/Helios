const STATUS_COLORS = {
  success: {
    badge: 'text-green-700 bg-green-100 dark:text-green-300 dark:bg-green-900/30',
    indicator: 'bg-green-500 dark:bg-green-400',
    icon: 'text-green-600 dark:text-green-400',
  },
  failure: { badge: 'text-red-700 bg-red-100 dark:text-red-300 dark:bg-red-900/30', indicator: 'bg-red-500 dark:bg-red-400', icon: 'text-red-600 dark:text-red-400' },
  warning: {
    badge: 'text-orange-700 bg-orange-50 dark:text-orange-300 dark:bg-orange-900/30',
    indicator: 'bg-orange-500 dark:bg-orange-400',
    icon: 'text-orange-600 dark:text-orange-400',
  },
  in_progress: {
    badge: 'text-primary-700 bg-primary-50 dark:text-primary-200 dark:bg-primary-900/30',
    indicator: 'bg-primary-500 dark:bg-primary-300',
    icon: 'text-yellow-500 dark:text-yellow-400',
  },
  neutral: {
    badge: 'text-surface-700 bg-surface-100 dark:text-surface-300 dark:bg-surface-800',
    indicator: 'bg-surface-400 dark:bg-surface-500',
    icon: 'text-surface-500 dark:text-surface-400',
  },
  unknown: { badge: 'text-muted-color', indicator: 'bg-surface-400 dark:bg-surface-500', icon: 'text-muted-color' },
} as const;

type StatusKey = keyof typeof STATUS_COLORS;

function normalize(value?: string | null) {
  return value?.toLowerCase();
}

export function getStatusKey(conclusion?: string | null, status?: string | null): StatusKey {
  const normalizedConclusion = normalize(conclusion);
  const normalizedStatus = normalize(status);

  if (normalizedConclusion === 'success') return 'success';
  if (normalizedConclusion === 'failure' || normalizedConclusion === 'startup_failure' || normalizedConclusion === 'timed_out') return 'failure';
  if (normalizedConclusion === 'action_required' || normalizedStatus === 'action_required') return 'warning';
  if (normalizedConclusion === 'cancelled' || normalizedConclusion === 'skipped') return 'neutral';
  if (normalizedStatus === 'in_progress') return 'in_progress';
  if (normalizedStatus === 'queued' || normalizedStatus === 'waiting' || normalizedStatus === 'pending' || normalizedStatus === 'requested') return 'neutral';
  return 'unknown';
}

export function getStatusColors(conclusion?: string | null, status?: string | null) {
  return STATUS_COLORS[getStatusKey(conclusion, status)];
}

export function getStatusIconClasses(conclusion?: string | null, status?: string | null) {
  const { icon } = getStatusColors(conclusion, status);
  return normalize(status) === 'in_progress' ? `${icon} animate-spin` : icon;
}
