import { Injectable, EventEmitter } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AuthenticationService {

    public _isUserLoggedIn: boolean;
    public isUserLoggedIn: EventEmitter<boolean> = new EventEmitter(this._isUserLoggedIn);

    constructor(private http: HttpClient) {
      this._isUserLoggedIn = !!localStorage.getItem('currentUser');
      this.isUserLoggedIn.emit(this._isUserLoggedIn);
    }

    login(username: string, password: string) {
      const params = new HttpParams().set('username', username).set('password', password);
      return this.http.post<any>(`${environment.authApiUrl}/v2/api/auth/login-password`, null, { params: params })
          .pipe(map(response => {
              console.log('response from auth server: ', response);
              // login successful if there's a jwt token in the response
              const user = response.user;
              if (user && user.token) {
                  // store user details and jwt token in local storage to keep user logged in between page refreshes
                  localStorage.setItem('currentUser', JSON.stringify(user));
                  this._isUserLoggedIn = true;
                  this.isUserLoggedIn.emit(true);
              }
              return user;
          }));
    }

    logout() {
        // remove user from local storage to log user out
        localStorage.removeItem('currentUser');
        this._isUserLoggedIn = false;
        this.isUserLoggedIn.emit(false);
    }

}
