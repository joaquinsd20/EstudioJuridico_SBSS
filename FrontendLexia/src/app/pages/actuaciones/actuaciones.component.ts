import { Component } from '@angular/core';
import { PanelComponent } from '../panel/panel.component';

@Component({
  selector: 'app-actuaciones',
  standalone: true,

  imports: [
    PanelComponent
  ],

  templateUrl: './actuaciones.component.html',
  styleUrl: './actuaciones.component.css'
})
export class ActuacionesComponent {
}