import { Component, OnInit } from '@angular/core';
import { DocumentService } from '../document.service';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-add-document',
  templateUrl: './add-document.component.html',
  styleUrls: ['./add-document.component.css']
})
export class AddDocumentComponent implements OnInit {

  extractForm: FormGroup;
  submitting = false;

  constructor(private documentService: DocumentService,
              private snackBar: MatSnackBar,
              private fb: FormBuilder) { }

  ngOnInit() {
    this.extractForm = this.fb.group({
      'machine_name': ['', Validators.required],
      'human_name': ['', Validators.required],
      'issues': ['', Validators.required],
      'procedures': ['', Validators.required],
      'problems': ['', Validators.required],
      'stage_relevance': ['', Validators.required],
      'docType': ['EXTRACT', Validators.required],
      'main_text': [''],
      'doc_bucket': [environment.docDefaultBucket],
      'doc_key': ['']
    });
  }

  submit() {
    this.submitting = true;
    this.documentService.addDocumentToGraph(this.extractForm.value)
      .subscribe(result => {
        console.log('got back result: ', result);
        this.submitting = false;
        this.snackBar.open('Done! Document added', null, { duration: 3000 });
      }, error => {
        this.submitting = false;
        this.snackBar.open('Sorry, error adding document');
        console.log('error adding doc: ', error);
      });
  }

}
