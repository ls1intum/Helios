import { NgModule } from '@angular/core';

import { TablerIconsModule } from 'angular-tabler-icons';
import {
  IconArrowGuide,
  IconHeart,
  IconRocket,
  IconServerCog,
  IconSettings,
  IconSun,
  IconCalendar,
  IconCheck,
  IconExternalLink,
  IconGitBranch,
  IconGitCommit,
  IconHistory,
  IconLockOpen,
  IconPencil,
  IconUser,
  IconBrandGithub,
  IconCircleCheck,
  IconProgress,
  IconReload,
  IconProgressHelp,
  IconCircleX,
  IconCloudUpload,
} from 'angular-tabler-icons/icons';

// Select some icons (use an object, not an array)
const icons = {
  IconHeart,
  IconServerCog,
  IconSun,
  IconSettings,
  IconRocket,
  IconArrowGuide,
  IconCheck,
  IconCalendar,
  IconUser,
  IconGitCommit,
  IconGitBranch,
  IconLockOpen,
  IconExternalLink,
  IconPencil,
  IconHistory,
  IconBrandGithub,
  IconCircleCheck,
  IconProgress,
  IconReload,
  IconProgressHelp,
  IconCircleX,
  IconCloudUpload,
};

@NgModule({
  imports: [TablerIconsModule.pick(icons)],
  exports: [TablerIconsModule],
})
export class IconsModule {}
