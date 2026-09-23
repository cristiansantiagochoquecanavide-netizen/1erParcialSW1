import 'dart:convert';

const List<String> kUmlDataTypes = [
  'String',
  'Integer',
  'Long',
  'Float',
  'Double',
  'Decimal',
  'Boolean',
  'Date',
  'DateTime',
  'Time',
  'Char',
  'Text',
  'UUID',
  'JSON',
  'List',
  'Set',
  'Array',
  'Object',
  'Enum',
];

class UmlAttributeModel {
  String id;
  String visibility;
  String name;
  String type;

  UmlAttributeModel({
    required this.id,
    this.visibility = '+',
    required this.name,
    this.type = 'String',
  });

  factory UmlAttributeModel.fromJson(Map<String, dynamic> json) {
    return UmlAttributeModel(
      id: json['id'] ?? '',
      visibility: json['visibility'] ?? '+',
      name: json['name'] ?? '',
      type: json['type'] ?? 'String',
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'visibility': visibility,
        'name': name,
        'type': type,
      };
}

class UmlMethodModel {
  String id;
  String visibility;
  String name;
  String returnType;
  String parameters;

  UmlMethodModel({
    required this.id,
    this.visibility = '+',
    required this.name,
    this.returnType = 'void',
    this.parameters = '',
  });

  factory UmlMethodModel.fromJson(Map<String, dynamic> json) {
    return UmlMethodModel(
      id: json['id'] ?? '',
      visibility: json['visibility'] ?? '+',
      name: json['name'] ?? '',
      returnType: json['returnType'] ?? 'void',
      parameters: json['parameters'] ?? '',
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'visibility': visibility,
        'name': name,
        'returnType': returnType,
        'parameters': parameters,
      };
}

class UmlPropertiesModel {
  String? description;
  String? stereotype;
  List<UmlAttributeModel> attributes;
  List<UmlMethodModel> methods;

  UmlPropertiesModel({
    this.description,
    this.stereotype,
    List<UmlAttributeModel>? attributes,
    List<UmlMethodModel>? methods,
  })  : attributes = attributes ?? [],
        methods = methods ?? [];

  factory UmlPropertiesModel.fromJson(Map<String, dynamic> json) {
    var rawAttrs = json['attributes'];
    var rawMethods = json['methods'];

    List<UmlAttributeModel> attrs = [];
    if (rawAttrs is List) {
      attrs = rawAttrs.map((a) => UmlAttributeModel.fromJson(Map<String, dynamic>.from(a))).toList();
    }

    List<UmlMethodModel> mthds = [];
    if (rawMethods is List) {
      mthds = rawMethods.map((m) => UmlMethodModel.fromJson(Map<String, dynamic>.from(m))).toList();
    }

    return UmlPropertiesModel(
      description: json['description'],
      stereotype: json['stereotype'],
      attributes: attrs,
      methods: mthds,
    );
  }

  Map<String, dynamic> toJson() => {
        if (description != null) 'description': description,
        if (stereotype != null) 'stereotype': stereotype,
        'attributes': attributes.map((a) => a.toJson()).toList(),
        'methods': methods.map((m) => m.toJson()).toList(),
      };
}

class ElementDetailModel {
  final String id;
  final String diagramaId;
  String tipoElemento;
  String nombre;
  double posicionX;
  double posicionY;
  String propiedadesJson;
  UmlPropertiesModel properties;

  String get diagramId => diagramaId;
  double get ancho => 240.0;
  double get alto => 160.0;
  String get color => '#ffffff';

  ElementDetailModel({
    required this.id,
    String? diagramaId,
    String? diagramId,
    required this.tipoElemento,
    required this.nombre,
    required this.posicionX,
    required this.posicionY,
    String? propiedadesJson,
    UmlPropertiesModel? properties,
    double? ancho,
    double? alto,
    String? color,
  })  : diagramaId = diagramaId ?? diagramId ?? '',
        properties = properties ?? parseProperties(propiedadesJson ?? '{}'),
        propiedadesJson = propiedadesJson ?? (properties != null ? jsonEncode(properties.toJson()) : '{}');


  static UmlPropertiesModel parseProperties(String jsonStr) {
    if (jsonStr.isEmpty) return UmlPropertiesModel();
    try {
      final decoded = jsonDecode(jsonStr);
      if (decoded is Map<String, dynamic>) {
        return UmlPropertiesModel.fromJson(decoded);
      }
    } catch (_) {}
    return UmlPropertiesModel();
  }

  factory ElementDetailModel.fromJson(Map<String, dynamic> json) {
    final rawProps = json['propiedadesJson'] ?? '{}';
    return ElementDetailModel(
      id: json['id'] ?? '',
      diagramaId: json['diagramaId'] ?? '',
      tipoElemento: json['tipoElemento'] ?? 'CLASE',
      nombre: json['nombre'] ?? '',
      posicionX: (json['posicionX'] as num?)?.toDouble() ?? 100.0,
      posicionY: (json['posicionY'] as num?)?.toDouble() ?? 100.0,
      propiedadesJson: rawProps,
    );
  }

  Map<String, dynamic> toSaveJson() => {
        'id': id.isEmpty ? null : id,
        'tipoElemento': tipoElemento,
        'nombre': nombre,
        'posicionX': posicionX,
        'posicionY': posicionY,
        'propiedadesJson': jsonEncode(properties.toJson()),
      };
}
