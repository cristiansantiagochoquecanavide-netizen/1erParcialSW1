class ProjectModel {
  final String id;
  final String name;
  final String? description;
  final String role;
  final String? createdAt;

  ProjectModel({
    required this.id,
    required this.name,
    this.description,
    required this.role,
    this.createdAt,
  });

  factory ProjectModel.fromJson(Map<String, dynamic> json) {
    return ProjectModel(
      id: json['id'] ?? '',
      name: json['name'] ?? '',
      description: json['description'],
      role: json['role'] ?? 'LECTOR',
      createdAt: json['createdAt'],
    );
  }
}
