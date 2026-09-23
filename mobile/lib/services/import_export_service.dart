import '../core/api_client.dart';
import '../models/diagram.dart';

class ImportExportService {
  final ApiClient _client = ApiClient.instance;

  Future<String> exportXmi(String projectId, String diagramId) async {
    return await _client.getText('/api/projects/$projectId/diagrams/$diagramId/xmi/export');
  }

  Future<String> exportJson(String projectId, String diagramId) async {
    return await _client.getText('/api/projects/$projectId/diagrams/$diagramId/xmi/export-json');
  }

  Future<DiagramFullViewModel> importXmi(String projectId, String diagramId, String xmlContent) async {
    final res = await _client.post(
      '/api/projects/$projectId/diagrams/$diagramId/xmi/import',
      xmlContent,
    );
    return DiagramFullViewModel.fromJson(res);
  }

  Future<DiagramFullViewModel> importJson(String projectId, String diagramId, String jsonContent) async {
    final res = await _client.post(
      '/api/projects/$projectId/diagrams/$diagramId/xmi/import-json',
      jsonContent,
    );
    return DiagramFullViewModel.fromJson(res);
  }

  Future<DiagramFullViewModel> importSql(String projectId, String diagramId, String sqlContent) async {
    final res = await _client.post(
      '/api/projects/$projectId/diagrams/$diagramId/import/sql',
      {'sql': sqlContent},
    );
    return DiagramFullViewModel.fromJson(res);
  }
}
