import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.token();

  let authReq = req;
  if (token) {
    authReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // Se for a própria rota de login que falhou, deixa o componente de login tratar o erro de credenciais
      if (req.url.includes('/auth/login')) {
        return throwError(() => error);
      }

      // Se qualquer outra chamada falhar por falta de autenticação (401) ou acesso proibido (403), desloga e redireciona
      if (error.status === 401 || error.status === 403) {
        authService.logout();
      }

      return throwError(() => error);
    })
  );
};
