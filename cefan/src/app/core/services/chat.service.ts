import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ChatMessage } from '../models/appointment.model';

export interface BotStatus {
  status: string;
  instance: string;
  webhookActive: boolean;
  timestamp: string;
}

@Injectable({
  providedIn: 'root'
})
export class ChatService {
  private readonly http = inject(HttpClient);
  private readonly apiRoot = '/api/v1';

  getMessages(sessionId: string): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`${this.apiRoot}/conversations/${sessionId}/messages`);
  }

  sendMessage(sessionId: string, message: string): Observable<ChatMessage> {
    return this.http.post<ChatMessage>(`${this.apiRoot}/conversations/${sessionId}/messages`, { message });
  }

  streamMessages(sessionId: string): Observable<ChatMessage> {
    return new Observable<ChatMessage>((observer) => {
      const eventSource = new EventSource(`${this.apiRoot}/conversations/${sessionId}/stream`);

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

  assignArtist(sessionId: string, artistId: string | null): Observable<void> {
    return this.http.post<void>(`${this.apiRoot}/conversations/${sessionId}/assign`, { artistId });
  }

  resetBotSession(sessionId: string): Observable<void> {
    return this.http.post<void>(`${this.apiRoot}/conversations/${sessionId}/reset`, {});
  }

  getBotStatus(): Observable<BotStatus> {
    return this.http.get<BotStatus>(`${this.apiRoot}/bot/status`);
  }
}
