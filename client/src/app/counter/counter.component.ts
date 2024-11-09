import { Component, signal, computed } from '@angular/core';

@Component({
  selector: 'app-counter',
  template: `
    <div>
      <h2>Counter: {{ count() }}</h2>
      <button (click)="increment()">Increment</button>
      <button (click)="decrement()">Decrement</button>
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