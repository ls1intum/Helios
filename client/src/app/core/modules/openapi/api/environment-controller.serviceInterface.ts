/**
 * Helios API
 *
 * Contact: turker.koc@tum.de
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */
import { HttpHeaders }                                       from '@angular/common/http';

import { Observable }                                        from 'rxjs';

import { EnvironmentDTO } from '../model/models';


import { Configuration }                                     from '../configuration';



export interface EnvironmentControllerServiceInterface {
    defaultHeaders: HttpHeaders;
    configuration: Configuration;

    /**
     * 
     * 
     */
    getAllEnvironments(extraHttpRequestParams?: any): Observable<Array<EnvironmentDTO>>;

    /**
     * 
     * 
     * @param id 
     */
    getEnvironmentById(id: number, extraHttpRequestParams?: any): Observable<EnvironmentDTO>;

    /**
     * 
     * 
     * @param repositoryId 
     */
    getEnvironmentsByRepositoryId(repositoryId: number, extraHttpRequestParams?: any): Observable<Array<EnvironmentDTO>>;

    /**
     * 
     * 
     * @param id 
     * @param environmentDTO 
     */
    updateEnvironment(id: number, environmentDTO: EnvironmentDTO, extraHttpRequestParams?: any): Observable<EnvironmentDTO>;

}
