import { BrowserModule } from '@angular/platform-browser';
import {BrowserAnimationsModule} from '@angular/platform-browser/animations';
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { AppComponent } from './app.component';
import { LayoutModule } from '@angular/cdk/layout';
import { MatToolbarModule, MatButtonModule, MatIconModule, MatListModule,
   MatGridListModule, MatCardModule, MatMenuModule,
   MatInputModule, MatFormFieldModule, MatAutocompleteModule, MatOptionModule, MatSelectModule, MatTableModule, MatSortModule, 
   MatRadioModule, 
   MatProgressSpinnerModule,
   MatSnackBarModule,
   MatExpansionModule} from '@angular/material';
import { QueryGraphComponent } from './query-graph/query-graph.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { ReactiveFormsModule } from '@angular/forms';
import { AddDocumentComponent } from './document/add-document/add-document.component';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { LoginComponent } from './auth/login/login.component';
import { JwtInterceptor } from './auth/jwt.interceptor';
import { ErrorInterceptor } from './auth/error.interceptor';
import { AuthGuard } from './auth/auth.guard';

const routes: Routes = [
  { path: '', redirectTo: '/query', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'query', component: QueryGraphComponent, canActivate: [AuthGuard]},
  { path: 'dashboard', component: DashboardComponent, canActivate: [AuthGuard]},
  { path: 'document', component: AddDocumentComponent, canActivate: [AuthGuard] }
];

@NgModule({
  declarations: [
    AppComponent,
    QueryGraphComponent,
    DashboardComponent,
    AddDocumentComponent,
    LoginComponent
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
    MatSortModule,
    MatRadioModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatExpansionModule
  ],
  providers: [
    { provide: HTTP_INTERCEPTORS, useClass: JwtInterceptor, multi: true },
    { provide: HTTP_INTERCEPTORS, useClass: ErrorInterceptor, multi: true },
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
