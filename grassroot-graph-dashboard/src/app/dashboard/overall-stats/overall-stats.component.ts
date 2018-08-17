import { Component, OnInit } from '@angular/core';
import { AnalyticsService } from '../analytics.service';

@Component({
  selector: 'app-overall-stats',
  templateUrl: './overall-stats.component.html',
  styleUrls: ['./overall-stats.component.css']
})
export class OverallStatsComponent implements OnInit {

  loading = true;

  metrics: {};
  metricsKeys: any;
  dataSource: any[] = [];

  displayedColumns: string[] = ['position', 'name', 'count'];

  constructor(private analyticsService: AnalyticsService) { }

  ngOnInit() {
    this.analyticsService.getTotalMetrics().subscribe(result => {
      console.log('metrics: ', result);
      this.metrics = result;
      this.metricsKeys = Object.keys(result);
      this.createDataSource(this.metrics);
      this.loading = false;
    }, error => {
      console.log('failed retrieving total metrics: ', error);
    });
  }

  createDataSource(metrics) {
    let index = 1;
    Object.keys(metrics).forEach(key => {
      const item = {
        position: index,
        metric: key,
        count: metrics[key]
      };
      this.dataSource.push(item);
      index++;
    });
    console.log('Constructed datasource: ', this.dataSource);
  }

}
