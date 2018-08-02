import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from 'src/environments/environment';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class DocumentService {

  private addExtractUrl = environment.lambdaUrl + '/document/create/extract';

  private creationKeysForTransform = ['issues', 'procedures', 'problems'];

  constructor(private httpClient: HttpClient) { }

  addExtractToGraph(docParams: any): Observable<any> {
    console.log('adding extract to graph, params: ', docParams);
    this.creationKeysForTransform.forEach(key =>
      docParams[key] = this.transformToString(docParams[key]));
    console.log('issues transformed: ', docParams['issues']);
    let params = new HttpParams();
    Object.keys(docParams).forEach(key => params = params.set(key, docParams[key]));
    console.log('posting to: ', this.addExtractUrl);
    return this.httpClient.get(this.addExtractUrl, { params: params });
  }

  transformToString(paramCSV) {
    console.log('incoming param: ', paramCSV);
    const splitStrings = paramCSV.split(',');
    console.log('split: ', splitStrings);
    const trimmed = splitStrings.map(value => value.trim());
    console.log('trimmed: ', trimmed);
    const jsonString = JSON.stringify(trimmed);
    console.log('and made a string: ', jsonString);
    return jsonString;
  }
}
