import { Component } from '@angular/core';

import { PanelComponent } from '../panel/panel.component';

@Component({
  selector: 'app-documentos',
  standalone: true,

  imports: [
    PanelComponent
  ],

  templateUrl: './documentos.component.html',
  styleUrl: './documentos.component.css'
})
export class DocumentosComponent {
}