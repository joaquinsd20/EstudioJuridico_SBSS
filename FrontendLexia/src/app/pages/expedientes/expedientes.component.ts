import { Component } from '@angular/core';

import { PanelComponent } from '../panel/panel.component';

@Component({
  selector: 'app-expedientes',
  standalone: true,

  imports: [
    PanelComponent
  ],

  templateUrl: './expedientes.component.html',
  styleUrl: './expedientes.component.css'
})
export class ExpedientesComponent {
}