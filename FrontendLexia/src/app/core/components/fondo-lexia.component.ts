import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Fondo animado de las pantallas de acceso (login y recuperar contraseña).
 *
 * Todo es CSS: tres luces de color que derivan lentamente como una aurora,
 * una cuadricula que se desplaza casi imperceptible, un haz de luz que cruza
 * en diagonal y particulas que suben. Movimientos largos (20-40 s) para que
 * acompañe sin distraer. Con "reducir movimiento" queda estatico.
 */
@Component({
  selector: 'app-fondo-lexia',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="fondo" aria-hidden="true">
      <div class="aurora a1"></div>
      <div class="aurora a2"></div>
      <div class="aurora a3"></div>
      <div class="grid"></div>
      <div class="haz"></div>
      @for (p of particulas; track $index) {
        <span class="particula"
              [style.left.%]="p.x"
              [style.width.px]="p.t"
              [style.height.px]="p.t"
              [style.animation-duration.s]="p.d"
              [style.animation-delay.s]="p.r"></span>
      }
      <div class="vineta"></div>
    </div>
  `,
  styles: [`
    :host { position: fixed; inset: 0; z-index: 0; pointer-events: none; }

    .fondo {
      position: absolute;
      inset: 0;
      overflow: hidden;
      background:
        radial-gradient(120% 80% at 0% 0%, #1e1b4b 0%, transparent 60%),
        linear-gradient(160deg, #0a0e22 0%, #10153a 55%, #1a1446 100%);
    }

    /* Luces de aurora: grandes, muy difusas y en movimiento lento. */
    .aurora {
      position: absolute;
      border-radius: 50%;
      filter: blur(90px);
      will-change: transform;
    }
    .a1 {
      width: 560px; height: 560px; right: -160px; top: -160px;
      background: #6366f1; opacity: .45;
      animation: deriva1 28s ease-in-out infinite;
    }
    .a2 {
      width: 480px; height: 480px; left: -160px; bottom: -180px;
      background: #8b5cf6; opacity: .36;
      animation: deriva2 34s ease-in-out infinite;
    }
    .a3 {
      width: 360px; height: 360px; left: 38%; top: 30%;
      background: #3b82f6; opacity: .14;
      animation: deriva3 40s ease-in-out infinite;
    }
    @keyframes deriva1 {
      0%, 100% { transform: translate(0, 0) scale(1); }
      33% { transform: translate(-90px, 70px) scale(1.12); }
      66% { transform: translate(40px, 120px) scale(.94); }
    }
    @keyframes deriva2 {
      0%, 100% { transform: translate(0, 0) scale(1); }
      40% { transform: translate(120px, -80px) scale(1.15); }
      70% { transform: translate(60px, -140px) scale(.9); }
    }
    @keyframes deriva3 {
      0%, 100% { transform: translate(0, 0) scale(1); opacity: .14; }
      50% { transform: translate(-140px, -60px) scale(1.3); opacity: .24; }
    }

    /* Cuadricula que avanza muy despacio en diagonal. */
    .grid {
      position: absolute;
      inset: -48px;
      background-image:
        linear-gradient(rgba(148, 163, 184, .06) 1px, transparent 1px),
        linear-gradient(90deg, rgba(148, 163, 184, .06) 1px, transparent 1px);
      background-size: 48px 48px;
      mask-image: radial-gradient(ellipse at 50% 45%, #000 15%, transparent 72%);
      -webkit-mask-image: radial-gradient(ellipse at 50% 45%, #000 15%, transparent 72%);
      animation: avance 24s linear infinite;
    }
    @keyframes avance {
      to { transform: translate(48px, 48px); }
    }

    /* Haz de luz que cruza la pantalla cada tanto. */
    .haz {
      position: absolute;
      top: -50%;
      left: -60%;
      width: 40%;
      height: 200%;
      background: linear-gradient(90deg, transparent, rgba(165, 180, 252, .07), transparent);
      transform: rotate(18deg);
      animation: barrido 14s ease-in-out infinite;
    }
    @keyframes barrido {
      0%, 55% { left: -60%; }
      100% { left: 130%; }
    }

    /* Particulas de luz que suben y se desvanecen. */
    .particula {
      position: absolute;
      bottom: -12px;
      border-radius: 50%;
      background: #c7d2fe;
      box-shadow: 0 0 8px rgba(165, 180, 252, .9);
      opacity: 0;
      animation-name: subir;
      animation-timing-function: linear;
      animation-iteration-count: infinite;
    }
    @keyframes subir {
      0% { transform: translateY(0); opacity: 0; }
      12% { opacity: .75; }
      80% { opacity: .5; }
      100% { transform: translateY(-105vh); opacity: 0; }
    }

    /* Oscurece los bordes para que la vista vaya al centro. */
    .vineta {
      position: absolute;
      inset: 0;
      background: radial-gradient(ellipse at center, transparent 45%, rgba(5, 7, 20, .55) 100%);
    }

    @media (max-width: 600px) {
      .a3, .haz { display: none; }
      .aurora { filter: blur(70px); }
    }

    @media (prefers-reduced-motion: reduce) {
      .aurora, .grid, .haz { animation: none; }
      .particula { display: none; }
    }
  `]
})
export class FondoLexiaComponent {
  /** Posicion, tamaño, duracion y retraso fijos: el fondo no cambia entre visitas. */
  readonly particulas = [
    { x: 6, t: 2, d: 19, r: -2 }, { x: 14, t: 3, d: 24, r: -11 }, { x: 23, t: 2, d: 21, r: -6 },
    { x: 31, t: 2, d: 27, r: -17 }, { x: 42, t: 3, d: 23, r: -3 }, { x: 51, t: 2, d: 29, r: -20 },
    { x: 59, t: 2, d: 20, r: -9 }, { x: 67, t: 3, d: 26, r: -14 }, { x: 75, t: 2, d: 22, r: -5 },
    { x: 83, t: 2, d: 28, r: -23 }, { x: 90, t: 3, d: 18, r: -8 }, { x: 96, t: 2, d: 25, r: -15 }
  ];
}
