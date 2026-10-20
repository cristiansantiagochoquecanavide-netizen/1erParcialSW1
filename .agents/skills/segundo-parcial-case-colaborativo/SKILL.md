---
name: segundo-parcial-case-colaborativo
description: Contexto rector y reglas de implementación del segundo parcial de la herramienta CASE UML colaborativa. Usar antes de diseñar, implementar, auditar o probar los bloques 9 a 16.
---

# Skill: Segundo parcial — Agente IA colaborativo y entorno de equipo

## 1. Objetivo y naturaleza del producto
El producto es una **herramienta CASE colaborativa de modelado UML de clases y transformación/generación de software**, NO un sistema administrativo. El segundo parcial agrega un entorno colaborativo tipo Discord dentro del proyecto y un agente de IA que actúa como **miembro activo del equipo**: conversa, conoce quién habla, comprende el contexto del sistema diseñado, supervisa cambios confirmados en el UML y emite recomendaciones proactivas y relevantes. El plazo orientativo es un mes; priorizar funcionalidad demostrable, integración segura y regresión mínima.

## 2. Repositorio y stack que deben inspeccionarse, no presumirse
- Ruta local informada: `D:\apuntes\Examensw1\proyecto-case`.
- Frontend React + TypeScript + Vite, editor UML con `@xyflow/react`.
- Backend Spring Boot 3.4.5, Java 21, JPA, PostgreSQL, Flyway.
- JWT, membresía y roles por proyecto: PROPIETARIO, EDITOR, LECTOR.
- WebSocket existente para colaboración en diagramas y control de concurrencia optimista por revisión.
- Reconocimiento de voz del navegador ya utilizado para comandos UML.
- Inspeccionar código y rutas reales antes de modificar; no suponer que nombres de archivos, endpoints o APIs siguen iguales.

## 3. Estado y límites conocidos
- Bloques 1–7: mejoras de permisos, WebSocket, historial, cola offline, UUID/idempotencia, revisión optimista y auditoría. Informes previos indicaron 87 pruebas backend, 9 frontend offline e integración PostgreSQL; **son resultados históricos, no prueba del estado actual**.
- Bloque 8: diagnóstico y diseño técnico completados, **sin cambios de código ni migraciones**. El diseño original proponía `conversacion_ia`, `mensaje_ia`, `LlmProvider`, contexto UML y acciones revisables. Este documento amplía ese diseño con requisitos posteriores.
- Antigravity informó que `.agents/skills/segundo-parcial-case-colaborativo/SKILL.md` no existía en el checkout inspeccionado. Esta skill se entrega para instalarla allí.
- No asumir que Flyway V7 se ha ejecutado. Comprobar el último número de migración real antes de crear otra.

## 4. Requisitos funcionales acordados
### 4.1 Espacio colaborativo
- Dentro de cada proyecto, un espacio de colaboración tipo Discord: canales de **texto**, canales/salas de **voz**, participantes, presencia y mensajes atribuidos.
- Canal de texto grupal compartido por los miembros autorizados, con historial persistente y difusión en tiempo real cuando se implemente backend.
- Consultas **privadas** con el agente: aisladas por usuario, incluso frente al propietario del proyecto. No difundir por WebSocket grupal ni reutilizar su contenido como memoria grupal sin consentimiento explícito.
- Identidad obtenida de la sesión JWT, no de nombres editables enviados por el cliente. Respuestas directas dirigidas naturalmente por nombre a quien pregunta; recomendaciones automáticas dirigidas a **«Equipo»**.
- Voz entre colaboradores por WebRTC en fase posterior; hablarle al agente debe atribuirse al participante autenticado que transmite la intervención, sin asumir identificación biométrica por voz.

