import { Component, OnInit } from '@angular/core';
import { map } from 'rxjs/operators';
import { Breakpoints, BreakpointObserver } from '@angular/cdk/layout';
import { AnalyticsService } from './analytics.service';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css'],
})
export class DashboardComponent implements OnInit {
  
  /** Based on the screen size, switch from standard to one column per row */
  cards = this.breakpointObserver.observe(Breakpoints.Handset).pipe(
    map(({ matches }) => {
      if (matches) {
        return [
          { title: 'Overall stats', cols: 1, rows: 1 },
          { title: 'Top groups', cols: 1, rows: 1 },
          { title: 'Top users', cols: 1, rows: 1 },
          { title: 'Pagerank', cols: 1, rows: 1 }
        ];
      }

      return [
        { title: 'Overall stats', cols: 2, rows: 1 },
        { title: 'Top groups', cols: 1, rows: 1 },
        { title: 'Top users', cols: 1, rows: 2 },
        { title: 'Pagerank', cols: 1, rows: 1 }
      ];
    })
  );

  constructor(private analyticsService: AnalyticsService, private breakpointObserver: BreakpointObserver) {}

  ngOnInit(): void {
    console.log('Initiated dashboard component!');
    
    // this.analyticsService.getTopGroupsByMembership().subscribe(result => {
    //   console.log('retrieved top groups: ', result);
    // }, error => {
    //   console.log('failed retrieving top groups: ', error);
    // });

    // this.analyticsService.getTopUserByMembership().subscribe(result => {
    //   console.log('got top users by participation: ', result);
    // }, error => {
    //   console.log('failed retrieving top users by membership: ', error);
    // });

    // this.analyticsService.getTopUserByPageRankCloseness().subscribe(result => {
    //   console.log('got top users by page range closeness: ', result);
    // }, error => {
    //   console.log('failed retrieving top users by page rank closeness: ', error);
    // });

  }

}
