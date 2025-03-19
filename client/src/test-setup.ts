import '@analogjs/vitest-angular/setup-snapshots';
import '@angular/compiler';

import { platformBrowserDynamicTesting } from '@angular/platform-browser-dynamic/testing';
import { getTestBed } from '@angular/core/testing';
import { NgModule, provideExperimentalZonelessChangeDetection } from '@angular/core';

@NgModule({
  providers: [provideExperimentalZonelessChangeDetection()],
})
export class ZonelessTestModule {}

getTestBed().initTestEnvironment(ZonelessTestModule, platformBrowserDynamicTesting());
