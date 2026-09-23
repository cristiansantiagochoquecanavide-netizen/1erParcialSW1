import 'package:flutter/material.dart';
import '../models/uml_element.dart';
import '../models/uml_relation.dart';

class RelationEditorSheet extends StatefulWidget {
  final List<ElementDetailModel> elements;
  final RelationDetailModel? relation;
  final Function(String sourceId, String targetId, String type, String? name, String? multOrigin, String? multDest) onSave;

  const RelationEditorSheet({
    super.key,
    required this.elements,
    this.relation,
    required this.onSave,
  });

  @override
  State<RelationEditorSheet> createState() => _RelationEditorSheetState();
}

class _RelationEditorSheetState extends State<RelationEditorSheet> {
  final _formKey = GlobalKey<FormState>();
  late String _sourceId;
  late String _targetId;
  late String _type;
  late TextEditingController _nameController;
  late TextEditingController _multOriginController;
  late TextEditingController _multDestController;

  @override
  void initState() {
    super.initState();
    final r = widget.relation;
    _sourceId = r?.elementoOrigen ?? (widget.elements.isNotEmpty ? widget.elements.first.id : '');
    _targetId = r?.elementoDestino ?? (widget.elements.length > 1 ? widget.elements[1].id : (widget.elements.isNotEmpty ? widget.elements.first.id : ''));
    _type = r?.tipoRelacion ?? 'ASOCIACION';
    _nameController = TextEditingController(text: r?.nombreRelacion ?? '');
    _multOriginController = TextEditingController(text: r?.multiplicidadOrigen ?? '1');
    _multDestController = TextEditingController(text: r?.multiplicidadDestino ?? '0..*');
  }

  @override
  void dispose() {
    _nameController.dispose();
    _multOriginController.dispose();
    _multDestController.dispose();
    super.dispose();
  }

  void _submit() {
    if (!_formKey.currentState!.validate()) return;
    if (_sourceId == _targetId) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('El origen y destino de la relación deben ser distintos.')),
      );
      return;
    }
    widget.onSave(
      _sourceId,
      _targetId,
      _type,
      _nameController.text.trim().isEmpty ? null : _nameController.text.trim(),
      _multOriginController.text.trim().isEmpty ? null : _multOriginController.text.trim(),
      _multDestController.text.trim().isEmpty ? null : _multDestController.text.trim(),
    );
    Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;
    return Container(
      padding: EdgeInsets.only(left: 16, right: 16, top: 16, bottom: bottomInset + 16),
      child: Form(
        key: _formKey,
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text('Configurar Relación UML', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                  IconButton(icon: const Icon(Icons.close), onPressed: () => Navigator.of(context).pop()),
                ],
              ),
              const Divider(),
              const SizedBox(height: 8),

              // Clase Origen
              DropdownButtonFormField<String>(
                initialValue: widget.elements.any((e) => e.id == _sourceId) ? _sourceId : widget.elements.firstOrNull?.id,
                decoration: const InputDecoration(labelText: 'Clase Origen', border: OutlineInputBorder()),
                items: widget.elements
                    .map((e) => DropdownMenuItem(value: e.id, child: Text(e.nombre)))
                    .toList(),
                onChanged: (v) {
                  if (v != null) setState(() => _sourceId = v);
                },
              ),
              const SizedBox(height: 12),

              // Clase Destino
              DropdownButtonFormField<String>(
                initialValue: widget.elements.any((e) => e.id == _targetId) ? _targetId : widget.elements.lastOrNull?.id,
                decoration: const InputDecoration(labelText: 'Clase Destino', border: OutlineInputBorder()),
                items: widget.elements
                    .map((e) => DropdownMenuItem(value: e.id, child: Text(e.nombre)))
                    .toList(),
                onChanged: (v) {
                  if (v != null) setState(() => _targetId = v);
                },
              ),
              const SizedBox(height: 12),

              // Tipo de Relación
              DropdownButtonFormField<String>(
                initialValue: _type,
                decoration: const InputDecoration(labelText: 'Tipo de Relación', border: OutlineInputBorder()),
                items: const [
                  DropdownMenuItem(value: 'ASOCIACION', child: Text('Asociación (——)')),
                  DropdownMenuItem(value: 'HERENCIA', child: Text('Generalización / Herencia (——▷)')),
                  DropdownMenuItem(value: 'AGREGACION', child: Text('Agregación (◇——)')),
                  DropdownMenuItem(value: 'COMPOSICION', child: Text('Composición (◆——)')),
                  DropdownMenuItem(value: 'DEPENDENCIA', child: Text('Dependencia (- - ▷)')),
                ],
                onChanged: (v) {
                  if (v != null) setState(() => _type = v);
                },
              ),
              const SizedBox(height: 12),

              // Nombre / Rol
              TextFormField(
                controller: _nameController,
                decoration: const InputDecoration(
                  labelText: 'Nombre / Rol de la Relación (Opcional)',
                  border: OutlineInputBorder(),
                  hintText: 'Ej. pedidos, items, cliente',
                ),
              ),
              const SizedBox(height: 12),

              // Multiplicidades
              Row(
                children: [
                  Expanded(
                    child: TextFormField(
                      controller: _multOriginController,
                      decoration: const InputDecoration(
                        labelText: 'Card. Origen',
                        border: OutlineInputBorder(),
                        hintText: '1, 0..1, *',
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: TextFormField(
                      controller: _multDestController,
                      decoration: const InputDecoration(
                        labelText: 'Card. Destino',
                        border: OutlineInputBorder(),
                        hintText: '0..*, 1..*, 1',
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),

              ElevatedButton(
                onPressed: _submit,
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF2563EB),
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                ),
                child: const Text('Guardar Relación'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
