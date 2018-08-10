import { Component } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { Breakpoints, BreakpointObserver } from '@angular/cdk/layout';
import { AuthenticationService } from './auth/authentication.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {

  title = 'grassroot-graph-dashboard';
  loggedIn = false;

  isHandset$: Observable<boolean> = this.breakpointObserver.observe(Breakpoints.Handset)
  .pipe(
    map(result => result.matches)
  );

  constructor(private authService: AuthenticationService, private router: Router, 
              private breakpointObserver: BreakpointObserver) {
    this.loggedIn = this.authService._isUserLoggedIn;
    this.authService.isUserLoggedIn.subscribe(loginState => {
      console.log('Log in state changed to: ', loginState);
      this.loggedIn = loginState;
    });
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

}
