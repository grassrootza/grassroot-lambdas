import { Component, OnInit } from '@angular/core';
import { FormControl, FormBuilder } from '@angular/forms';
import { DocumentService } from '../document/document.service';

@Component({
  selector: 'app-query-graph',
  templateUrl: './query-graph.component.html',
  styleUrls: ['./query-graph.component.css']
})
export class QueryGraphComponent implements OnInit {

  queryControl: FormControl;
  options: string[] = ['Find', 'Do'];

  displayedColumns: string[] = ['docId', 'description', 'issues'];
  currentDocs: Document[];

  constructor(private docService: DocumentService, private fb: FormBuilder) { }

  ngOnInit() {
    this.queryControl = this.fb.control('');
    this.docService.listDocuments().subscribe(result => {
      console.log('result of list: ', result);
      this.currentDocs = result;
    });
  }

  executeQuery() {
    console.log('executing this query: ', this.queryControl.value);
    this.docService.queryDocuments(this.queryControl.value).subscribe(result => {
      this.currentDocs = result;
    });
  }

}
