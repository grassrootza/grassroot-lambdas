import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from 'src/environments/environment';
import { map } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class AnalyticsService {

  private metricsUrl = environment.lambdaUrl + '/profile/counts';
  private topGroupsUrl = environment.lambdaUrl + '/profile/group/membership';
  private topUsersByPart = environment.lambdaUrl + '/profile/user/participation';
  private connectionsMetricsUrl = environment.lambdaUrl + '/connections/compareMetrics';

  private rankParams = new HttpParams().set('first_rank', '0').set('last_rank', '0');

  constructor(private httpClient: HttpClient) { }

  public getTotalMetrics() {
    return this.httpClient.get(this.metricsUrl);
  }

  public getTopGroupsByMembership() {
    return this.httpClient.get(this.topGroupsUrl, { params: this.rankParams });
  }

  public getTopUserByMembership() {
    return this.httpClient.get(this.topUsersByPart, { params: this.rankParams });
  }

  public getTopUserByPageRankCloseness() {
    const pageRankParams = this.rankParams.set('entity_type', 'ACTOR').set('sub_type', 'INDIVIDUAL')
      .set('depth', '3').set('count_entities', 'true');
    return this.httpClient.get(this.connectionsMetricsUrl, { params: pageRankParams });
  }

}
