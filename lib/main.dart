import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'app.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final prefs = await SharedPreferences.getInstance();
  final deps = AppDependencies.create(prefs: prefs);
  await deps.session.init();
  runApp(TeishuCanApp(deps: deps));
}
