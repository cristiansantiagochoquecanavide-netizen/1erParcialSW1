const List<String> kUmlRelationTypes = [
  'ASOCIACION',
  'HERENCIA',
  'AGREGACION',
  'COMPOSICION',
  'DEPENDENCIA',
];

class RelationDetailModel {
  final String id;
  final String diagramaId;
  String elementoOrigen;
  String elementoDestino;
  String? nombreRelacion;
  String tipoRelacion;
  String? multiplicidadOrigen;
  String? multiplicidadDestino;

  String get diagramId => diagramaId;

  RelationDetailModel({
    required this.id,
    String? diagramaId,
    String? diagramId,
    required this.elementoOrigen,
    required this.elementoDestino,
    this.nombreRelacion,
    this.tipoRelacion = 'ASOCIACION',
    this.multiplicidadOrigen,
    this.multiplicidadDestino,
  }) : diagramaId = diagramaId ?? diagramId ?? '';

  factory RelationDetailModel.fromJson(Map<String, dynamic> json) {
    return RelationDetailModel(
      id: json['id'] ?? '',
      diagramaId: json['diagramaId'] ?? '',
      elementoOrigen: json['elementoOrigen'] ?? '',
      elementoDestino: json['elementoDestino'] ?? '',
      nombreRelacion: json['nombreRelacion'],
      tipoRelacion: json['tipoRelacion'] ?? 'ASOCIACION',
      multiplicidadOrigen: json['multiplicidadOrigen'],
      multiplicidadDestino: json['multiplicidadDestino'],
    );
  }

  Map<String, dynamic> toSaveJson() => {
        'id': id.isEmpty ? null : id,
        'elementoOrigen': elementoOrigen,
        'elementoDestino': elementoDestino,
        'nombreRelacion': nombreRelacion,
        'tipoRelacion': tipoRelacion,
        'multiplicidadOrigen': multiplicidadOrigen,
        'multiplicidadDestino': multiplicidadDestino,
      };
}
