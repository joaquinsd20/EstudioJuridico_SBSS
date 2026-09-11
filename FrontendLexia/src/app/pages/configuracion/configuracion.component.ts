import { Component } from '@angular/core';
import { PanelComponent } from '../panel/panel.component';

@Component({
  selector: 'app-configuracion',
  standalone: true,

  imports: [
    PanelComponent
  ],

  templateUrl: './configuracion.component.html',
  styleUrl: './configuracion.component.css'
})
export class ConfiguracionComponent {
}