### 4.2 Agente IA como colaborador proactivo
- Analiza el **diagrama UML que está en el lienzo** tras cambios confirmados, sea creado manualmente, por voz/texto, importación XMI, restauración o reconstrucción desde imagen.
- Comprende el dominio de negocio del sistema que se está diseñando y recomienda clases, atributos, métodos si corresponde, relaciones, multiplicidades, restricciones, normalización y mejoras de consistencia.
- **Lienzo vacío o insuficiente:** ofrece al equipo describir el sistema y sus reglas de negocio; es opcional. Si aún no hay contexto y aparecen aproximadamente 2–3 clases, intenta inferir el dominio y pide confirmación. Este umbral es orientativo, no bloqueante.
- **Importación / imagen / restauración masiva:** esperar a finalizar la operación y hacer **una evaluación global** del diagrama, no una recomendación por cada elemento importado; emitir la mejora de mayor prioridad.
- **Voz:** interpretar el contexto del sistema comunicado oralmente, además de comandos explícitos. No inventar órdenes de mutación implícitas en descripciones de contexto.
- **Después de cada modificación confirmada:** programar evaluación; agrupar ráfagas de cambios (debounce/coalescing), deduplicar hallazgos y publicar solo recomendaciones relevantes. Si no hay mejora significativa, no generar ruido.
- Distinguir contexto confirmado por humanos de inferencias de IA; conservar contexto general de proyecto y analizar específicamente el diagrama activo. Memoria de decisiones y recomendaciones para evitar repetir lo ya descartado.
- El agente **propone** modificaciones estructuradas, pero solo ejecuta mediante servicios UML existentes, revisión y autorización explícita de un PROPIETARIO o EDITOR; conflictos 409 y validaciones conservados. No escritura directa a BD.
- Recomendaciones generales en canal grupal, respuestas a preguntas individuales personalizadas por nombre.

### 4.3 Voz del agente (NO prioritaria)
- Deseable que, estando el equipo en canal de voz, el agente **lea** las recomendaciones publicadas.
- Primera versión potencial: TTS del navegador, con opción de silenciar y evitar reproducción múltiple; versión avanzada con API de voz opcional.
- No convertir esto en prerrequisito del análisis UML ni del chat.

### 4.4 Proveedor IA
- **No se ha decidido** Gemini vs OpenAI; evaluar en Bloque 12 por calidad UML, latencia, costos, disponibilidad y facilidad de integración. Nombres de modelos y precios se verificarán en ese momento.
- Implementar un puerto `LlmProvider` intercambiable, configuración segura por variables de entorno, timeout, límites y manejo de fallos.
- IA local/Ollama es opción futura, no prioridad del mes. No enviar credenciales o secretos al proveedor; limitar contexto a información autorizada.

## 5. Arquitectura y seguridad obligatorias
- Respetar acceso por proyecto y diagrama en HTTP, WebSocket, repositorios, historial y contexto.
- Un mensaje privado no puede ser leído por otro colaborador ni difundido al canal público.
- No confiar en IDs de usuario, roles o nombres provistos por frontend; validar servidor.
- No reutilizar historial privado en prompts grupales.
- No exponer claves de API en cliente o repositorio.
- Usar el flujo de revisión optimista e idempotencia existentes para acciones UML; emitir eventos después de commit.
- Tratar texto de diagramas, mensajes e importaciones como datos no confiables frente a prompt injection.
- Evitar llamadas al LLM dentro de transacciones de escritura; usar procesamiento desacoplado y tolerante a errores.
- Las conversaciones y canales deben pertenecer a un proyecto; definir claramente alcance por proyecto y/o diagrama.

## 6. Orden de trabajo aprobado
**Bloque 9 — Interfaz y posición del espacio colaborativo:** panel derecho acoplado, expandible y colapsable; canales de texto/voz visibles, participantes, chat, área privada del agente y estados de conexión; sin afirmar backend o voz real. UI inicial con mocks claramente aislados; no romper lienzo.
**Bloque 10 — Canales de texto reales:** persistencia, autorización, identidad, historial, envío/recepción WebSocket, canales por proyecto, privadas del agente.
**Bloque 11 — Canales de voz reales:** WebRTC, señalización, unirse/salir, mute, presencia, conectividad entre redes y manejo de errores.
**Bloque 12 — Selección e integración LLM:** comparación práctica Gemini/OpenAI y adaptador desacoplado; consultoría técnica real.
**Bloque 13 — Contexto y memoria:** propósito del sistema, reglas de negocio, contexto inicial, contexto de diagrama y decisiones confirmadas; texto y voz.
**Bloque 14 — Supervisión proactiva:** eventos de cambios confirmados, análisis global de importaciones, deduplicación y una recomendación prioritaria en el chat.
**Bloque 15 — Identidad, privacidad y acciones UML:** personalización, control estricto de ámbitos, propuestas de cambios, aprobación humana, concurrencia y auditoría. La seguridad básica de los bloques 10–14 es obligatoria desde su implementación; Bloque 15 la profundiza, NO la posterga.
**Bloque 16 — Voz del agente (opcional):** lectura de recomendaciones en salas de voz con controles y prevención de duplicados.

