import '../core/api_client.dart';
import '../models/diagram.dart';

class DiagramService {
  final ApiClient _client = ApiClient.instance;

  Future<List<DiagramSummaryModel>> listDiagrams(String projectId) async {
    final res = await _client.get('/api/projects/$projectId/diagrams');
    if (res is List) {
      return res.map((d) => DiagramSummaryModel.fromJson(Map<String, dynamic>.from(d))).toList();
    }
    return [];
  }

  Future<DiagramSummaryModel> getDefaultDiagram(String projectId) async {
    final res = await _client.get('/api/projects/$projectId/diagrams/default');
    return DiagramSummaryModel.fromJson(res);
  }

  Future<DiagramFullViewModel> getDiagramFull(String projectId, String diagramId) async {
    final res = await _client.get('/api/projects/$projectId/diagrams/$diagramId');
    return DiagramFullViewModel.fromJson(res);
  }

  Future<DiagramSummaryModel> createDiagram(String projectId, String name, String? description) async {
    final res = await _client.post('/api/projects/$projectId/diagrams', {
      'nombre': name.trim(),
      'descripcion': description?.trim(),
    });
    return DiagramSummaryModel.fromJson(res);
  }
}
