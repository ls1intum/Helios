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


export interface DeploymentDTO { 
    id: number;
    repository?: RepositoryInfoDTO;
    url: string;
    statusesUrl: string;
    sha: string;
    ref: string;
    task: string;
    environment: string;
    createdAt?: string;
    updatedAt?: string;
}

