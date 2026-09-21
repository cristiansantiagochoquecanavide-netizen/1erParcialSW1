import React, { useEffect, useRef, useState } from 'react'
import { DiagramFullView } from './types'

type Props = {
  projectId: string
  diagramId: string
  token: string
  onClose: () => void
  onDiagramUpdated: (fullView: DiagramFullView) => void
  onError: (err: unknown) => void
}

type MessageItem = {
  sender: 'user' | 'ai'
  text: string
  actionType?: string
  success?: boolean
  suggestion?: string
}

export function AiAssistantModal({
  projectId,
  diagramId,
  token,
  onClose,
  onDiagramUpdated,
  onError
}: Props) {
  const [messages, setMessages] = useState<MessageItem[]>([
    {
      sender: 'ai',
      text: '¡Hola! Soy tu asistente de modelado UML e ingeniería de software. Puedes pedirme crear clases, agregar atributos, conectar relaciones por voz o texto, o resolver dudas conceptuales.'
    }
  ])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [isListening, setIsListening] = useState(false)
  const [mode, setMode] = useState<'command' | 'chat'>('command')

  const chatEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  // Reconocimiento de voz mediante Web Speech API
  function toggleListening() {
    const SpeechRecognition =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
    if (!SpeechRecognition) {
      alert('Tu navegador no soporta la API de reconocimiento de voz por micrófono. Puedes escribir el comando directamente.')
      return
    }

    if (isListening) {
      setIsListening(false)
      return
    }

    try {
      const recognition = new SpeechRecognition()
      recognition.lang = 'es-ES'
      recognition.continuous = false
      recognition.interimResults = false

      recognition.onstart = () => {
        setIsListening(true)
      }

      recognition.onresult = (event: any) => {
        const transcript = event.results[0][0].transcript
        setInput(transcript)
        setIsListening(false)
      }

      recognition.onerror = () => {
        setIsListening(false)
      }

      recognition.onend = () => {
        setIsListening(false)
      }

      recognition.start()
    } catch (e) {
      setIsListening(false)
    }
  }

  async function handleSend(textToSend?: string) {
    const query = (textToSend || input).trim()
    if (!query || loading) return

    const userMsg: MessageItem = { sender: 'user', text: query }
    setMessages((prev) => [...prev, userMsg])
    setInput('')
    setLoading(true)

    try {
      if (mode === 'command') {
        const res = await fetch(`/api/projects/${projectId}/diagrams/${diagramId}/ai/command`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`
          },
          body: JSON.stringify({ command: query, source: isListening ? 'VOICE' : 'TEXT' })
        })
        if (!res.ok) {
          const body = await res.json().catch(() => ({}))
          throw new Error(body.message || 'Error al ejecutar comando de IA.')
        }
        const data = await res.json()
        setMessages((prev) => [
          ...prev,
          {
            sender: 'ai',
            text: data.message,
            actionType: data.actionType,
            success: data.success
          }
        ])
        if (data.success && data.updatedDiagram) {
          onDiagramUpdated(data.updatedDiagram)
        }
      } else {
        // Chat conceptual / asesoría
        const res = await fetch(`/api/projects/${projectId}/diagrams/${diagramId}/ai/chat`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`
          },
          body: JSON.stringify({ question: query })
        })
        if (!res.ok) {
          throw new Error('Error al consultar al asesor de IA.')
        }
        const data = await res.json()
        setMessages((prev) => [
          ...prev,
          {
            sender: 'ai',
            text: data.answer,
            suggestion: data.suggestion
          }
        ])
      }
    } catch (err) {
      onError(err)
      setMessages((prev) => [
        ...prev,
        {
          sender: 'ai',
          text: 'Ocurrió un error al procesar la solicitud.',
          success: false
        }
      ])
    } finally {
      setLoading(false)
    }
  }

  const quickPrompts = [
    'Crear clase Cliente con atributos nombre:String, email:String, telefono:String',
    'Crear clase Pedido con atributos fecha:LocalDate, total:Double',
    'Relacionar Cliente con Pedido 1 a N',
    'Crear clase Producto con atributos codigo:String, precio:Double',
    'Explicar diferencia entre Composición y Agregación'
  ]

  return (
    <div className="uml-modal-backdrop">
      <div className="uml-modal ai-modal" style={{ maxWidth: '680px', height: '620px', display: 'flex', flexDirection: 'column' }}>
        <div className="uml-modal-header" style={{ paddingBottom: '0.75rem', borderBottom: '1px solid #e2e8f0' }}>
          <div>
            <h3>🤖 Asistente de IA Local & Comandos de Voz</h3>
            <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
              Modela mediante lenguaje natural o consulta conceptos de ingeniería
            </span>
          </div>
          <button type="button" className="close-btn" onClick={onClose}>
            ✕
          </button>
        </div>

        {/* Selector de modo */}
        <div style={{ display: 'flex', gap: '0.5rem', padding: '0.75rem 0', borderBottom: '1px solid #f1f5f9' }}>
          <button
            type="button"
            className={`btn-sm ${mode === 'command' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setMode('command')}
          >
            ⚡ Comandos al Modelo (Acciones directas)
          </button>
          <button
            type="button"
            className={`btn-sm ${mode === 'chat' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setMode('chat')}
          >
            💡 Asesoría y Conceptos UML
          </button>
        </div>

        {/* Historial de conversación */}
        <div className="ai-chat-history" style={{ flex: 1, overflowY: 'auto', padding: '1rem 0', display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          {messages.map((m, idx) => (
            <div
              key={idx}
              style={{
                alignSelf: m.sender === 'user' ? 'flex-end' : 'flex-start',
                maxWidth: '85%',
                background: m.sender === 'user' ? '#2563eb' : '#f1f5f9',
                color: m.sender === 'user' ? '#ffffff' : '#0f172a',
                padding: '0.75rem 1rem',
                borderRadius: '12px',
                fontSize: '0.9rem',
                lineHeight: '1.4',
                boxShadow: '0 1px 2px rgba(0,0,0,0.05)'
              }}
            >
              {m.actionType && (
                <div style={{ fontSize: '0.75rem', fontWeight: 'bold', marginBottom: '0.25rem', color: m.success ? '#15803d' : '#b91c1c' }}>
                  {m.success ? '✓ ' + m.actionType : '⚠ ' + m.actionType}
                </div>
              )}
              <div>{m.text}</div>
              {m.suggestion && (
                <div
                  onClick={() => handleSend(m.suggestion)}
                  style={{ marginTop: '0.5rem', fontSize: '0.78rem', color: '#4338ca', cursor: 'pointer', textDecoration: 'underline' }}
                >
                  💡 Sugerencia: {m.suggestion}
                </div>
              )}
            </div>
          ))}
          {loading && (
            <div style={{ alignSelf: 'flex-start', color: '#64748b', fontSize: '0.85rem', fontStyle: 'italic' }}>
              El asistente está procesando tu comando...
            </div>
          )}
          <div ref={chatEndRef} />
        </div>

        {/* Sugerencias rápidas */}
        <div style={{ display: 'flex', gap: '0.4rem', overflowX: 'auto', paddingBottom: '0.5rem' }}>
          {quickPrompts.slice(0, 3).map((prompt, i) => (
            <button
              key={i}
              type="button"
              className="btn-sm"
              style={{ fontSize: '0.75rem', whiteSpace: 'nowrap', background: '#f8fafc', borderColor: '#cbd5e1' }}
              onClick={() => handleSend(prompt)}
            >
              {prompt}
            </button>
          ))}
        </div>

        {/* Input con micrófono y envío */}
        <form
          onSubmit={(e) => {
            e.preventDefault()
            handleSend()
          }}
          style={{ display: 'flex', gap: '0.5rem', paddingTop: '0.75rem', borderTop: '1px solid #e2e8f0' }}
        >
          <button
            type="button"
            onClick={toggleListening}
            title={isListening ? 'Escuchando... clic para detener' : 'Hablar por micrófono'}
            style={{
              padding: '0.6rem 0.9rem',
              borderRadius: '8px',
              border: '1px solid #cbd5e1',
              background: isListening ? '#ef4444' : '#f1f5f9',
              color: isListening ? '#ffffff' : '#334155',
              cursor: 'pointer',
              fontWeight: 'bold',
              transition: 'background 0.2s'
            }}
          >
            {isListening ? '🔴 Detener' : '🎙️ Voz'}
          </button>

          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder={
              mode === 'command'
                ? 'Ej. Crear clase Producto con nombre:String, precio:Double'
                : 'Haz una pregunta sobre modelado UML o arquitectura...'
            }
            style={{ flex: 1, padding: '0.6rem 0.9rem', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '0.9rem' }}
          />

          <button
            type="submit"
            disabled={loading || !input.trim()}
            className="btn btn-primary"
            style={{ padding: '0.6rem 1.2rem' }}
          >
            Enviar
          </button>
        </form>
      </div>
    </div>
  )
}
