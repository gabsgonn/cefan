import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ChatMessage } from '../models/appointment.model';
import { AuthService } from './auth.service';

export interface BotStatus {
  status: string;
  instance: string;
  webhookActive: boolean;
  springBootOnline?: boolean;
  postgresOnline?: boolean;
  evolutionApiConnected?: boolean;
  evolutionLiveReachable?: boolean;
  mercadoPagoWebhookActive?: boolean;
  timestamp: string;
}

export interface BotReconnectResponse {
  status: string;
  restarted: boolean;
  instance: string;
  message: string;
  timestamp: string;
}

export interface BotIssueReportResponse {
  protocol: string;
  status: string;
  category: string;
  message: string;
  timestamp: string;
}

@Injectable({
  providedIn: 'root'
})
export class ChatService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly apiRoot = '/api/v1';

  getMessages(sessionId: string): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`${this.apiRoot}/conversations/${sessionId}/messages`);
  }

  sendMessage(sessionId: string, message: string): Observable<ChatMessage> {
    return this.http.post<ChatMessage>(`${this.apiRoot}/conversations/${sessionId}/messages`, { message });
  }

  streamMessages(sessionId: string): Observable<ChatMessage> {
    return new Observable<ChatMessage>((observer) => {
      if (typeof EventSource === 'undefined') {
        return;
      }
      const token = this.authService.token();
      const queryParam = token ? `?token=${encodeURIComponent(token)}` : '';
      const eventSource = new EventSource(`${this.apiRoot}/conversations/${sessionId}/stream${queryParam}`);

      eventSource.addEventListener('MESSAGE', (event: MessageEvent) => {
        try {
          const chatMsg: ChatMessage = JSON.parse(event.data);
          observer.next(chatMsg);
        } catch (e) {
          console.warn('Erro ao decodificar mensagem SSE:', e);
        }
      });

      eventSource.onerror = (error) => {
        // SSE connection will naturally retry
        console.debug('SSE Live Chat reconectando...', error);
      };

      return () => {
        eventSource.close();
      };
    });
  }

  streamAllMessages(): Observable<ChatMessage> {
    return new Observable<ChatMessage>((observer) => {
      if (typeof EventSource === 'undefined') {
        return;
      }
      const token = this.authService.token();
      const queryParam = token ? `?token=${encodeURIComponent(token)}` : '';
      const eventSource = new EventSource(`${this.apiRoot}/conversations/stream${queryParam}`);

      eventSource.addEventListener('MESSAGE', (event: MessageEvent) => {
        try {
          const chatMsg: ChatMessage = JSON.parse(event.data);
          observer.next(chatMsg);
        } catch (e) {
          console.warn('Erro ao decodificar mensagem SSE global:', e);
        }
      });

      eventSource.onerror = (error) => {
        console.debug('SSE Global Live Chat reconectando...', error);
      };

      return () => {
        eventSource.close();
      };
    });
  }

  assignArtist(sessionId: string, artistId: string | null): Observable<void> {
    return this.http.post<void>(`${this.apiRoot}/conversations/${sessionId}/assign`, { artistId });
  }

  resetBotSession(sessionId: string): Observable<void> {
    return this.http.post<void>(`${this.apiRoot}/conversations/${sessionId}/reset`, {});
  }

  getBotStatus(): Observable<BotStatus> {
    return this.http.get<BotStatus>(`${this.apiRoot}/bot/status`);
  }

  reconnectBot(): Observable<BotReconnectResponse> {
    return this.http.post<BotReconnectResponse>(`${this.apiRoot}/bot/reconnect`, {});
  }

  reportIssue(payload: { description: string; category?: string; sessionId?: string }): Observable<BotIssueReportResponse> {
    return this.http.post<BotIssueReportResponse>(`${this.apiRoot}/bot/report-issue`, payload);
  }
}
