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


export interface BranchInfoDTO { 
    name: string;
    commit_sha: string;
    repository?: RepositoryInfoDTO;
}

