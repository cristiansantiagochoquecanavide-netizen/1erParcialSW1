---
description: Guia de integracion con Sparx Enterprise Architect mediante el servidor MCP enterprise-architect
trigger: model_decision
---

# Enterprise Architect MCP Guide

Trabaja sobre la instancia abierta de Sparx Enterprise Architect mediante las herramientas del MCP `enterprise-architect`.

## Reglas de Interaccion
1. **Requisito indispensable**: Enterprise Architect debe estar abierto con el proyecto/modelo cargado.
2. Antes de modificar, comprueba el estado usando `ea_status` para confirmar la conexion y el proyecto activo.
3. Identifica o localiza el paquete destino usando `ea_list_packages`.
4. Para diagramas de secuencia completos, utiliza `ea_create_sequence_diagram`. Usa operaciones granulares (`ea_add_element`, `ea_add_message`, etc.) para modificar diagramas existentes.
5. Para diagramas de clases nativos, utiliza `ea_create_class_diagram`.

## Convencion Visual de Secuencia
- **Actor**: a la izquierda.
- **Vistas** (`V.*`): antes de los controladores.
- **Controladores**: al centro.
- **Modelos** (`M.*`): a la derecha.
- Mensajes de llamada solidos y retornos punteados.
- Fragmentos combinados `alt` para flujos principales y alternativos.
- Abre y refresca el diagrama con `ea_open_diagram` al finalizar.
