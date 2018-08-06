import { BrowserModule } from '@angular/platform-browser';
import {BrowserAnimationsModule} from '@angular/platform-browser/animations';
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { AppComponent } from './app.component';
import { LayoutModule } from '@angular/cdk/layout';
import { MatToolbarModule, MatButtonModule, MatIconModule, MatListModule,
   MatGridListModule, MatCardModule, MatMenuModule,
   MatInputModule, MatFormFieldModule, MatAutocompleteModule, MatOptionModule, MatSelectModule, MatTableModule, MatSortModule } from '@angular/material';
import { QueryGraphComponent } from './query-graph/query-graph.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { ReactiveFormsModule } from '@angular/forms';
import { AddDocumentComponent } from './document/add-document/add-document.component';
import { HttpClientModule } from '@angular/common/http';

const routes: Routes = [
  { path: '', redirectTo: '/query', pathMatch: 'full' },
  { path: 'query', component: QueryGraphComponent},
  { path: 'dashboard', component: DashboardComponent},
  { path: 'document', component: AddDocumentComponent }
];

@NgModule({
  declarations: [
    AppComponent,
    QueryGraphComponent,
    DashboardComponent,
    AddDocumentComponent
  ],
  imports: [
    BrowserModule,
    HttpClientModule,
    BrowserAnimationsModule,
    LayoutModule,
    RouterModule.forRoot(routes),
    ReactiveFormsModule,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatGridListModule,
    MatCardModule,
    MatMenuModule,
    MatInputModule,
    MatFormFieldModule,
    MatAutocompleteModule,
    MatOptionModule,
    MatSelectModule,
    MatTableModule,
    MatSortModule
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
