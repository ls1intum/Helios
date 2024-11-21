export * from './pull-request-controller.service';
import { PullRequestControllerService } from './pull-request-controller.service';
export * from './pull-request-controller.serviceInterface';
export * from './status-controller.service';
import { StatusControllerService } from './status-controller.service';
export * from './status-controller.serviceInterface';
export const APIS = [PullRequestControllerService, StatusControllerService];
