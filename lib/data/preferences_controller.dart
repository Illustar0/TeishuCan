import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../domain/theme_settings.dart';

class PreferencesController extends ChangeNotifier {
  PreferencesController(this._prefs) {
    _load();
  }

  static const String _themeKey = 'teishucan.theme_settings.v1';
  static const String _languageKey = 'teishucan.app_language.v1';

  final SharedPreferences _prefs;

  ThemeSettings _theme = const ThemeSettings();
  AppLanguage _language = AppLanguage.system;

  ThemeSettings get theme => _theme;
  AppLanguage get language => _language;

  void _load() {
    final themeRaw = _prefs.getString(_themeKey);
    if (themeRaw != null && themeRaw.isNotEmpty) {
      try {
        final decoded = jsonDecode(themeRaw);
        if (decoded is Map<String, dynamic>) {
          _theme = ThemeSettings.fromJson(decoded);
        }
      } catch (_) {
        _theme = const ThemeSettings();
      }
    }

    final langRaw = _prefs.getString(_languageKey);
    if (langRaw != null) {
      for (final item in AppLanguage.values) {
        if (item.name == langRaw) {
          _language = item;
          break;
        }
      }
    }
  }

  Future<void> updateTheme(ThemeSettings Function(ThemeSettings) transform) async {
    _theme = transform(_theme);
    notifyListeners();
    await _prefs.setString(_themeKey, jsonEncode(_theme.toJson()));
  }

  Future<void> setLanguage(AppLanguage language) async {
    if (_language == language) return;
    _language = language;
    notifyListeners();
    await _prefs.setString(_languageKey, language.name);
  }
}
