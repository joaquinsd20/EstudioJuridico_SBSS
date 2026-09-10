import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';

import { PanelComponent } from '../panel/panel.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,

  imports: [
    CommonModule,
    PanelComponent
  ],

  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent {
}