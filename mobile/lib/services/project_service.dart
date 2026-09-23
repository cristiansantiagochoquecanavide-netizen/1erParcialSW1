import '../core/api_client.dart';
import '../models/project.dart';

class ProjectService {
  final ApiClient _client = ApiClient.instance;

  Future<List<ProjectModel>> listProjects() async {
    final res = await _client.get('/api/projects');
    if (res is List) {
      return res.map((p) => ProjectModel.fromJson(Map<String, dynamic>.from(p))).toList();
    }
    return [];
  }

  Future<ProjectModel> createProject(String name, String? description) async {
    final res = await _client.post('/api/projects', {
      'name': name.trim(),
      'description': description?.trim(),
    });
    return ProjectModel.fromJson(res);
  }
}
