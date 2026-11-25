import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:teishucan/app.dart';
import 'package:teishucan/data/session_store.dart';
import 'package:teishucan/domain/theme_settings.dart';
import 'package:teishucan/l10n/app_strings.dart';
import 'package:teishucan/ui/pages/services/services_page.dart';
import 'package:teishucan/ui/pages/settings/about_page.dart';
import 'package:teishucan/ui/pages/settings/open_source_licenses_page.dart';
import 'package:teishucan/ui/theme/app_theme.dart';
import 'package:teishucan/ui/widgets/teishucan_logo.dart';

class _InMemorySecureStore implements KeyValueSecureStore {
  final Map<String, String> _values = <String, String>{};

  @override
  Future<void> delete(String key) async => _values.remove(key);

  @override
  Future<String?> read(String key) async => _values[key];

  @override
  Future<void> write(String key, String value) async => _values[key] = value;
}

Future<AppDependencies> _createTestDeps({
  Map<String, Object> initialPrefs = const <String, Object>{},
}) async {
  SharedPreferences.setMockInitialValues(<String, Object>{
    'teishucan.app_language.v1': 'chinese',
    ...initialPrefs,
  });
  final prefs = await SharedPreferences.getInstance();
  final deps = AppDependencies.create(
    prefs: prefs,
    secureStore: _InMemorySecureStore(),
  );
  await deps.session.init();
  return deps;
}

void main() {
  testWidgets('Signed-out home page opens login sheet and validates inputs',
      (WidgetTester tester) async {
    final deps = await _createTestDeps();
    addTearDown(deps.dispose);

    await tester.pumpWidget(TeishuCanApp(deps: deps));
    await tester.pumpAndSettle();

    expect(find.text('未登录'), findsOneWidget);
    expect(find.text('点击登录'), findsOneWidget);
    expect(find.text('支持开发'), findsOneWidget);

    await tester.tap(find.text('点击登录'));
    await tester.pumpAndSettle();

    expect(find.text('账户'), findsOneWidget);
    expect(find.text('密码'), findsOneWidget);
    expect(find.text('登录'), findsWidgets);

    final loginButton = tester.widget<FilledButton>(find.byType(FilledButton));
    expect(loginButton.onPressed, isNull);

    await tester.enterText(find.byType(TextField).at(0), '20260001');
    await tester.enterText(find.byType(TextField).at(1), 'secret');
    await tester.pump();

    final enabledLoginButton =
        tester.widget<FilledButton>(find.byType(FilledButton));
    expect(enabledLoginButton.onPressed, isNotNull);
  });

  testWidgets('Theme settings toggles Monet, palette options, blur, and glass',
      (WidgetTester tester) async {
    final deps = await _createTestDeps();
    addTearDown(deps.dispose);

    await tester.pumpWidget(TeishuCanApp(deps: deps));
    await tester.pumpAndSettle();

    await tester.tap(find.text('设置'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('主题'));
    await tester.pumpAndSettle();

    expect(find.text('Monet 配色'), findsOneWidget);
    expect(find.text('配色风格'), findsNothing);
    expect(find.text('液态玻璃'), findsOneWidget);
    expect(deps.preferences.theme.isMonetEnabled, isFalse);

    await tester.tap(find.text('Monet 配色'));
    await tester.pumpAndSettle();

    expect(deps.preferences.theme.isMonetEnabled, isTrue);
    expect(find.text('配色风格'), findsOneWidget);
    expect(find.text('主题色'), findsOneWidget);

    await tester.drag(find.byType(ListView), const Offset(0, -300));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('液态玻璃'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('液态玻璃'));
    await tester.pumpAndSettle();
    expect(deps.preferences.theme.isLiquidGlassEnabled, isTrue);

    await tester.ensureVisible(find.text('模糊效果'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('模糊效果'));
    await tester.pumpAndSettle();
    expect(deps.preferences.theme.isBlurEnabled, isFalse);
  });

  testWidgets('Services page shows campus network and filters search queries',
      (WidgetTester tester) async {
    final deps = await _createTestDeps();
    addTearDown(deps.dispose);

    var openedNetwork = false;
    final strings = AppStrings.resolve(AppLanguage.chinese, null);
    await tester.pumpWidget(
      MaterialApp(
        theme: buildTeishuTheme(const ThemeSettings(), Brightness.light),
        home: ServicesPage(
          strings: strings,
          repository: deps.services,
          isBlurEnabled: false,
          bottomPadding: 0,
          onOpenUndergradEas: () {},
          onOpenCampusNetwork: () => openedNetwork = true,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('校园网认证与设备'), findsOneWidget);
    expect(find.text('图书馆门户'), findsOneWidget);
    expect(find.text('一卡通'), findsOneWidget);

    await tester.tap(find.text('校园网认证与设备'));
    await tester.pump();
    expect(openedNetwork, isTrue);

    await tester.enterText(find.byType(TextField), '成绩');
    await tester.pumpAndSettle();

    expect(find.text('查询课程成绩'), findsOneWidget);
    expect(find.text('图书馆门户'), findsNothing);

    await tester.enterText(find.byType(TextField), '不存在的服务xyz');
    await tester.pumpAndSettle();

    expect(find.text('未找到相关服务'), findsOneWidget);
  });

  testWidgets('About page renders TeishuCanLogo and navigates to licenses',
      (WidgetTester tester) async {
    LicenseRegistry.addLicense(
      () => Stream<LicenseEntry>.value(
        const LicenseEntryWithLineBreaks(
          <String>['teishucan_test_pkg'],
          'GPL-3.0-or-later test notice',
        ),
      ),
    );

    final strings = AppStrings.resolve(AppLanguage.chinese, null);
    await tester.pumpWidget(
      MaterialApp(
        theme: buildTeishuTheme(const ThemeSettings(), Brightness.light),
        home: AboutPage(strings: strings, isBlurEnabled: false),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(TeishuCanLogo), findsOneWidget);
    expect(find.text('0.3.1 (2)'), findsOneWidget);

    await tester.tap(find.text('开源许可证'));
    await tester.pumpAndSettle();

    expect(find.byType(OpenSourceLicensesPage), findsOneWidget);
    expect(find.text('teishucan_test_pkg'), findsOneWidget);
  });
}
