import { Component, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';

import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { InputNumberModule } from 'primeng/inputnumber';
import { DividerModule } from 'primeng/divider';
import { IftaLabelModule } from 'primeng/iftalabel';

@Component({
  selector: 'app-locking-thresholds',
  standalone: true,
  imports: [ToggleSwitchModule, InputNumberModule, DividerModule, IftaLabelModule, FormsModule, ButtonModule],
  templateUrl: './locking-thresholds.component.html',
})
export class LockingThresholdsComponent {
  // Add these to your component class
  isLockExpirationEnabled = false;
  isLockReservationEnabled = false;

  // Initialize with -1 (disabled state)
  lockingExpirationThreshold: number | undefined;
  lockingReservationThreshold: number | undefined;

  environmentId = input<string | undefined>();

  // Add these methods
  onThresholdToggle(type: 'expiration' | 'reservation') {
    if (type === 'expiration') {
      if (!this.isLockExpirationEnabled) {
        this.lockingExpirationThreshold = undefined;
      } else {
        // Set default value when enabling
        this.lockingExpirationThreshold = this.lockingExpirationThreshold === undefined ? 3600 : this.lockingExpirationThreshold;
      }
    } else {
      if (!this.isLockReservationEnabled) {
        this.lockingReservationThreshold = undefined;
      } else {
        // Set default value when enabling
        this.lockingReservationThreshold = this.lockingReservationThreshold === undefined ? 900 : this.lockingReservationThreshold;
      }
    }
  }

  // Update your existing updateLockingThresholds method
  updateLockingThresholds() {
    // Here you would typically send the values to your backend
    console.log('Saving thresholds:', {
      expiration: this.lockingExpirationThreshold,
      reservation: this.lockingReservationThreshold,
    });

    // Add your API call here
  }
}
