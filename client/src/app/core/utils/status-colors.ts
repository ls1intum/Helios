const STATUS_COLORS = {
  success:     { badge: 'text-green-600 bg-green-100 dark:text-green-400 dark:bg-green-900/30',    indicator: 'bg-green-500 dark:bg-green-400',   icon: 'text-green-600 dark:text-green-400'   },
  failure:     { badge: 'text-red-600 bg-red-100 dark:text-red-400 dark:bg-red-900/30',            indicator: 'bg-red-500 dark:bg-red-400',        icon: 'text-red-600 dark:text-red-400'       },
  cancelled:   { badge: 'text-orange-600 bg-orange-50 dark:text-orange-400 dark:bg-orange-900/30', indicator: 'bg-orange-500 dark:bg-orange-400',  icon: 'text-orange-600 dark:text-orange-400' },
  in_progress: { badge: 'text-blue-600 bg-blue-50 dark:text-blue-400 dark:bg-blue-900/30',         indicator: 'bg-blue-500 dark:bg-blue-400',      icon: 'text-blue-600 dark:text-blue-400'     },
  skipped:     { badge: 'text-gray-600 bg-gray-100 dark:text-gray-400 dark:bg-gray-900',           indicator: 'bg-gray-400 dark:bg-gray-500',      icon: 'text-gray-600 dark:text-gray-400'     },
  queued:      { badge: 'text-gray-600 bg-gray-100 dark:text-gray-400 dark:bg-gray-900',           indicator: 'bg-gray-300 dark:bg-gray-600',      icon: 'text-gray-600 dark:text-gray-400'     },
  unknown:     { badge: 'text-gray-600 dark:text-gray-400',                                        indicator: 'bg-gray-300 dark:bg-gray-600',      icon: 'text-gray-600 dark:text-gray-400'     },
} as const;

type StatusKey = keyof typeof STATUS_COLORS;

function resolveKey(conclusion?: string | null, status?: string | null): StatusKey {
  if (conclusion === 'success')   return 'success';
  if (conclusion === 'failure')   return 'failure';
  if (conclusion === 'skipped')   return 'skipped';
  if (conclusion === 'cancelled') return 'cancelled';
  if (status === 'in_progress')   return 'in_progress';
  if (status === 'queued' || status === 'waiting') return 'queued';
  return 'unknown';
}

export function getStatusColors(conclusion?: string | null, status?: string | null) {
  return STATUS_COLORS[resolveKey(conclusion, status)];
}
