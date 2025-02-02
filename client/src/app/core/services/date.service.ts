import { DatePipe } from '@angular/common';
import { inject, Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class DateService {
  private datePipe = inject(DatePipe);

  formatDate = (date?: Date | string | number, formatString: string = 'd. MMMM y'): string | null => {
    return date ? this.datePipe.transform(date, formatString) : null;
  };

  timeSinceLocked(lockedAt: string | undefined, timeNow: Date): string {
    if (!lockedAt) return '';

    const lockedDate = new Date(lockedAt);
    const now = timeNow;
    const diffMs = now.getTime() - lockedDate.getTime();

    // If lockedAt is in the future, return empty or handle differently
    if (diffMs < 0) {
      return '';
    }

    const totalSeconds = Math.floor(diffMs / 1000);
    const days = Math.floor(totalSeconds / 86400); // 1 day = 86400s
    const hours = Math.floor((totalSeconds % 86400) / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    // Zero-pad hours, minutes, seconds
    const padTwo = (num: number) => num.toString().padStart(2, '0');

    if (days > 0) {
      // Format: dd:hh:mm (e.g., "1:05:12" => 1 day, 5 hours, 12 minutes)
      const dd = days.toString(); // or padTwo(days) if you prefer "01" for 1 day
      const hh = padTwo(hours);
      const mm = padTwo(minutes);
      return `${dd}:${hh}:${mm}`;
    } else {
      // Format: hh:mm:ss (e.g., "05:12:36")
      const hh = padTwo(hours);
      const mm = padTwo(minutes);
      const ss = padTwo(seconds);
      return `${hh}:${mm}:${ss}`;
    }
  }
}
