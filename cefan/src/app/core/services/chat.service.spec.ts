import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ChatService } from './chat.service';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';

describe('ChatService', () => {
  let service: ChatService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ChatService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(ChatService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch messages for a session', () => {
    const dummyMessages = [
      {
        id: 'msg-1',
        sessionId: 'sess-1',
        customerId: 'cust-1',
        senderType: 'CUSTOMER' as const,
        senderName: 'Cliente',
        content: 'Olá, gostaria de um orçamento',
        isFromMe: false,
        createdAt: '2026-11-13T10:00:00Z'
      }
    ];

    service.getMessages('sess-1').subscribe(msgs => {
      expect(msgs.length).toBe(1);
      expect(msgs[0].content).toBe('Olá, gostaria de um orçamento');
    });

    const req = httpMock.expectOne('/api/v1/conversations/sess-1/messages');
    expect(req.request.method).toBe('GET');
    req.flush(dummyMessages);
  });

  it('should send an agent message', () => {
    const reply = {
      id: 'msg-2',
      sessionId: 'sess-1',
      customerId: 'cust-1',
      senderType: 'AGENT' as const,
      senderName: 'Dono - Vício Tattoo',
      content: 'Olá! Podemos fazer seu orçamento agora.',
      isFromMe: true,
      createdAt: '2026-11-13T10:05:00Z'
    };

    service.sendMessage('sess-1', 'Olá! Podemos fazer seu orçamento agora.').subscribe(res => {
      expect(res.senderType).toBe('AGENT');
      expect(res.content).toBe('Olá! Podemos fazer seu orçamento agora.');
    });

    const req = httpMock.expectOne('/api/v1/conversations/sess-1/messages');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ message: 'Olá! Podemos fazer seu orçamento agora.' });
    req.flush(reply);
  });
});
