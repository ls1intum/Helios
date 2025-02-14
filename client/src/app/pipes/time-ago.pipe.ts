import { Pipe, PipeTransform } from '@angular/core';

interface TimeAgoPipeOptions {
  showSeconds?: boolean;
  referenceDate?: Date;
}

@Pipe({
  name: 'timeAgo',
})
export class TimeAgoPipe implements PipeTransform {
  transform(value: string, options?: TimeAgoPipeOptions): string {
    const date = new Date(value);
    const now = options?.referenceDate || new Date();
    const diff = Math.abs(now.getTime() - date.getTime());
    let seconds = Math.round(diff / 1000);
    let minutes = Math.round(seconds / 60);
    let hours = Math.round(minutes / 60);
    let days = Math.round(hours / 24);
    let months = Math.round(days / 30);
    let years = Math.round(days / 365);
    if (Number.isNaN(seconds)) {
      return '';
    } else if (seconds < 60) {
      if (options?.showSeconds) {
        return seconds + ` second${seconds === 1 ? '' : 's'} ago`;
      }
      return 'a few seconds ago';
    } else if (seconds < 120) {
      return 'a minute ago';
    } else if (minutes < 60) {
      return minutes + ' minutes ago';
    } else if (minutes < 120) {
      return 'an hour ago';
    } else if (hours < 24) {
      return hours + ' hours ago';
    } else if (hours < 48) {
      return 'a day ago';
    } else if (days < 30) {
      return days + ' days ago';
    } else if (days < 60) {
      return 'a month ago';
    } else if (days < 365) {
      return months + ' months ago';
    } else if (days < 730) {
      return 'a year ago';
    } else {
      return years + ' years ago';
    }
  }
}
