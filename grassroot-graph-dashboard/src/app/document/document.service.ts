import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs';
import { map, flatMap } from 'rxjs/operators';
import { transformDoc } from './document.model';

@Injectable({
  providedIn: 'root'
})
export class DocumentService {

  private listDocumentsUrl = environment.lambdaUrl + '/document/list';
  private queryDocumentsUrl = environment.lambdaUrl + '/document/query';

  private addDocumentUrl = environment.lambdaUrl + '/document/create';

  private creationKeysForTransform = ['issues', 'procedures', 'problems'];

  constructor(private httpClient: HttpClient) { }

  listDocuments(): Observable<Document[]> {
    return this.httpClient.get<any>(this.listDocumentsUrl).pipe(
      map(this.extractDocResults));
  }

  queryDocuments(queryText: string): Observable<Document[]> {
    const params = new HttpParams().set('query_text', queryText);
    return this.httpClient.get<any>(this.queryDocumentsUrl, { params: params })
      .pipe(map(this.extractDocResults));
  }

  extractDocResults = (result: any) => {
    console.log('Received from Neo4J: ', result);
    const records = result.records;
    const returnedDocs: Document[] = records.map(item => transformDoc(item._fields[0].properties));
    console.log('returned docs: ', returnedDocs);
    return returnedDocs;
  }

  addDocumentToGraph(docParams: any): Observable<any> {
    console.log('adding document to graph, params: ', docParams);
    this.creationKeysForTransform.forEach(key =>
      docParams[key] = this.transformToString(docParams[key]));
    console.log('issues transformed: ', docParams['issues']);
    let params = new HttpParams().set('doc_type', 'EXTRACT');
    Object.keys(docParams).forEach(key => params = params.set(key, docParams[key]));
    const fullUrl = this.addDocumentUrl + '/' + params.get('docType');
    console.log('posting to: ', fullUrl);
    return this.httpClient.get(fullUrl, { params: params });
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
