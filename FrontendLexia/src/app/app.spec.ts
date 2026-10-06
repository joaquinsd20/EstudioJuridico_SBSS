import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      // App inyecta Router para la barra superior movil y para cerrar el
      // cajon al navegar; sin esto el TestBed no puede construirlo.
      providers: [provideRouter([])]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  // App es un contenedor: solo el <router-outlet>, y las pantallas llegan por
  // loadComponent. Este test verifica el arbol de rutas, no un titulo en el
  // template (que no lo tiene, y por eso fallaba desde el andamiaje del CLI).
  it('debe montar el router-outlet que hospeda las pantallas', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('router-outlet')).toBeTruthy();
  });
});
