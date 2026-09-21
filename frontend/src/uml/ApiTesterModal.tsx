import { useState } from 'react'
import { ElementDetail } from './types'

type Props = {
  elements: ElementDetail[]
  onClose: () => void
}

export function ApiTesterModal({ elements, onClose }: Props) {
  const classes = elements.filter((e) => e.tipoElemento !== 'INTERFAZ')

  const [selectedClass, setSelectedClass] = useState<string>(
    classes.length > 0 ? classes[0].nombre : 'Entidad'
  )
  const [method, setMethod] = useState<'GET' | 'POST' | 'PUT' | 'DELETE'>('GET')
  const [endpointUrl, setEndpointUrl] = useState(`http://localhost:8081/api/v1/${classes[0]?.nombre.toLowerCase() || 'entidad'}s`)
  const [requestBody, setRequestBody] = useState('{\n  "nombre": "Ejemplo"\n}')
  const [responseOutput, setResponseOutput] = useState<string | null>(null)
  const [statusOutput, setStatusOutput] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [voiceQuery, setVoiceQuery] = useState('')
  const [isListening, setIsListening] = useState(false)

  function handleClassChange(name: string) {
    setSelectedClass(name)
    const plural = name.toLowerCase().endsWith('s') ? name.toLowerCase() : name.toLowerCase() + 's'
    setEndpointUrl(`http://localhost:8081/api/v1/${plural}`)
  }

  // Dictado por voz
  function toggleVoice() {
    const SpeechRecognition =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
    if (!SpeechRecognition) {
      alert('Tu navegador no soporta reconocimiento de voz por micrófono.')
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

      recognition.onstart = () => setIsListening(true)
      recognition.onresult = (e: any) => {
        const text = e.results[0][0].transcript
        setVoiceQuery(text)
        setIsListening(false)
        interpretVoiceCommand(text)
      }
      recognition.onerror = () => setIsListening(false)
      recognition.onend = () => setIsListening(false)

      recognition.start()
    } catch {
      setIsListening(false)
    }
  }

  function interpretVoiceCommand(text: string) {
    const lower = text.toLowerCase()
    if (lower.startsWith('crear') || lower.startsWith('crea')) {
      setMethod('POST')
      setRequestBody(`{\n  "descripcion": "${text}"\n}`)
    } else if (lower.startsWith('listar') || lower.startsWith('consulta') || lower.startsWith('ver')) {
      setMethod('GET')
    } else if (lower.startsWith('eliminar') || lower.startsWith('borra')) {
      setMethod('DELETE')
    }
  }

  async function handleExecute() {
    try {
      setLoading(true)
      setStatusOutput(null)
      setResponseOutput(null)

      const options: RequestInit = {
        method,
        headers: { 'Content-Type': 'application/json' }
      }
      if (method === 'POST' || method === 'PUT') {
        options.body = requestBody
      }

      const res = await fetch(endpointUrl, options)
      setStatusOutput(`${res.status} ${res.statusText}`)
      const text = await res.text()
      try {
        const json = JSON.parse(text)
        setResponseOutput(JSON.stringify(json, null, 2))
      } catch {
        setResponseOutput(text || '(Sin cuerpo de respuesta)')
      }
    } catch (err: any) {
      setStatusOutput('Error de Conexión')
      setResponseOutput(
        `No se pudo conectar a ${endpointUrl}.\n\nPara probar esta API, descarga y ejecuta el backend generado:\n1. Descomprime el archivo ZIP.\n2. Ejecuta: mvn spring-boot:run dentro de /backend.\n3. Tu API responderá en vivo en este puerto.`
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="uml-modal-backdrop">
      <div className="uml-modal api-tester-modal" style={{ maxWidth: '780px' }}>
        <div className="uml-modal-header">
          <div>
            <h3>🎙️ Consola de Prueba de API Generada (Voz & REST)</h3>
            <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
              Prueba interactivamente los endpoints Spring Boot generados para tu modelo UML
            </span>
          </div>
          <button type="button" className="close-btn" onClick={onClose}>
            ✕
          </button>
        </div>

        <div style={{ marginTop: '1rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {/* Asistente de comando por voz */}
          <div style={{ background: '#f8fafc', padding: '0.9rem', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
              <strong>Prueba rápida por Voz (Agente estilo Alexa)</strong>
              <button
                type="button"
                className="btn-sm"
                onClick={toggleVoice}
                style={{
                  background: isListening ? '#ef4444' : '#2563eb',
                  color: 'white',
                  fontWeight: 'bold',
                  padding: '0.4rem 0.8rem'
                }}
              >
                {isListening ? '🔴 Escuchando...' : '🎙️ Dictar Prueba'}
              </button>
            </div>
            <p style={{ fontSize: '0.8rem', color: '#64748b' }}>
              Di algo como: <em>"Crear cliente Carlos"</em> o <em>"Listar pedidos"</em> para configurar la petición automáticamente.
            </p>
            {voiceQuery && (
              <div style={{ marginTop: '0.5rem', fontSize: '0.85rem', color: '#1e293b' }}>
                Comando dictado: <strong>«{voiceQuery}»</strong>
              </div>
            )}
          </div>

          {/* Selector de entidad y método */}
          <div style={{ display: 'grid', gridTemplateColumns: '150px 100px 1fr', gap: '0.5rem', alignItems: 'center' }}>
            <select
              value={selectedClass}
              onChange={(e) => handleClassChange(e.target.value)}
              style={{ padding: '0.5rem', borderRadius: '6px', border: '1px solid #cbd5e1' }}
            >
              {classes.map((c) => (
                <option key={c.id} value={c.nombre}>
                  {c.nombre}
                </option>
              ))}
            </select>

            <select
              value={method}
              onChange={(e) => setMethod(e.target.value as any)}
              style={{
                padding: '0.5rem',
                borderRadius: '6px',
                border: '1px solid #cbd5e1',
                fontWeight: 'bold',
                color: method === 'GET' ? '#15803d' : method === 'POST' ? '#2563eb' : method === 'DELETE' ? '#b91c1c' : '#d97706'
              }}
            >
              <option value="GET">GET</option>
              <option value="POST">POST</option>
              <option value="PUT">PUT</option>
              <option value="DELETE">DELETE</option>
            </select>

            <input
              type="text"
              value={endpointUrl}
              onChange={(e) => setEndpointUrl(e.target.value)}
              style={{ padding: '0.5rem', borderRadius: '6px', border: '1px solid #cbd5e1', fontFamily: 'monospace', fontSize: '0.85rem' }}
            />
          </div>

          {/* Cuerpo si aplica */}
          {(method === 'POST' || method === 'PUT') && (
            <div>
              <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 'bold', marginBottom: '0.25rem' }}>
                Payload JSON:
              </label>
              <textarea
                rows={4}
                value={requestBody}
                onChange={(e) => setRequestBody(e.target.value)}
                style={{ width: '100%', padding: '0.5rem', borderRadius: '6px', border: '1px solid #cbd5e1', fontFamily: 'monospace', fontSize: '0.8rem' }}
              />
            </div>
          )}

          <div style={{ display: 'flex', justifyContent: 'flex-start' }}>
            <button
              type="button"
              className="btn btn-primary"
              disabled={loading}
              onClick={handleExecute}
            >
              {loading ? 'Ejecutando...' : '🚀 Enviar Petición'}
            </button>
          </div>

          {/* Respuesta */}
          {statusOutput && (
            <div style={{ background: '#0f172a', color: '#f8fafc', padding: '1rem', borderRadius: '8px', fontFamily: 'monospace', fontSize: '0.82rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid #334155', paddingBottom: '0.5rem', marginBottom: '0.5rem' }}>
                <span>Respuesta del Servidor</span>
                <span style={{ color: statusOutput.startsWith('2') ? '#4ade80' : '#f87171', fontWeight: 'bold' }}>
                  {statusOutput}
                </span>
              </div>
              <pre style={{ margin: 0, whiteSpace: 'pre-wrap', maxHeight: '180px', overflowY: 'auto' }}>
                {responseOutput}
              </pre>
            </div>
          )}
        </div>

        <div className="uml-modal-actions" style={{ marginTop: '1.5rem', display: 'flex', justifyContent: 'flex-end' }}>
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cerrar
          </button>
        </div>
      </div>
    </div>
  )
}
