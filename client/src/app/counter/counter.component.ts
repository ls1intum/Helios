import { Component, signal, computed } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { IconsModule } from '../../icons.module';

@Component({
  selector: 'app-counter',
  standalone: true,
  imports: [ButtonModule, IconsModule],
  template: `
    <h2 class="text-3xl mt-2">Counter: {{ count() }}</h2>
    <div class="flex gap-4">
      <p-button (click)="increment()">Increment</p-button>
      <p-button (click)="decrement()">Decrement</p-button>
      <i-tabler name="heart" class="text-red-500" />
    </div>
  `,
})
export class CounterComponent {
  // Using Angular's signal for the count
  count = signal(0);

  // Computed property, if you want to perform any derived calculations
  isEven = computed(() => this.count() % 2 === 0);

  increment() {
    // Update the signal without triggering zone-based change detection
    this.count.set(this.count() + 1);
  }

  decrement() {
    this.count.set(this.count() - 1);
  }
}
