import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class EnvironmentStateService {
  // Subject to emit environment update events
  private environmentUpdateSubject = new Subject<void>();

  // Observable for components to subscribe to environment update events
  environmentUpdate$ = this.environmentUpdateSubject.asObservable();

  /**
   * Triggers an environment update event.
   * Call this method after deploying or unlocking an environment.
   * So that other components can re-fetch the environment data.
   */
  triggerEnvironmentUpdate(): void {
    this.environmentUpdateSubject.next();
  }
}
