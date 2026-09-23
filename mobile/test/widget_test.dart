import 'package:flutter_test/flutter_test.dart';
import 'package:case_mobile/main.dart';

void main() {
  testWidgets('App smoke test loads LoginScreen', (WidgetTester tester) async {
    await tester.pumpWidget(const CaseAppMobile());
    expect(find.text('CASE UML Editor'), findsOneWidget);
    expect(find.text('Iniciar Sesión'), findsOneWidget);
  });
}
