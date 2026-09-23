import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../models/diagram.dart';
import '../models/uml_element.dart';
import '../models/uml_relation.dart';
import '../services/diagram_service.dart';
import '../services/import_export_service.dart';
import '../services/uml_element_service.dart';
import '../widgets/class_editor_sheet.dart';
import '../widgets/relation_editor_sheet.dart';
import '../widgets/uml_canvas_viewer.dart';

class DiagramDetailScreen extends StatefulWidget {
  final String projectId;
  final String projectName;
  final String diagramId;
  final String diagramName;

  const DiagramDetailScreen({
    super.key,
    required this.projectId,
    required this.projectName,
    required this.diagramId,
    required this.diagramName,
  });

  @override
  State<DiagramDetailScreen> createState() => _DiagramDetailScreenState();
}

class _DiagramDetailScreenState extends State<DiagramDetailScreen> {
  final _diagramService = DiagramService();
  final _elementService = UmlElementService();
  final _importExportService = ImportExportService();

  DiagramFullViewModel? _diagramData;
  bool _isLoading = true;
  bool _isSaving = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _loadDiagram();
  }

  Future<void> _loadDiagram() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final full = await _diagramService.getDiagramFull(widget.projectId, widget.diagramId);
      setState(() {
        _diagramData = full;
      });
    } catch (e) {
      setState(() {
        _errorMessage = e.toString().replaceFirst('Exception: ', '');
      });
    } finally {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  // --- Element Actions ---

  void _openCreateClassSheet() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => ClassEditorSheet(
        onSave: (name, type, props) async {
          Navigator.of(ctx).pop();
          await _saveNewClass(name, type, props);
        },
      ),
    );
  }

  void _openEditClassSheet(ElementDetailModel element) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => ClassEditorSheet(
        element: element,
        onSave: (name, type, props) async {
          Navigator.of(ctx).pop();
          await _updateClass(element, name, type, props);
        },
      ),
    );
  }

  Future<void> _saveNewClass(String name, String type, UmlPropertiesModel props) async {
    setState(() => _isSaving = true);
    try {
      // Calculate sensible initial placement
      final count = _diagramData?.elements.length ?? 0;
      final posX = 80.0 + (count % 4) * 260.0;
      final posY = 80.0 + (count ~/ 4) * 220.0;

      final newElem = ElementDetailModel(
        id: '', // backend will generate UUID
        diagramId: widget.diagramId,
        nombre: name,
        tipoElemento: type,
        posicionX: posX,
        posicionY: posY,
        ancho: 240,
        alto: 160,
        color: '#ffffff',
        properties: props,
      );

      final saved = await _elementService.saveElement(widget.projectId, widget.diagramId, newElem);

      setState(() {
        _diagramData?.elements.add(saved);
      });

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Clase "$name" creada exitosamente')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error al crear clase: $e')),
      );
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  Future<void> _updateClass(
    ElementDetailModel original,
    String name,
    String type,
    UmlPropertiesModel props,
  ) async {
    setState(() => _isSaving = true);
    try {
      final updated = ElementDetailModel(
        id: original.id,
        diagramId: original.diagramId,
        nombre: name,
        tipoElemento: type,
        posicionX: original.posicionX,
        posicionY: original.posicionY,
        ancho: original.ancho,
        alto: original.alto,
        color: original.color,
        properties: props,
      );

      final saved = await _elementService.saveElement(widget.projectId, widget.diagramId, updated);

      setState(() {
        final idx = _diagramData?.elements.indexWhere((e) => e.id == original.id) ?? -1;
        if (idx != -1) {
          _diagramData?.elements[idx] = saved;
        }
      });

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Clase "$name" actualizada')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error al actualizar clase: $e')),
      );
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  Future<void> _confirmDeleteElement(ElementDetailModel element) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Confirmar Eliminación'),
        content: Text('¿Deseas eliminar la clase "${element.nombre}" y sus relaciones asociadas?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('Cancelar'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: Colors.red, foregroundColor: Colors.white),
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('Eliminar'),
          ),
        ],
      ),
    );

    if (confirm == true) {
      try {
        await _elementService.deleteElement(widget.projectId, widget.diagramId, element.id);
        setState(() {
          _diagramData?.elements.removeWhere((e) => e.id == element.id);
          _diagramData?.relations.removeWhere(
            (r) => r.elementoOrigen == element.id || r.elementoDestino == element.id,
          );
        });
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Clase "${element.nombre}" eliminada')),
        );
      } catch (e) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error al eliminar clase: $e')),
        );
      }
    }
  }

  void _onElementMoved(ElementDetailModel element, double nx, double ny) {
    setState(() {
      final idx = _diagramData?.elements.indexWhere((e) => e.id == element.id) ?? -1;
      if (idx != -1) {
        _diagramData!.elements[idx] = ElementDetailModel(
          id: element.id,
          diagramId: element.diagramId,
          nombre: element.nombre,
          tipoElemento: element.tipoElemento,
          posicionX: nx,
          posicionY: ny,
          ancho: element.ancho,
          alto: element.alto,
          color: element.color,
          properties: element.properties,
        );
      }
    });

    // Debounced or direct background sync
    _elementService.syncPositions(widget.projectId, widget.diagramId, _diagramData?.elements ?? []);
  }

  // --- Relation Actions ---

  void _openAddRelationSheet() {
    final elems = _diagramData?.elements ?? [];
    if (elems.length < 2) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Necesitas al menos 2 clases en el diagrama para crear una relación.')),
      );
      return;
    }

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => RelationEditorSheet(
        elements: elems,
        onSave: (sourceId, targetId, type, name, multOrigin, multDest) async {
          Navigator.of(ctx).pop();
          await _saveRelation(sourceId, targetId, type, name, multOrigin, multDest);
        },
      ),
    );
  }

  Future<void> _saveRelation(
    String sourceId,
    String targetId,
    String type,
    String? name,
    String? multOrigin,
    String? multDest,
  ) async {
    setState(() => _isSaving = true);
    try {
      final newRel = RelationDetailModel(
        id: '',
        diagramId: widget.diagramId,
        elementoOrigen: sourceId,
        elementoDestino: targetId,
        tipoRelacion: type,
        nombreRelacion: name,
        multiplicidadOrigen: multOrigin,
        multiplicidadDestino: multDest,
      );

      final saved = await _elementService.saveRelation(widget.projectId, widget.diagramId, newRel);

      setState(() {
        _diagramData?.relations.add(saved);
      });

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Relación creada con éxito')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error al crear relación: $e')),
      );
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  Future<void> _confirmDeleteRelation(RelationDetailModel relation) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Confirmar Eliminación'),
        content: const Text('¿Deseas eliminar esta relación?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('Cancelar'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: Colors.red, foregroundColor: Colors.white),
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('Eliminar'),
          ),
        ],
      ),
    );

    if (confirm == true) {
      try {
        await _elementService.deleteRelation(widget.projectId, widget.diagramId, relation.id);
        setState(() {
          _diagramData?.relations.removeWhere((r) => r.id == relation.id);
        });
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Relación eliminada')),
        );
      } catch (e) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error al eliminar relación: $e')),
        );
      }
    }
  }

  // --- Export & Import Actions ---

  Future<void> _exportDiagram(String format) async {
    try {
      String content;
      if (format == 'XMI') {
        content = await _importExportService.exportXmi(widget.projectId, widget.diagramId);
      } else {
        content = await _importExportService.exportJson(widget.projectId, widget.diagramId);
      }

      if (!mounted) return;

      showDialog(
        context: context,
        builder: (ctx) => AlertDialog(
          title: Text('Exportar $format'),
          content: SizedBox(
            width: double.maxFinite,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  format == 'XMI' ? 'Estándar OMG XMI 2.1 (con cardinalidades):' : 'Backup JSON Completo:',
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                ),
                const SizedBox(height: 8),
                Container(
                  padding: const EdgeInsets.all(8),
                  height: 220,
                  decoration: BoxDecoration(
                    color: const Color(0xFF1E293B),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: SingleChildScrollView(
                    child: SelectableText(
                      content,
                      style: const TextStyle(color: Color(0xFF38BDF8), fontSize: 11, fontFamily: 'monospace'),
                    ),
                  ),
                ),
              ],
            ),
          ),
          actions: [
            TextButton.icon(
              icon: const Icon(Icons.copy, size: 16),
              label: const Text('Copiar'),
              onPressed: () {
                Clipboard.setData(ClipboardData(text: content));
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(content: Text('Contenido $format copiado al portapapeles')),
                );
              },
            ),
            ElevatedButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('Cerrar'),
            ),
          ],
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error al exportar: $e')),
      );
    }
  }

  Future<void> _showImportDialog(String type) async {
    final textController = TextEditingController();
    String defaultHint = '';
    String title = '';

    if (type == 'SQL') {
      title = 'Importar SQL a UML (CU11)';
      defaultHint = '''CREATE TABLE cliente (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    email VARCHAR(150)
);

CREATE TABLE pedido (
    id SERIAL PRIMARY KEY,
    cliente_id INT REFERENCES cliente(id),
    fecha TIMESTAMP NOT NULL
);''';
    } else if (type == 'JSON') {
      title = 'Importar JSON a UML (CU13)';
      defaultHint = '{\n  "version": "1.0",\n  "elements": [...],\n  "relations": [...]\n}';
    } else {
      title = 'Importar XMI a UML (CU13)';
      defaultHint = '<?xml version="1.0" encoding="UTF-8"?>\n<xmi:XMI ...>';
    }

    textController.text = defaultHint;

    final shouldImport = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(title),
        content: SizedBox(
          width: double.maxFinite,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: textController,
                maxLines: 10,
                style: const TextStyle(fontFamily: 'monospace', fontSize: 12),
                decoration: InputDecoration(
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(6)),
                  hintText: 'Pega aquí el código $type...',
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('Cancelar'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('Importar'),
          ),
        ],
      ),
    );

    if (shouldImport == true && textController.text.trim().isNotEmpty) {
      setState(() => _isLoading = true);
      try {
        DiagramFullViewModel imported;
        if (type == 'SQL') {
          imported = await _importExportService.importSql(
            widget.projectId,
            widget.diagramId,
            textController.text.trim(),
          );
        } else if (type == 'JSON') {
          imported = await _importExportService.importJson(
            widget.projectId,
            widget.diagramId,
            textController.text.trim(),
          );
        } else {
          imported = await _importExportService.importXmi(
            widget.projectId,
            widget.diagramId,
            textController.text.trim(),
          );
        }

        setState(() {
          _diagramData = imported;
        });

        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Importación $type completada correctamente')),
        );
      } catch (e) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error al importar: $e')),
        );
      } finally {
        if (mounted) setState(() => _isLoading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              widget.diagramName,
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
            Row(
              children: [
                Text(
                  widget.projectName,
                  style: const TextStyle(fontSize: 11, color: Colors.white70),
                ),
                const SizedBox(width: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
                  decoration: BoxDecoration(
                    color: _isSaving ? Colors.amber : const Color(0xFF10B981),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Text(
                    _isSaving ? 'Guardando...' : '🟢 En línea',
                    style: const TextStyle(fontSize: 9, color: Colors.white, fontWeight: FontWeight.bold),
                  ),
                ),
              ],
            ),
          ],
        ),
        actions: [
          // Add Relation Button
          IconButton(
            icon: const Icon(Icons.linear_scale),
            tooltip: 'Crear Relación',
            onPressed: _openAddRelationSheet,
          ),
          // Refresh Button
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Recargar Diagrama',
            onPressed: _loadDiagram,
          ),
          // Export/Import Menu
          PopupMenuButton<String>(
            icon: const Icon(Icons.more_vert),
            tooltip: 'Exportar / Importar',
            onSelected: (val) {
              switch (val) {
                case 'export_xmi':
                  _exportDiagram('XMI');
                  break;
                case 'export_json':
                  _exportDiagram('JSON');
                  break;
                case 'import_sql':
                  _showImportDialog('SQL');
                  break;
                case 'import_json':
                  _showImportDialog('JSON');
                  break;
                case 'import_xmi':
                  _showImportDialog('XMI');
                  break;
              }
            },
            itemBuilder: (ctx) => [
              const PopupMenuItem(
                value: 'export_xmi',
                child: Row(
                  children: [
                    Icon(Icons.download, size: 18, color: Colors.blue),
                    SizedBox(width: 8),
                    Text('Exportar XMI (CU13)'),
                  ],
                ),
              ),
              const PopupMenuItem(
                value: 'export_json',
                child: Row(
                  children: [
                    Icon(Icons.data_object, size: 18, color: Colors.indigo),
                    SizedBox(width: 8),
                    Text('Exportar JSON (CU13)'),
                  ],
                ),
              ),
              const PopupMenuDivider(),
              const PopupMenuItem(
                value: 'import_sql',
                child: Row(
                  children: [
                    Icon(Icons.storage, size: 18, color: Colors.teal),
                    SizedBox(width: 8),
                    Text('Importar SQL (CU11)'),
                  ],
                ),
              ),
              const PopupMenuItem(
                value: 'import_json',
                child: Row(
                  children: [
                    Icon(Icons.file_upload_outlined, size: 18, color: Colors.orange),
                    SizedBox(width: 8),
                    Text('Importar JSON'),
                  ],
                ),
              ),
              const PopupMenuItem(
                value: 'import_xmi',
                child: Row(
                  children: [
                    Icon(Icons.code, size: 18, color: Colors.purple),
                    SizedBox(width: 8),
                    Text('Importar XMI'),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _openCreateClassSheet,
        backgroundColor: const Color(0xFF2563EB),
        foregroundColor: Colors.white,
        icon: const Icon(Icons.add_box_outlined),
        label: const Text('Nueva Clase'),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _errorMessage != null
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24.0),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Icon(Icons.error_outline, size: 48, color: Colors.red),
                        const SizedBox(height: 12),
                        Text(_errorMessage!, textAlign: TextAlign.center),
                        const SizedBox(height: 16),
                        ElevatedButton(
                          onPressed: _loadDiagram,
                          child: const Text('Reintentar'),
                        ),
                      ],
                    ),
                  ),
                )
              : UmlCanvasViewer(
                  elements: _diagramData?.elements ?? [],
                  relations: _diagramData?.relations ?? [],
                  onElementTap: _openEditClassSheet,
                  onElementDelete: _confirmDeleteElement,
                  onRelationDelete: _confirmDeleteRelation,
                  onElementMoved: _onElementMoved,
                ),
    );
  }
}
