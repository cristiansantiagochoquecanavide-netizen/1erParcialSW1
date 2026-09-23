import { createWorker } from 'tesseract.js'

export interface RecognizedAttribute {
  visibility: string
  name: string
  type: string
}

export interface RecognizedMethod {
  visibility: string
  name: string
  returnType: string
}

export interface RecognizedClass {
  name: string
  posX: number
  posY: number
  attributes: RecognizedAttribute[]
  methods: RecognizedMethod[]
}

export interface RecognizedRelation {
  source: string
  target: string
  name?: string
  tipo: string
  multSource: string
  multTarget: string
}

export interface RecognizedDiagram {
  classes: RecognizedClass[]
  relations: RecognizedRelation[]
  source: 'OCR' | 'GEMINI'
}

function parseVisibility(str: string): { vis: string; rest: string } {
  if (str.startsWith('-')) return { vis: '-', rest: str.substring(1).trim() }
  if (str.startsWith('+')) return { vis: '+', rest: str.substring(1).trim() }
  if (str.startsWith('#')) return { vis: '#', rest: str.substring(1).trim() }
  if (str.startsWith('~')) return { vis: '~', rest: str.substring(1).trim() }
  return { vis: '+', rest: str.trim() }
}

function cleanType(raw: string): string {
  if (!raw) return 'String'
  const trimmed = raw.trim()
  const genericMatch = trimmed.match(/^(List|Set|Collection|Map)\s*<([a-zA-Z0-9_]+)>/i)
  if (genericMatch) {
    const coll = genericMatch[1].charAt(0).toUpperCase() + genericMatch[1].slice(1).toLowerCase()
    const inner = genericMatch[2].charAt(0).toUpperCase() + genericMatch[2].slice(1)
    return `${coll}<${inner}>`
  }
  const clean = trimmed.replace(/[^a-zA-Z0-9_]/g, '')
  const lower = clean.toLowerCase()
  if (lower === 'long') return 'Long'
  if (lower === 'string' || lower === 'strin') return 'String'
  if (lower === 'int' || lower === 'integer') return 'Integer'
  if (lower === 'date' || lower === 'datetime' || lower === 'localdate') return 'Date'
  if (lower === 'double' || lower === 'float' || lower === 'decimal') return 'Double'
  if (lower === 'bool' || lower === 'boolean') return 'Boolean'
  if (lower === 'void') return 'void'
  return clean || 'String'
}

function parseAttribute(line: string): RecognizedAttribute {
  const { vis, rest } = parseVisibility(line)
  const colonIdx = rest.indexOf(':')
  if (colonIdx > 0) {
    let name = rest.substring(0, colonIdx).trim().replace(/[^a-zA-Z0-9_]/g, '')
    if (name.toLowerCase() === 'fechalnicio') name = 'fechaInicio'
    const type = cleanType(rest.substring(colonIdx + 1))
    return { visibility: vis, name, type }
  }
  const parts = rest.split(/\s+/)
  if (parts.length >= 2) {
    return { visibility: vis, name: parts[0], type: cleanType(parts[1]) }
  }
  return { visibility: vis, name: rest.replace(/[^a-zA-Z0-9_]/g, ''), type: 'String' }
}

function parseMethod(line: string): RecognizedMethod {
  const { vis, rest } = parseVisibility(line)
  let name = rest
  let returnType = 'void'
  const colonIdx = rest.indexOf(':')
  if (colonIdx > 0) {
    returnType = cleanType(rest.substring(colonIdx + 1))
    name = rest.substring(0, colonIdx).trim()
  }
  name = name.replace(/\(\s*\)/g, '').replace(/[^a-zA-Z0-9_]/g, '')
  return { visibility: vis, name: name || 'metodo', returnType }
}

function cleanMultiplicity(raw: string): string | null {
  const clean = raw.trim()
  if (/[a-zA-Z]{3,}/.test(clean)) return null
  const s = clean.toLowerCase().replace(/[^0-9\.\*bnm]/g, '')
  if (s === '0.' || s === '0..' || s.includes('0.*') || s.includes('0..*') || s.includes('0*')) return '0..*'
  if (s === '1.' || s === '1..' || s.includes('1.*') || s.includes('1..*') || s.includes('1*') || s === 'b' || s === 'bi') return '1..*'
  if (s.includes('0.1') || s.includes('0..1')) return '0..1'
  if (s.includes('1.1') || s.includes('1..1')) return '1..1'
  if (s === '1') return '1'
  if (s === '0') return '0'
  if (s === '*' || s === 'n' || s === 'm') return '*'
  return null
}

