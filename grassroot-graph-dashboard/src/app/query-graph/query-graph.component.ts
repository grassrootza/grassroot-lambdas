import { Component, OnInit } from '@angular/core';
import { FormControl, FormBuilder } from '@angular/forms';

@Component({
  selector: 'app-query-graph',
  templateUrl: './query-graph.component.html',
  styleUrls: ['./query-graph.component.css']
})
export class QueryGraphComponent implements OnInit {

  queryControl: FormControl;

  options: string[] = ['Find', 'Do'];

  constructor(private fb: FormBuilder) { }

  ngOnInit() {
    this.queryControl = this.fb.control('');
  }

  executeQuery() {
    console.log('executing this query: ', this.queryControl.value);
  }

}
