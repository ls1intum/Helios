import { NgModule } from '@angular/core';

import { TablerIconsModule } from 'angular-tabler-icons';
import { IconHeart } from 'angular-tabler-icons/icons';

// Select some icons (use an object, not an array)
const icons = {
  IconHeart,
};

@NgModule({
  imports: [TablerIconsModule.pick(icons)],
  exports: [TablerIconsModule],
})
export class IconsModule {}
