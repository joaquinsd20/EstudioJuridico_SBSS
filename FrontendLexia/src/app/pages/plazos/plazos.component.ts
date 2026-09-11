import { Component } from '@angular/core';
import { PanelComponent } from '../panel/panel.component';

@Component({
  selector: 'app-plazos',
  standalone: true,

  imports: [
    PanelComponent
  ],

  templateUrl: './plazos.component.html',
  styleUrl: './plazos.component.css'
})
export class PlazosComponent {
}