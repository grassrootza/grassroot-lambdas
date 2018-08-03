import { Component, OnInit } from '@angular/core';
import { DocumentService } from '../document.service';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';

@Component({
  selector: 'app-add-document',
  templateUrl: './add-document.component.html',
  styleUrls: ['./add-document.component.css']
})
export class AddDocumentComponent implements OnInit {

  extractForm: FormGroup;

  constructor(private documentService: DocumentService,
              private fb: FormBuilder) { }

  ngOnInit() {
    this.extractForm = this.fb.group({
      'machine_name': ['', Validators.required],
      'human_name': ['', Validators.required],
      'issues': ['', Validators.required],
      'procedures': ['', Validators.required],
      'problems': ['', Validators.required],
      'stage_relevance': ['', Validators.required],
      'main_text': ['', Validators.required]
    });
  }

  submit() {
    this.documentService.addExtractToGraph(this.extractForm.value)
      .subscribe(result => console.log('got back result: ', result), error => console.log('error adding doc: ', error));
  }

}
