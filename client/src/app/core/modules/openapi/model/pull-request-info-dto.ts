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
import { UserInfoDTO } from './user-info-dto';


export interface PullRequestInfoDTO { 
    id: number;
    number: number;
    title: string;
    state: PullRequestInfoDTO.StateEnum;
    isDraft: boolean;
    isMerged: boolean;
    commentsCount: number;
    author?: UserInfoDTO;
    assignees?: Array<UserInfoDTO>;
    repository?: RepositoryInfoDTO;
    additions: number;
    deletions: number;
    headSha: string;
    headRefName: string;
    headRefRepoNameWithOwner: string;
    mergedAt?: string;
    closedAt?: string;
    htmlUrl: string;
    createdAt?: string;
    updatedAt?: string;
}
export namespace PullRequestInfoDTO {
    export type StateEnum = 'OPEN' | 'CLOSED';
    export const StateEnum = {
        Open: 'OPEN' as StateEnum,
        Closed: 'CLOSED' as StateEnum
    };
}


