import '../core/api_client.dart';
import '../models/auth_user.dart';

class AuthService {
  final ApiClient _client = ApiClient.instance;

  Future<AuthUser> login(String email, String password) async {
    final res = await _client.post('/api/auth/login', {
      'email': email.trim(),
      'password': password,
    });

    final authUser = AuthUser.fromJson(res);
    _client.setSession(token: authUser.token, email: authUser.email, name: authUser.name);
    return authUser;
  }

  Future<AuthUser> register(String email, String name, String password) async {
    final res = await _client.post('/api/auth/register', {
      'email': email.trim(),
      'name': name.trim(),
      'password': password,
    });

    final authUser = AuthUser.fromJson(res);
    _client.setSession(token: authUser.token, email: authUser.email, name: authUser.name);
    return authUser;
  }

  void logout() {
    _client.clearSession();
  }
}
