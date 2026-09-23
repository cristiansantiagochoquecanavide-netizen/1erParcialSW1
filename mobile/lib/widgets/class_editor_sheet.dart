import 'package:flutter/material.dart';
import '../models/uml_element.dart';

class ClassEditorSheet extends StatefulWidget {
  final ElementDetailModel? element;
  final Function(String name, String type, UmlPropertiesModel properties) onSave;

  const ClassEditorSheet({
    super.key,
    this.element,
    required this.onSave,
  });

  @override
  State<ClassEditorSheet> createState() => _ClassEditorSheetState();
}

class _ClassEditorSheetState extends State<ClassEditorSheet> {
  final _formKey = GlobalKey<FormState>();
  late TextEditingController _nameController;
  late TextEditingController _descController;
  late TextEditingController _stereoController;
  String _selectedType = 'CLASE';

  late List<UmlAttributeModel> _attributes;
  late List<UmlMethodModel> _methods;

  @override
  void initState() {
    super.initState();
    final el = widget.element;
    _nameController = TextEditingController(text: el?.nombre ?? 'NuevaClase');
    _selectedType = el?.tipoElemento ?? 'CLASE';
    _descController = TextEditingController(text: el?.properties.description ?? '');
    _stereoController = TextEditingController(text: el?.properties.stereotype ?? '');

    _attributes = el != null
        ? el.properties.attributes
            .map((a) => UmlAttributeModel(
                  id: a.id,
                  visibility: a.visibility,
                  name: a.name,
                  type: a.type,
                ))
            .toList()
        : [];

    _methods = el != null
        ? el.properties.methods
            .map((m) => UmlMethodModel(
                  id: m.id,
                  visibility: m.visibility,
                  name: m.name,
                  returnType: m.returnType,
                  parameters: m.parameters,
                ))
            .toList()
        : [];
  }

  @override
  void dispose() {
    _nameController.dispose();
    _descController.dispose();
    _stereoController.dispose();
    super.dispose();
  }

  void _addAttribute() {
    setState(() {
      _attributes.add(UmlAttributeModel(
        id: 'attr_${DateTime.now().millisecondsSinceEpoch}',
        visibility: '+',
        name: 'nuevoAtributo',
        type: 'String',
      ));
    });
  }

  void _addMethod() {
    setState(() {
      _methods.add(UmlMethodModel(
        id: 'meth_${DateTime.now().millisecondsSinceEpoch}',
        visibility: '+',
        name: 'nuevoMetodo',
        returnType: 'void',
      ));
    });
  }

  void _submit() {
    if (!_formKey.currentState!.validate()) return;
    final props = UmlPropertiesModel(
      description: _descController.text.trim().isEmpty ? null : _descController.text.trim(),
      stereotype: _stereoController.text.trim().isEmpty ? null : _stereoController.text.trim(),
      attributes: _attributes,
      methods: _methods,
    );
    widget.onSave(_nameController.text.trim(), _selectedType, props);
    Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;
    final isEditing = widget.element != null;

    return Container(
      padding: EdgeInsets.only(
        left: 16,
        right: 16,
        top: 16,
        bottom: bottomInset + 16,
      ),
      constraints: BoxConstraints(
        maxHeight: MediaQuery.of(context).size.height * 0.88,
      ),
      child: Form(
        key: _formKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  isEditing ? 'Editar Clase UML' : 'Crear Nueva Clase',
                  style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                ),
                IconButton(
                  icon: const Icon(Icons.close),
                  onPressed: () => Navigator.of(context).pop(),
                ),
              ],
            ),
            const Divider(),
            Expanded(
              child: ListView(
                children: [
                  TextFormField(
                    controller: _nameController,
                    decoration: const InputDecoration(
                      labelText: 'Nombre de la Clase *',
                      border: OutlineInputBorder(),
                    ),
                    validator: (v) => (v == null || v.trim().isEmpty) ? 'Ingresa el nombre' : null,
                  ),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<String>(
                    initialValue: _selectedType,
                    decoration: const InputDecoration(
                      labelText: 'Tipo de Elemento',
                      border: OutlineInputBorder(),
                    ),
                    items: const [
                      DropdownMenuItem(value: 'CLASE', child: Text('Clase')),
                      DropdownMenuItem(value: 'INTERFAZ', child: Text('Interfaz')),
                      DropdownMenuItem(value: 'CLASE_ABSTRACTA', child: Text('Clase Abstracta')),
                      DropdownMenuItem(value: 'ENUMERACION', child: Text('Enumeración')),
                    ],
                    onChanged: (v) {
                      if (v != null) setState(() => _selectedType = v);
                    },
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: _stereoController,
                    decoration: const InputDecoration(
                      labelText: 'Estereotipo (ej. Entity, DTO, Service)',
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: _descController,
                    maxLines: 2,
                    decoration: const InputDecoration(
                      labelText: 'Descripción del Elemento',
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 16),

                  // SECCIÓN ATRIBUTOS
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'Atributos (${_attributes.length})',
                        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                      ),
                      TextButton.icon(
                        icon: const Icon(Icons.add, size: 18),
                        label: const Text('+ Atributo'),
                        onPressed: _addAttribute,
                      ),
                    ],
                  ),
                  if (_attributes.isEmpty)
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 8.0),
                      child: Text(
                        'Sin atributos definidos.',
                        style: TextStyle(color: Colors.grey, fontStyle: FontStyle.italic),
                      ),
                    ),
                  ..._attributes.asMap().entries.map((entry) {
                    final idx = entry.key;
                    final attr = entry.value;
                    return Card(
                      margin: const EdgeInsets.symmetric(vertical: 4),
                      color: Colors.grey.shade50,
                      child: Padding(
                        padding: const EdgeInsets.all(8.0),
                        child: Row(
                          children: [
                            // Visibilidad
                            SizedBox(
                              width: 50,
                              child: DropdownButton<String>(
                                value: attr.visibility,
                                isExpanded: true,
                                underline: const SizedBox(),
                                items: const [
                                  DropdownMenuItem(value: '+', child: Text('+')),
                                  DropdownMenuItem(value: '-', child: Text('-')),
                                  DropdownMenuItem(value: '#', child: Text('#')),
                                  DropdownMenuItem(value: '~', child: Text('~')),
                                ],
                                onChanged: (v) {
                                  if (v != null) setState(() => attr.visibility = v);
                                },
                              ),
                            ),
                            const SizedBox(width: 8),
                            // Nombre
                            Expanded(
                              flex: 3,
                              child: TextFormField(
                                initialValue: attr.name,
                                decoration: const InputDecoration(
                                  labelText: 'Nombre',
                                  isDense: true,
                                  border: OutlineInputBorder(),
                                ),
                                onChanged: (v) => attr.name = v,
                              ),
                            ),
                            const SizedBox(width: 8),
                            // Tipo (Dropdown con kUmlDataTypes)
                            Expanded(
                              flex: 3,
                              child: DropdownButtonFormField<String>(
                                initialValue: kUmlDataTypes.contains(attr.type) ? attr.type : 'String',
                                isDense: true,
                                decoration: const InputDecoration(
                                  labelText: 'Tipo',
                                  isDense: true,
                                  border: OutlineInputBorder(),
                                ),
                                items: kUmlDataTypes
                                    .map((t) => DropdownMenuItem(value: t, child: Text(t, style: const TextStyle(fontSize: 13))))
                                    .toList(),
                                onChanged: (v) {
                                  if (v != null) setState(() => attr.type = v);
                                },
                              ),
                            ),
                            IconButton(
                              icon: const Icon(Icons.delete_outline, color: Colors.red),
                              onPressed: () {
                                setState(() => _attributes.removeAt(idx));
                              },
                            ),
                          ],
                        ),
                      ),
                    );
                  }),

