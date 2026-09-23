import '../core/api_client.dart';
import '../models/uml_element.dart';
import '../models/uml_relation.dart';

class UmlElementService {
  final ApiClient _client = ApiClient.instance;

  Future<ElementDetailModel> saveElement(
    String projectId,
    String diagramId,
    ElementDetailModel element,
  ) async {
    final res = await _client.post(
      '/api/projects/$projectId/diagrams/$diagramId/elements',
      element.toSaveJson(),
    );
    return ElementDetailModel.fromJson(res);
  }

  Future<void> deleteElement(
    String projectId,
    String diagramId,
    String elementId,
  ) async {
    await _client.delete('/api/projects/$projectId/diagrams/$diagramId/elements/$elementId');
  }

  Future<RelationDetailModel> saveRelation(
    String projectId,
    String diagramId,
    RelationDetailModel relation,
  ) async {
    final res = await _client.post(
      '/api/projects/$projectId/diagrams/$diagramId/relations',
      relation.toSaveJson(),
    );
    return RelationDetailModel.fromJson(res);
  }

  Future<void> deleteRelation(
    String projectId,
    String diagramId,
    String relationId,
  ) async {
    await _client.delete('/api/projects/$projectId/diagrams/$diagramId/relations/$relationId');
  }

  Future<void> syncPositions(
    String projectId,
    String diagramId,
    List<ElementDetailModel> elements,
  ) async {
    final payload = {
      'positions': elements
          .map((e) => {
                'id': e.id,
                'posicionX': e.posicionX,
                'posicionY': e.posicionY,
              })
          .toList(),
    };
    await _client.post(
      '/api/projects/$projectId/diagrams/$diagramId/elements/positions',
      payload,
    );
  }
}
