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

import { BranchInfoDTO } from '../model/models';


import { Configuration }                                     from '../configuration';



export interface BranchControllerServiceInterface {
    defaultHeaders: HttpHeaders;
    configuration: Configuration;

    /**
     * 
     * 
     */
    getAllBranches(extraHttpRequestParams?: any): Observable<Array<BranchInfoDTO>>;

    /**
     * 
     * 
     * @param repoId 
     * @param name 
     */
    getBranchByRepositoryIdAndName(repoId: number, name: string, extraHttpRequestParams?: any): Observable<BranchInfoDTO>;

}
