/**
 * Helios API
 *
 * Contact: turker.koc@tum.de
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */
import { RepositoryInfoDTO } from './repository-info-dto';


export interface WorkflowDTO { 
    id: number;
    repository?: RepositoryInfoDTO;
    name: string;
    path: string;
    fileNameWithExtension?: string;
    state: WorkflowDTO.StateEnum;
    url?: string;
    htmlUrl?: string;
    badgeUrl?: string;
    createdAt?: string;
    updatedAt?: string;
}
export namespace WorkflowDTO {
    export type StateEnum = 'ACTIVE' | 'DELETED' | 'DISABLED_FORK' | 'DISABLED_INACTIVITY' | 'DISABLED_MANUALLY' | 'UNKNOWN';
    export const StateEnum = {
        Active: 'ACTIVE' as StateEnum,
        Deleted: 'DELETED' as StateEnum,
        DisabledFork: 'DISABLED_FORK' as StateEnum,
        DisabledInactivity: 'DISABLED_INACTIVITY' as StateEnum,
        DisabledManually: 'DISABLED_MANUALLY' as StateEnum,
        Unknown: 'UNKNOWN' as StateEnum
    };
}