## 7. Instrucciones concretas del Bloque 9
- Comenzar por **inspeccionar** el editor, sus paneles laterales, layout, rutas, estilos, hooks, WebSocket y componentes reutilizables. Reportar brevemente hallazgos y luego implementar.
- Diseño inicial recomendado: **panel lateral derecho** colapsable/expandible, con opción de vista amplia; lienzo sigue siendo prioridad, tamaño responsivo, sin superposición de controles ni desplazamiento roto. No forzar una ventana externa ni rediseñar toda la aplicación.
- UI tipo espacio de equipo: encabezado del proyecto; lista de canales (p. ej. `# general`, `# diseño-uml`), sección de voz (p. ej. `Sala de diseño`), participantes, mensajes con autor/hora, entrada de texto y área de IA privada. Diseñar también estado de panel vacío y carga/error.
- Mostrar **«Agente CASE IA»** como miembro reconocible del equipo, pero no simular que un LLM responde realmente.
- Si todavía no existe infraestructura de chat, usar mocks locales/fixtures **explícitos y reemplazables**. Deshabilitar o etiquetar acciones sin backend; no mostrar falsos mensajes de éxito. No implementar llamadas de voz, persistencia, endpoints o migraciones en Bloque 9.
- Respetar tipografía legible, contraste, estética profesional, evitar emojis decorativos y mantener controles del UML existentes. Comprobar pantallas pequeñas y zoom.
- Entregar cambios localizados, sin dependencias pesadas innecesarias y sin alterar las 15 definiciones académicas de casos de uso por iniciativa propia.

## 8. Criterios de aceptación del Bloque 9
1. El panel se abre/cierra y cambia de tamaño sin perder estado del diagrama ni tapar herramientas importantes.
2. Se distinguen visualmente canales de texto, salas de voz, participantes y área privada de IA.
3. Navegar entre vistas/canales funciona en la UI; datos demo se distinguen de mensajes reales.
4. No hay errores TypeScript ni fallos de build frontend; ejecutar pruebas disponibles y reportar comandos/resultados reales.
5. No se modificaron migraciones, servicios de IA, endpoints de chat ni voz real salvo que exista una dependencia mínima indispensable y se explique previamente.
6. Presentar archivos modificados, capturas si el entorno permite, límites de la implementación y tareas exactas para Bloque 10.

## 9. Pendientes reservados para cierre final del proyecto
- Prueba manual con **dos usuarios en navegadores reales**: eventos UML, conflictos 409, reconexión y cola offline.
- Compilar y ejecutar backend/frontend generado desde diagramas.
- Revisar documentación y diagramas contra implementación; mantener 15 CU salvo modificación académicamente justificada.
- Guía reproducible de instalación, ejecución y demostración.
- Validar permisos, privacidad, IA proactiva, contexto y voz de punta a punta.
- Regresión completa al cierre. El usuario pidió expresamente que se le recuerde esta lista **al final**, no ahora.

## 10. Disciplina de ejecución para Antigravity
- Trabajar **solo el bloque solicitado**; no adelantar funcionalidades de bloques posteriores.
- Primero inspeccionar código y skill; después cambios pequeños; al final pruebas y reporte.
- Si surge una decisión no confirmada que altera arquitectura o alcance, presentar alternativas y pedir aprobación.
- No afirmar que una funcionalidad funciona sin prueba concreta; distinguir unitarias, integración y pruebas manuales.