export async function recognizeDiagramWithGemini(
  base64Image: string,
  apiKey: string
): Promise<RecognizedDiagram | null> {
  if (!apiKey || !apiKey.trim()) return null

  try {
    const cleanBase64 = base64Image.includes(',')
      ? base64Image.substring(base64Image.indexOf(',') + 1)
      : base64Image

    const mimeType = base64Image.startsWith('data:image/png') ? 'image/png' : 'image/jpeg'

    const prompt = `You are an expert UML Reverse Engineering system.
Analyze this UML diagram image in detail.
Extract EVERY class shown with:
- name: PascalCase class name
- attributes: array of { visibility: ("+"|"-"|"#"|"~"), name: string, type: string }
- methods: array of { visibility: ("+"|"-"|"#"|"~"), name: string, returnType: string }
- posX: estimated X coordinate keeping the exact relative layout from the image (scale range 80 to 850)
- posY: estimated Y coordinate keeping the exact relative layout from the image (scale range 60 to 450)

Extract EVERY relationship shown with:
- source: source class name (where the arrow starts, or left/top class)
- target: target class name (where the arrow points, or right/bottom class)
- name: relationship label if present (e.g. "realiza", "contiene", "clasifica", "tiene", "asocia", "pertenece")
- tipo: "ASOCIACION" | "HERENCIA" | "AGREGACION" | "COMPOSICION" | "DEPENDENCIA"
- multSource: multiplicity at source (e.g. "1", "1..*", "0..*")
- multTarget: multiplicity at target (e.g. "*", "0..1", "0..*", "1")

Return ONLY valid JSON matching this exact structure without markdown or explanation:
{
  "classes": [
    {
      "name": "Cliente",
      "posX": 100,
      "posY": 60,
      "attributes": [{ "visibility": "-", "name": "id", "type": "Long" }],
      "methods": [{ "visibility": "+", "name": "actualizarDatos", "returnType": "void" }]
    }
  ],
  "relations": [
    {
      "source": "Cliente",
      "target": "Pedido",
      "name": "realiza",
      "tipo": "ASOCIACION",
      "multSource": "1",
      "multTarget": "0..*"
    }
  ]
}`

    const models = ['gemini-2.0-flash', 'gemini-1.5-flash']
    for (const model of models) {
      const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey.trim()}`
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          contents: [
            {
              parts: [
                { text: prompt },
                {
                  inline_data: {
                    mime_type: mimeType,
                    data: cleanBase64
                  }
                }
              ]
            }
          ],
          generationConfig: {
            response_mime_type: 'application/json'
          }
        })
      })

      if (res.ok) {
        const data = await res.json()
        const text = data.candidates?.[0]?.content?.parts?.[0]?.text
        if (text) {
          const parsed = JSON.parse(text)
          if (Array.isArray(parsed.classes) && parsed.classes.length > 0) {
            const rawRels: RecognizedRelation[] = Array.isArray(parsed.relations) ? parsed.relations : []
            const validRels = rawRels.filter(r => {
              return r.source && r.target && r.source.toLowerCase() !== r.target.toLowerCase()
            })

            return {
              classes: parsed.classes,
              relations: validRels,
              source: 'GEMINI'
            }
          }
        }
      } else {
        const errText = await res.text().catch(() => '')
        console.warn(`Gemini Vision [${model}] falló con status ${res.status}:`, errText)
      }
    }
  } catch (err) {
    console.warn('Error durante reconocimiento con Gemini Vision:', err)
  }
  return null
}

export async function recognizeDiagramWithOcr(
  imageSource: string | HTMLCanvasElement,
  onProgress?: (msg: string) => void
): Promise<RecognizedDiagram | null> {
  const ocrTask = async (): Promise<RecognizedDiagram | null> => {
    let worker: any = null
    try {
      onProgress?.('Iniciando motor de OCR local...')

      const isBrowser = typeof window !== 'undefined'
      const workerOptions = isBrowser
        ? {
            workerPath: '/tessdata/worker.min.js',
            corePath: '/tessdata/tesseract-core-lstm.wasm.js',
            langPath: '/tessdata',
            workerBlobURL: false,
            gzip: false
          }
        : undefined

      worker = await (createWorker as any)('eng', 1, workerOptions)

      await worker.setParameters({
        tessedit_pageseg_mode: '11' as any
      })

      onProgress?.('Extrayendo texto y geometría del diagrama...')
      const ret = await (worker as any).recognize(imageSource, {}, { blocks: true })

      const blocks: any[] = ret.data.blocks || []
      const valid = blocks
        .map(b => ({
          text: (b.text || '').trim().replace(/\n+/g, '\n'),
          bbox: b.bbox
        }))
        .filter(b => b.text.length > 0)

      onProgress?.('Analizando clases, atributos y métodos...')

      const commonWords = new Set([
        'long', 'string', 'date', 'double', 'int', 'integer', 'boolean', 'void', 'list', 'set', 'map',
        'realiza', 'tiene', 'contiene', 'clasifica', 'asociacion', 'asocia', 'pertenece', 'incluye'
      ])

      interface TempClass {
        name: string
        bbox: { x0: number; y0: number; x1: number; y1: number }
        fullBbox: { x0: number; y0: number; x1: number; y1: number }
        attributes: RecognizedAttribute[]
        methods: RecognizedMethod[]
        posX: number
        posY: number
      }

      const classes: TempClass[] = []
      const items: { type: 'attr' | 'method'; line: string; bbox: any }[] = []
      const unassigned: { text: string; bbox: any }[] = []

      // 1. Identificar encabezados de clases
      for (const b of valid) {
        const lines = b.text.split('\n').map((l: string) => l.trim()).filter(Boolean)
        for (const line of lines) {
          const clean = line.replace(/[:=+\-#\(\)]/g, '').trim()
          if (
            /^[A-Z][a-zA-Z0-9_]{2,}$/.test(clean) &&
            !commonWords.has(clean.toLowerCase())
          ) {
            if (!classes.some(c => c.name.toLowerCase() === clean.toLowerCase())) {
              classes.push({
                name: clean,
                bbox: { ...b.bbox },
                fullBbox: { ...b.bbox },
                attributes: [],
                methods: [],
                posX: Math.round(b.bbox.x0),
                posY: Math.round(b.bbox.y0)
              })
            }
          }
        }
      }

      // 2. Clasificar líneas de atributos, métodos y otros tokens
      for (const b of valid) {
        const lines = b.text.split('\n').map((l: string) => l.trim()).filter(Boolean)
        for (const line of lines) {
          const clean = line.replace(/[:=+\-#\(\)]/g, '').trim()
          if (classes.some(c => c.name.toLowerCase() === clean.toLowerCase())) continue

          const isMethod = line.includes('()') || line.includes('):') || (line.startsWith('+') && line.includes('('))
          const isAttr = !isMethod && (line.startsWith('-') || line.includes(':'))

          if (isMethod || isAttr) {
            items.push({ type: isMethod ? 'method' : 'attr', line, bbox: b.bbox })
          } else {
            unassigned.push({ text: line, bbox: b.bbox })
          }
        }
      }

      // 3. Asociar atributos y métodos a la clase correspondiente más cercana
      for (const item of items) {
        let best: TempClass | null = null
        let minD = Infinity
        for (const c of classes) {
          const vert = item.bbox.y0 - c.bbox.y0
          const horiz = Math.abs((item.bbox.x0 + item.bbox.x1) / 2 - (c.bbox.x0 + c.bbox.x1) / 2)
          if (vert >= -10 && vert < 360 && horiz < 260) {
            const d = horiz + vert * 0.4
            if (d < minD) {
              minD = d
              best = c
            }
          }
        }

        if (best) {
          if (item.type === 'method') {
            best.methods.push(parseMethod(item.line))
          } else {
            best.attributes.push(parseAttribute(item.line))
          }
          best.fullBbox.x0 = Math.min(best.fullBbox.x0, item.bbox.x0)
          best.fullBbox.y0 = Math.min(best.fullBbox.y0, item.bbox.y0)
          best.fullBbox.x1 = Math.max(best.fullBbox.x1, item.bbox.x1)
          best.fullBbox.y1 = Math.max(best.fullBbox.y1, item.bbox.y1)
        } else {
          unassigned.push({ text: item.line, bbox: item.bbox })
        }
      }

      // 4. Normalizar coordenadas de clases preservando la topología fiel de la imagen
      if (classes.length > 0) {
        const minX = Math.min(...classes.map(c => c.bbox.x0))
        const maxX = Math.max(...classes.map(c => c.bbox.x0))
        const minY = Math.min(...classes.map(c => c.bbox.y0))
        const maxY = Math.max(...classes.map(c => c.bbox.y0))
        const spanX = maxX > minX ? maxX - minX : 1
        const spanY = maxY > minY ? maxY - minY : 1

        for (const c of classes) {
          c.posX = Math.round(80 + ((c.bbox.x0 - minX) / spanX) * 750)
          c.posY = Math.round(60 + ((c.bbox.y0 - minY) / spanY) * 380)
        }
      }

      onProgress?.('Detectando relaciones y multiplicidades...')

      // 5. Extraer tokens de multiplicidad y etiquetas de relación
      const multTokens: { mult: string; bbox: any }[] = []
      const labelTokens: { text: string; bbox: any }[] = []

      for (const u of unassigned) {
        const mult = cleanMultiplicity(u.text)
        if (mult) {
          multTokens.push({ mult, bbox: u.bbox })
        } else {
          labelTokens.push(u)
        }
      }

      const relations: RecognizedRelation[] = []

      // 6. Detección espacial de relaciones basada en etiquetas y dirección de flechas
      for (const u of labelTokens) {
        const raw = u.text.trim()
        const cleanWord = raw.replace(/[>»▶◀<v¥▼▲^:;,\.\-=_]/g, '').trim()

        if (cleanWord.length >= 3) {
          const cx = (u.bbox.x0 + u.bbox.x1) / 2
          const cy = (u.bbox.y0 + u.bbox.y1) / 2

          let direction: 'RIGHT' | 'LEFT' | 'DOWN' | 'UP' = 'RIGHT'
          if (/[v¥▼]/.test(raw) || cleanWord.toLowerCase() === 'contiene') {
            direction = 'DOWN'
          } else if (/[▲\^]/.test(raw)) {
            direction = 'UP'
          } else if (/[<◀«]/.test(raw)) {
            direction = 'LEFT'
          } else if (/[>▶»]/.test(raw)) {
            direction = 'RIGHT'
          }

          let source: TempClass | null = null
          let target: TempClass | null = null

          if (direction === 'RIGHT') {
            let bestLeftDist = Infinity
            let bestRightDist = Infinity
            for (const c of classes) {
              const cCenterY = (c.fullBbox.y0 + c.fullBbox.y1) / 2
              const yDist = Math.abs(cy - cCenterY)
              if (c.fullBbox.x1 <= cx + 40 && yDist < 250) {
                const dist = cx - c.fullBbox.x1 + yDist * 0.5
                if (dist < bestLeftDist) {
                  bestLeftDist = dist
                  source = c
                }
              }
              if (c.fullBbox.x0 >= cx - 40 && yDist < 250) {
                const dist = c.fullBbox.x0 - cx + yDist * 0.5
                if (dist < bestRightDist) {
                  bestRightDist = dist
                  target = c
                }
              }
            }
          } else if (direction === 'DOWN') {
            let bestTopDist = Infinity
            let bestBottomDist = Infinity
            for (const c of classes) {
              const cCenterX = (c.fullBbox.x0 + c.fullBbox.x1) / 2
              const xDist = Math.abs(cx - cCenterX)
              if (c.fullBbox.y1 <= cy + 40 && xDist < 350) {
                const dist = cy - c.fullBbox.y1 + xDist * 0.5
                if (dist < bestTopDist) {
                  bestTopDist = dist
                  source = c
                }
              }
              if (c.fullBbox.y0 >= cy - 40 && xDist < 350) {
                const dist = c.fullBbox.y0 - cy + xDist * 0.5
                if (dist < bestBottomDist) {
                  bestBottomDist = dist
                  target = c
                }
              }
            }
          } else if (direction === 'LEFT') {
            let bestLeftDist = Infinity
            let bestRightDist = Infinity
            for (const c of classes) {
              const cCenterY = (c.fullBbox.y0 + c.fullBbox.y1) / 2
              const yDist = Math.abs(cy - cCenterY)
              if (c.fullBbox.x0 >= cx - 40 && yDist < 250) {
                const dist = c.fullBbox.x0 - cx + yDist * 0.5
                if (dist < bestRightDist) {
                  bestRightDist = dist
                  source = c
                }
              }
              if (c.fullBbox.x1 <= cx + 40 && yDist < 250) {
                const dist = cx - c.fullBbox.x1 + yDist * 0.5
                if (dist < bestLeftDist) {
                  bestLeftDist = dist
                  target = c
                }
              }
            }
          } else if (direction === 'UP') {
            let bestTopDist = Infinity
            let bestBottomDist = Infinity
            for (const c of classes) {
              const cCenterX = (c.fullBbox.x0 + c.fullBbox.x1) / 2
              const xDist = Math.abs(cx - cCenterX)
              if (c.fullBbox.y0 >= cy - 40 && xDist < 350) {
                const dist = c.fullBbox.y0 - cy + xDist * 0.5
                if (dist < bestBottomDist) {
                  bestBottomDist = dist
                  source = c
                }
              }
              if (c.fullBbox.y1 <= cy + 40 && xDist < 350) {
                const dist = cy - c.fullBbox.y1 + xDist * 0.5
                if (dist < bestTopDist) {
                  bestTopDist = dist
                  target = c
                }
              }
            }
          }

          if (source && target && source.name !== target.name) {
            // Calcular multiplicidad por proximidad al punto de conexión origen y destino
            const srcEndpointX = direction === 'RIGHT' ? source.fullBbox.x1 : (source.fullBbox.x0 + source.fullBbox.x1) / 2
            const srcEndpointY = direction === 'DOWN' ? source.fullBbox.y1 : (source.fullBbox.y0 + source.fullBbox.y1) / 2

            const tgtEndpointX = direction === 'RIGHT' ? target.fullBbox.x0 : (target.fullBbox.x0 + target.fullBbox.x1) / 2
            const tgtEndpointY = direction === 'DOWN' ? target.fullBbox.y0 : (target.fullBbox.y0 + target.fullBbox.y1) / 2

            let mSource: string | null = null
            let mTarget: string | null = null
            let minSDist = Infinity
            let minTDist = Infinity

            for (const mt of multTokens) {
              const mcx = (mt.bbox.x0 + mt.bbox.x1) / 2
              const mcy = (mt.bbox.y0 + mt.bbox.y1) / 2

              if (direction === 'DOWN' && mcy < source.fullBbox.y0 + 60) continue
              if (direction === 'RIGHT' && (mcy < Math.min(source.fullBbox.y0, target.fullBbox.y0) - 80 || mcy > Math.max(source.fullBbox.y1, target.fullBbox.y1) + 80)) continue

              const sDist = Math.hypot(mcx - srcEndpointX, mcy - srcEndpointY)
              const tDist = Math.hypot(mcx - tgtEndpointX, mcy - tgtEndpointY)

              if (sDist < tDist) {
                if (sDist < minSDist && sDist < 220) {
                  minSDist = sDist
                  mSource = mt.mult
                }
              } else {
                if (tDist < minTDist && tDist < 220) {
                  minTDist = tDist
                  mTarget = mt.mult
                }
              }
            }

            if (!mSource) mSource = cleanWord.toLowerCase() === 'contiene' ? '1..*' : '1'
            if (!mTarget) mTarget = cleanWord.toLowerCase() === 'tiene' ? '0..1' : cleanWord.toLowerCase() === 'contiene' ? '1' : '0..*'

            if (!relations.some(r => r.source === source!.name && r.target === target!.name)) {
              relations.push({
                source: source.name,
                target: target.name,
                name: cleanWord,
                tipo: 'ASOCIACION',
                multSource: mSource,
                multTarget: mTarget
              })
            }
          }
        }
      }

      // 7. Respaldo Semántico: analizar métodos o atributos que hagan referencia a otras clases
      for (const c of classes) {
        for (const m of c.methods) {
          for (const other of classes) {
            if (other.name !== c.name && m.name.toLowerCase().includes(other.name.toLowerCase())) {
              if (!relations.some(r => (r.source === c.name && r.target === other.name) || (r.source === other.name && r.target === c.name))) {
                relations.push({
                  source: c.name,
                  target: other.name,
                  name: 'asociado',
                  tipo: 'ASOCIACION',
                  multSource: '1',
                  multTarget: m.returnType.includes('List') || m.returnType.includes('Set') ? '*' : '1'
                })
              }
            }
          }
        }
      }

      return {
        classes: classes.map(c => ({
          name: c.name,
          posX: c.posX,
          posY: c.posY,
          attributes: c.attributes,
          methods: c.methods
        })),
        relations,
        source: 'OCR'
      }
    } catch (err) {
      console.warn('Fallo en OCR de navegador:', err)
      return null
    } finally {
      if (worker) {
        try {
          await worker.terminate()
        } catch (_) {}
      }
    }
  }

  const timeoutPromise = new Promise<null>((resolve) => {
    setTimeout(() => {
      console.warn('OCR en navegador excedió 25 segundos. Se delegará al servidor.')
      resolve(null)
    }, 25000)
  })

  return await Promise.race([ocrTask(), timeoutPromise])
}
