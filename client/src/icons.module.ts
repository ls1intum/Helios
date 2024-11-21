import { NgModule } from '@angular/core';

import { TablerIconsModule } from 'angular-tabler-icons';
import { IconArrowGuide, IconHeart, IconRocket, IconServerCog, IconSettings, IconSun, IconTimelineEventX } from 'angular-tabler-icons/icons';

// Select some icons (use an object, not an array)
const icons = {
  IconHeart,
  IconServerCog,
  IconSun,
  IconSettings,
  IconRocket,
  IconArrowGuide,
};

@NgModule({
  imports: [TablerIconsModule.pick(icons)],
  exports: [TablerIconsModule],
})
export class IconsModule {}
