/**
 * Request header that tells the global error interceptor (see `AppComponent`) not to show its
 * generic "Error <status>" toast for this call. Set it when the caller shows its own, more
 * specific message — otherwise the user sees two alerts for one failure.
 */
export const HANDLED_LOCALLY_HEADER = 'X-Helios-Handled-Locally';

/** Headers to pass to a generated SDK call whose errors the caller reports itself. */
export const handledLocallyHeaders = { [HANDLED_LOCALLY_HEADER]: 'true' } as const;
