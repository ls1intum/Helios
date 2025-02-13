import { DatePipe } from '@angular/common';
import { inject, Injectable } from '@angular/core';
import { QueryClient } from '@tanstack/angular-query-experimental';
import {
  getAllEnabledEnvironmentsQueryKey,
  getAllEnvironmentsQueryKey,
  getEnvironmentsByUserLockingQueryKey,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
@Injectable({
  providedIn: 'root',
})
export class DateService {
  private datePipe = inject(DatePipe);
  private queryClient = inject(QueryClient);

  formatDate = (date?: Date | string | number, formatString: string = 'd. MMMM y'): string | null => {
    return date ? this.datePipe.transform(date, formatString) : null;
  };

  timeSinceLocked(lockedAt: string | undefined, timeNow: Date): string {
    if (!lockedAt) return '';

    const lockedDate = new Date(lockedAt);
    const now = timeNow;
    const diffMs = now.getTime() - lockedDate.getTime();

    if (diffMs < 0) {
      return '';
    }

    return this.formatTimeDiff(diffMs);
  }

  timeUntilExpire(timeNow: Date, timeWillExpire: string | undefined): string {
    if (!timeWillExpire) return '';

    const expireDate = new Date(timeWillExpire);
    const now = timeNow;
    const diffMs = expireDate.getTime() - now.getTime();

    if (diffMs <= 0) {
      // TODO move this to the component using service
      this.queryClient.invalidateQueries({ queryKey: getAllEnabledEnvironmentsQueryKey() });
      this.queryClient.invalidateQueries({ queryKey: getAllEnvironmentsQueryKey() });
      this.queryClient.invalidateQueries({ queryKey: getEnvironmentsByUserLockingQueryKey() });
      return '00:00:00'; // Already expired
    }

    return this.formatTimeDiff(diffMs);
  }

  private formatTimeDiff(diffMs: number): string {
    const totalSeconds = Math.floor(diffMs / 1000);
    const days = Math.floor(totalSeconds / 86400);
    const hours = Math.floor((totalSeconds % 86400) / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    const padTwo = (num: number) => num.toString().padStart(2, '0');

    if (days > 0) {
      return `${days}:${padTwo(hours)}:${padTwo(minutes)}`;
    } else {
      return `${padTwo(hours)}:${padTwo(minutes)}:${padTwo(seconds)}`;
    }
  }
}
