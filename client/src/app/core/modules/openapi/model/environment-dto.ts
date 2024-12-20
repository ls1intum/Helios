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


export interface EnvironmentDTO { 
    repository?: RepositoryInfoDTO;
    id: number;
    name: string;
    url?: string;
    htmlUrl?: string;
    createdAt?: string;
    updatedAt?: string;
    installedApps?: Array<string>;
    description?: string;
    serverUrl?: string;
}

