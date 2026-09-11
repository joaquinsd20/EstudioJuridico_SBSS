import { Component } from '@angular/core';
import { PanelComponent } from '../panel/panel.component';

@Component({
  selector: 'app-agenda',
  standalone: true,

  imports: [
    PanelComponent
  ],

  templateUrl: './agenda.component.html',
  styleUrl: './agenda.component.css'
})
export class AgendaComponent {
}