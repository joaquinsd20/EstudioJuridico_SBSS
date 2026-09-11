import { Component } from '@angular/core';
import { PanelComponent } from '../panel/panel.component';

@Component({
  selector: 'app-monitor-judicial',
  standalone: true,

  imports: [
    PanelComponent
  ],

  templateUrl: './monitor-judicial.component.html',
  styleUrl: './monitor-judicial.component.css'
})
export class MonitorJudicialComponent {
}