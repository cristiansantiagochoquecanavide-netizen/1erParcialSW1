class AuthUser {
  final String token;
  final String email;
  final String name;

  AuthUser({required this.token, required this.email, required this.name});

  factory AuthUser.fromJson(Map<String, dynamic> json) {
    return AuthUser(
      token: json['token'] ?? '',
      email: json['email'] ?? '',
      name: json['name'] ?? '',
    );
  }
}