                  const SizedBox(height: 16),

                  // SECCIÓN MÉTODOS
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'Métodos (${_methods.length})',
                        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                      ),
                      TextButton.icon(
                        icon: const Icon(Icons.add, size: 18),
                        label: const Text('+ Método'),
                        onPressed: _addMethod,
                      ),
                    ],
                  ),
                  if (_methods.isEmpty)
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 8.0),
                      child: Text(
                        'Sin métodos definidos.',
                        style: TextStyle(color: Colors.grey, fontStyle: FontStyle.italic),
                      ),
                    ),
                  ..._methods.asMap().entries.map((entry) {
                    final idx = entry.key;
                    final m = entry.value;
                    return Card(
                      margin: const EdgeInsets.symmetric(vertical: 4),
                      color: Colors.grey.shade50,
                      child: Padding(
                        padding: const EdgeInsets.all(8.0),
                        child: Row(
                          children: [
                            SizedBox(
                              width: 50,
                              child: DropdownButton<String>(
                                value: m.visibility,
                                isExpanded: true,
                                underline: const SizedBox(),
                                items: const [
                                  DropdownMenuItem(value: '+', child: Text('+')),
                                  DropdownMenuItem(value: '-', child: Text('-')),
                                  DropdownMenuItem(value: '#', child: Text('#')),
                                  DropdownMenuItem(value: '~', child: Text('~')),
                                ],
                                onChanged: (v) {
                                  if (v != null) setState(() => m.visibility = v);
                                },
                              ),
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              flex: 3,
                              child: TextFormField(
                                initialValue: m.name,
                                decoration: const InputDecoration(
                                  labelText: 'Método()',
                                  isDense: true,
                                  border: OutlineInputBorder(),
                                ),
                                onChanged: (v) => m.name = v,
                              ),
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              flex: 2,
                              child: TextFormField(
                                initialValue: m.returnType,
                                decoration: const InputDecoration(
                                  labelText: 'Retorno',
                                  isDense: true,
                                  border: OutlineInputBorder(),
                                ),
                                onChanged: (v) => m.returnType = v,
                              ),
                            ),
                            IconButton(
                              icon: const Icon(Icons.delete_outline, color: Colors.red),
                              onPressed: () {
                                setState(() => _methods.removeAt(idx));
                              },
                            ),
                          ],
                        ),
                      ),
                    );
                  }),
                ],
              ),
            ),
            const SizedBox(height: 12),
            ElevatedButton(
              onPressed: _submit,
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF2563EB),
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 14),
              ),
              child: Text(isEditing ? 'Guardar Cambios' : 'Crear Clase UML'),
            ),
          ],
        ),
      ),
    );
  }
}
