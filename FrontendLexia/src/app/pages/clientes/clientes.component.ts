import { Component } from '@angular/core';

import { PanelComponent } from '../panel/panel.component';

@Component({
  selector: 'app-clientes',
  standalone: true,

  imports: [
    PanelComponent
  ],

  templateUrl: './clientes.component.html',
  styleUrl: './clientes.component.css'
})
export class ClientesComponent {
}