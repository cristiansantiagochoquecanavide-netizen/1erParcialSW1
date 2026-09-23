import 'uml_element.dart';
import 'uml_relation.dart';

class DiagramSummaryModel {
  final String id;
  final String projectId;
  final String nombre;
  final String? descripcion;
  final String estado;
  final String? fechaCreacion;

  DiagramSummaryModel({
    required this.id,
    required this.projectId,
    required this.nombre,
    this.descripcion,
    required this.estado,
    this.fechaCreacion,
  });

  factory DiagramSummaryModel.fromJson(Map<String, dynamic> json) {
    return DiagramSummaryModel(
      id: json['id'] ?? '',
      projectId: json['projectId'] ?? '',
      nombre: json['nombre'] ?? '',
      descripcion: json['descripcion'],
      estado: json['estado'] ?? 'ACTIVO',
      fechaCreacion: json['fechaCreacion'],
    );
  }
}

class DiagramFullViewModel {
  final DiagramSummaryModel diagram;
  final List<ElementDetailModel> elements;
  final List<RelationDetailModel> relations;

  DiagramFullViewModel({
    required this.diagram,
    required this.elements,
    required this.relations,
  });

  factory DiagramFullViewModel.fromJson(Map<String, dynamic> json) {
    final diagMap = Map<String, dynamic>.from(json['diagram'] ?? {});
    final elemsList = json['elements'] as List? ?? [];
    final relsList = json['relations'] as List? ?? [];

    return DiagramFullViewModel(
      diagram: DiagramSummaryModel.fromJson(diagMap),
      elements: elemsList.map((e) => ElementDetailModel.fromJson(Map<String, dynamic>.from(e))).toList(),
      relations: relsList.map((r) => RelationDetailModel.fromJson(Map<String, dynamic>.from(r))).toList(),
    );
  }
}
