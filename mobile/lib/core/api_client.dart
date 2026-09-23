import 'dart:convert';
import 'package:http/http.dart' as http;

class ApiException implements Exception {
  final String message;
  final int? statusCode;

  ApiException(this.message, [this.statusCode]);

  @override
  String toString() => message;
}

class ApiClient {
  static final ApiClient instance = ApiClient._();
  ApiClient._();

  String baseUrl = 'http://localhost:8080';
  String? token;
  String? userEmail;
  String? userName;

  bool get isAuthenticated => token != null && token!.isNotEmpty;

  void setSession({required String token, required String email, required String name}) {
    this.token = token;
    userEmail = email;
    userName = name;
  }

  void clearSession() {
    token = null;
    userEmail = null;
    userName = null;
  }

  Map<String, String> _headers([Map<String, String>? extra]) {
    final map = <String, String>{
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    };
    if (token != null && token!.isNotEmpty) {
      map['Authorization'] = 'Bearer $token';
    }
    if (extra != null) {
      map.addAll(extra);
    }
    return map;
  }

  Uri _resolveUri(String path) {
    final cleanBase = baseUrl.endsWith('/') ? baseUrl.substring(0, baseUrl.length - 1) : baseUrl;
    final cleanPath = path.startsWith('/') ? path : '/$path';
    return Uri.parse('$cleanBase$cleanPath');
  }

  Future<dynamic> get(String path) async {
    final res = await http.get(_resolveUri(path), headers: _headers());
    return _handleResponse(res);
  }

  Future<dynamic> post(String path, [dynamic body]) async {
    final res = await http.post(
      _resolveUri(path),
      headers: _headers(),
      body: body != null ? jsonEncode(body) : null,
    );
    return _handleResponse(res);
  }

  Future<dynamic> put(String path, [dynamic body]) async {
    final res = await http.put(
      _resolveUri(path),
      headers: _headers(),
      body: body != null ? jsonEncode(body) : null,
    );
    return _handleResponse(res);
  }

  Future<dynamic> delete(String path) async {
    final res = await http.delete(_resolveUri(path), headers: _headers());
    return _handleResponse(res);
  }

  Future<String> getText(String path) async {
    final res = await http.get(_resolveUri(path), headers: _headers());
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return res.body;
    }
    throw ApiException('Error al descargar: ${res.statusCode}', res.statusCode);
  }

  dynamic _handleResponse(http.Response res) {
    if (res.statusCode == 204) {
      return null;
    }
    dynamic decoded;
    try {
      decoded = res.body.isNotEmpty ? jsonDecode(res.body) : null;
    } catch (_) {
      decoded = res.body;
    }

    if (res.statusCode >= 200 && res.statusCode < 300) {
      return decoded;
    }

    String errorMsg = 'Error en el servidor (${res.statusCode})';
    if (decoded is Map<String, dynamic> && decoded['message'] != null) {
      errorMsg = decoded['message'].toString();
    }
    throw ApiException(errorMsg, res.statusCode);
  }
}
