import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';
import * as Sentry from '@sentry/angular';
import { environment } from 'environments/environment';

Sentry.init({
  enabled: environment.sentry.enabled,
  // The DSN (Data Source Name) tells the SDK where to send the events to.
  dsn: environment.sentry.dsn,
  // The browser tracing integration captures performance data
  // like throughput and latency
  integrations: [Sentry.browserTracingIntegration(), Sentry.replayIntegration()],
  // Our sample rate for tracing is 20% in production
  tracesSampleRate: environment.production ? 0.2 : 1.0,
});

bootstrapApplication(AppComponent, appConfig).catch(err => console.error(err));
