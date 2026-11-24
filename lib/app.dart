import 'package:cupertino_ui/cupertino_ui.dart' show GlobalCupertinoLocalizations;
import 'package:flutter_localizations/flutter_localizations.dart'
    show GlobalWidgetsLocalizations;
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'data/campus_card_repository.dart';
import 'data/campus_refresher.dart';
import 'data/campus_service_repository.dart';
import 'data/electricity_repository.dart';
import 'data/preferences_controller.dart';
import 'data/session_store.dart';
import 'data/snapshot_cache.dart';
import 'data/timetable_repository.dart';
import 'data/zzu_session.dart';
import 'domain/account.dart';
import 'domain/campus_exception.dart';
import 'domain/theme_settings.dart';
import 'l10n/app_strings.dart';
import 'ui/pages/electricity/electricity_settings_page.dart';
import 'ui/pages/home/campus_card_recharge_sheet.dart';
import 'ui/pages/home/home_page.dart';
import 'ui/pages/services/services_page.dart';
import 'ui/pages/services/undergrad_eas_page.dart';
import 'ui/pages/settings/about_page.dart';
import 'ui/pages/settings/login_sheet.dart';
import 'ui/pages/settings/settings_page.dart';
import 'ui/pages/settings/theme_settings_page.dart';
import 'ui/pages/timetable/timetable_page.dart';
import 'ui/theme/app_theme.dart';
import 'ui/widgets/frosted_bars.dart';

class AppDependencies {
  AppDependencies._({
    required this.preferences,
    required this.session,
    required this.timetable,
    required this.campusCard,
    required this.electricity,
    required this.services,
    required this.refresher,
  });

  factory AppDependencies.create({
    required SharedPreferences prefs,
    KeyValueSecureStore? secureStore,
    RuntimeFactory? runtimeFactory,
  }) {
    final store = SessionStore(secureStore ?? FlutterSecureStorageAdapter());
    final snapshots = SnapshotStorage(prefs);
    final preferences = PreferencesController(prefs);
    final session = ZzuSessionController(
      store: store,
      snapshots: snapshots,
      runtimeFactory: runtimeFactory,
    );
    final timetable = TimetableRepository(
      session: session,
      snapshots: snapshots,
    );
    final campusCard = CampusCardRepository(
      session: session,
      snapshots: snapshots,
    );
    final electricity = ElectricityRepository(
      prefs: prefs,
      session: session,
      snapshots: snapshots,
    );
    final services = CampusServiceRepository(session);
    final refresher = CampusRefresher(
      session: session,
      timetable: timetable,
      campusCard: campusCard,
      electricity: electricity,
    );
    return AppDependencies._(
      preferences: preferences,
      session: session,
      timetable: timetable,
      campusCard: campusCard,
      electricity: electricity,
      services: services,
      refresher: refresher,
    );
  }

  final PreferencesController preferences;
  final ZzuSessionController session;
  final TimetableRepository timetable;
  final CampusCardRepository campusCard;
  final ElectricityRepository electricity;
  final CampusServiceRepository services;
  final CampusRefresher refresher;

  void dispose() {
    refresher.dispose();
    electricity.dispose();
    campusCard.dispose();
    timetable.dispose();
    session.dispose();
    preferences.dispose();
  }
}

enum _AppTab { home, timetable, services, settings }

class TeishuCanApp extends StatefulWidget {
  const TeishuCanApp({super.key, required this.deps});

  final AppDependencies deps;

  @override
  State<TeishuCanApp> createState() => _TeishuCanAppState();
}

class _TeishuCanAppState extends State<TeishuCanApp>
    with WidgetsBindingObserver {
  final GlobalKey<NavigatorState> _navigatorKey = GlobalKey<NavigatorState>();
  final GlobalKey<ScaffoldMessengerState> _messengerKey =
      GlobalKey<ScaffoldMessengerState>();
  Object? _lastShownRefreshFailure;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.deps.refresher.addListener(_onRefresherChange);
  }

  @override
  void dispose() {
    widget.deps.refresher.removeListener(_onRefresherChange);
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      widget.deps.refresher.onForegroundResumed();
    }
  }

  void _onRefresherChange() {
    final failure = widget.deps.refresher.refreshFailure;
    if (failure == null || identical(failure, _lastShownRefreshFailure)) return;
    _lastShownRefreshFailure = failure;
    widget.deps.refresher.acknowledgeFailure(failure);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final context = _navigatorKey.currentContext;
      if (context == null) return;
      final strings = AppStrings.resolve(
        widget.deps.preferences.language,
        Localizations.maybeLocaleOf(context),
      );
      final message = _failureMessage(strings, failure);
      _messengerKey.currentState?.showSnackBar(
        SnackBar(content: Text(message)),
      );
    });
  }

  String _failureMessage(AppStrings strings, Object error) {
    if (error is InvalidMeterLocationException) {
      return strings.electricityMeterReselectLocation;
    }
    if (error is CampusRequestException) {
      if (error.reason != null && error.reason!.isNotEmpty) {
        return error.reason!;
      }
      return switch (error.feature) {
        CampusFeature.profile => strings.profileRefreshFailed,
        CampusFeature.timetable => strings.timetableRefreshFailed,
        CampusFeature.campusCard => strings.campusCardRefreshFailed,
        CampusFeature.electricity => strings.electricityRefreshFailed,
        CampusFeature.services => strings.serviceOpenFailed,
        CampusFeature.account => strings.refreshFailed,
      };
    }
    return strings.refreshFailed;
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.deps.preferences,
      builder: (context, _) {
        final prefs = widget.deps.preferences;
        final themeSettings = prefs.theme;
        final locale = switch (prefs.language) {
          AppLanguage.system => null,
          AppLanguage.japanese => const Locale('ja'),
          AppLanguage.chinese => const Locale('zh'),
        };
        final themeMode = switch (themeSettings.mode) {
          AppThemeMode.system => ThemeMode.system,
          AppThemeMode.light => ThemeMode.light,
          AppThemeMode.dark => ThemeMode.dark,
        };

        return MaterialApp(
          navigatorKey: _navigatorKey,
          scaffoldMessengerKey: _messengerKey,
          debugShowCheckedModeBanner: false,
          title: 'TeishuCan',
          locale: locale,
          supportedLocales: const <Locale>[Locale('ja'), Locale('zh')],
          localizationsDelegates: const <LocalizationsDelegate<dynamic>>[
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          themeMode: themeMode,
          theme: buildTeishuTheme(themeSettings, Brightness.light),
          darkTheme: buildTeishuTheme(themeSettings, Brightness.dark),
          home: _MainShell(deps: widget.deps),
        );
      },
    );
  }
}

class _MainShell extends StatefulWidget {
  const _MainShell({required this.deps});

  final AppDependencies deps;

  @override
  State<_MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<_MainShell> {
  final PageController _pageController = PageController();
  _AppTab _selectedTab = _AppTab.home;
  bool _wasSignedIn = false;

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  void _selectTab(List<_AppTab> tabs, int index) {
    if (index < 0 || index >= tabs.length) return;
    setState(() => _selectedTab = tabs[index]);
    if (_pageController.hasClients) {
      _pageController.animateToPage(
        index,
        duration: const Duration(milliseconds: 280),
        curve: Curves.easeOutCubic,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final listenables = Listenable.merge(<Listenable>[
      widget.deps.preferences,
      widget.deps.session,
      widget.deps.timetable,
      widget.deps.campusCard,
      widget.deps.electricity,
      widget.deps.refresher,
    ]);

    return ListenableBuilder(
      listenable: listenables,
      builder: (context, _) {
        final account = widget.deps.session.account;
        if (account is AccountRestoring) {
          return const Scaffold(body: SizedBox.shrink());
        }

        final isSignedIn = account is AccountSignedIn;
        if (_wasSignedIn && !isSignedIn) {
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (!mounted) return;
            Navigator.of(context).popUntil((route) => route.isFirst);
          });
        }
        _wasSignedIn = isSignedIn;

        final student = account is AccountSignedIn ? account.profile : null;
        final tabs = isSignedIn
            ? const <_AppTab>[
                _AppTab.home,
                _AppTab.timetable,
                _AppTab.services,
                _AppTab.settings,
              ]
            : const <_AppTab>[_AppTab.home, _AppTab.settings];

        if (!tabs.contains(_selectedTab)) {
          _selectedTab = _AppTab.home;
        }
        final selectedIndex = tabs.indexOf(_selectedTab);

        final strings = AppStrings.resolve(
          widget.deps.preferences.language,
          Localizations.maybeLocaleOf(context),
        );
        final settings = widget.deps.preferences.theme;
        final isWide = MediaQuery.sizeOf(context).width >= 600;
        final destinations = tabs
            .map((t) => _navDestination(t, strings))
            .toList(growable: false);

        final bottomSafe = MediaQuery.paddingOf(context).bottom;
        final bottomBarClearance = isWide
            ? bottomSafe
            : bottomSafe +
                (settings.isFloatingNavigationBarEnabled ? 84.0 : 80.0);

        final pager = PageView.builder(
          controller: _pageController,
          itemCount: tabs.length,
          onPageChanged: (index) => setState(() => _selectedTab = tabs[index]),
          itemBuilder: (context, index) => _buildTabPage(
            context: context,
            tab: tabs[index],
            strings: strings,
            student: student,
            isBlurEnabled: settings.isBlurEnabled,
            bottomPadding: bottomBarClearance,
          ),
        );

        if (isWide) {
          return Scaffold(
            body: Row(
              children: [
                NavigationRail(
                  selectedIndex: selectedIndex,
                  onDestinationSelected: (idx) => _selectTab(tabs, idx),
                  labelType: NavigationRailLabelType.all,
                  destinations: destinations
                      .map(
                        (d) => NavigationRailDestination(
                          icon: Icon(d.icon),
                          selectedIcon: Icon(d.selectedIcon),
                          label: Text(d.label),
                        ),
                      )
                      .toList(growable: false),
                ),
                const VerticalDivider(width: 1),
                Expanded(child: pager),
              ],
            ),
          );
        }

        return Scaffold(
          extendBody: true,
          body: pager,
          bottomNavigationBar: TeishuBottomBar(
            destinations: destinations,
            selectedIndex: selectedIndex,
            onSelect: (idx) => _selectTab(tabs, idx),
            isFloating: settings.isFloatingNavigationBarEnabled,
            isBlurEnabled: settings.isBlurEnabled,
          ),
        );
      },
    );
  }

  TeishuNavDestination _navDestination(_AppTab tab, AppStrings strings) =>
      switch (tab) {
        _AppTab.home => TeishuNavDestination(
            label: strings.destinationHome,
            icon: Icons.home_outlined,
            selectedIcon: Icons.home_rounded,
          ),
        _AppTab.timetable => TeishuNavDestination(
            label: strings.destinationTimetable,
            icon: Icons.calendar_month_outlined,
            selectedIcon: Icons.calendar_month_rounded,
          ),
        _AppTab.services => TeishuNavDestination(
            label: strings.destinationServices,
            icon: Icons.grid_view_outlined,
            selectedIcon: Icons.grid_view_rounded,
          ),
        _AppTab.settings => TeishuNavDestination(
            label: strings.destinationSettings,
            icon: Icons.settings_outlined,
            selectedIcon: Icons.settings_rounded,
          ),
      };

  Widget _buildTabPage({
    required BuildContext context,
    required _AppTab tab,
    required AppStrings strings,
    required StudentProfile? student,
    required bool isBlurEnabled,
    required double bottomPadding,
  }) {
    switch (tab) {
      case _AppTab.home:
        if (student == null) {
          return SignedOutHomePage(
            strings: strings,
            isBlurEnabled: isBlurEnabled,
            bottomPadding: bottomPadding,
            onLoginClick: () => showLoginSheet(
              context: context,
              strings: strings,
              session: widget.deps.session,
            ),
          );
        }
        return HomePage(
          strings: strings,
          profile: student,
          terms: widget.deps.timetable.terms,
          campusCardBalanceYuan: widget.deps.campusCard.balanceYuan,
          meters: widget.deps.electricity.meters,
          isRefreshing: widget.deps.refresher.isRefreshing,
          isBlurEnabled: isBlurEnabled,
          bottomPadding: bottomPadding,
          onRefresh: widget.deps.refresher.refresh,
          onOpenRecharge: () => showCampusCardRechargeSheet(
            context: context,
            strings: strings,
            campusCard: widget.deps.campusCard,
          ),
          onOpenElectricitySettings: () => _openElectricitySettings(context),
        );

      case _AppTab.timetable:
        return TimetablePage(
          strings: strings,
          repository: widget.deps.timetable,
          isBlurEnabled: isBlurEnabled,
          bottomPadding: bottomPadding,
        );

      case _AppTab.services:
        return ServicesPage(
          strings: strings,
          repository: widget.deps.services,
          isBlurEnabled: isBlurEnabled,
          bottomPadding: bottomPadding,
          onOpenUndergradEas: () => _openUndergradEas(context),
        );

      case _AppTab.settings:
        return SettingsPage(
          strings: strings,
          student: student,
          preferences: widget.deps.preferences,
          isBlurEnabled: isBlurEnabled,
          bottomPadding: bottomPadding,
          onLoginClick: () => showLoginSheet(
            context: context,
            strings: strings,
            session: widget.deps.session,
          ),
          onLogout: widget.deps.session.logout,
          onOpenThemeSettings: () => _openThemeSettings(context),
          onOpenElectricitySettings: () => _openElectricitySettings(context),
          onOpenAbout: () => _openAbout(context),
        );
    }
  }

  void _openThemeSettings(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ListenableBuilder(
          listenable: widget.deps.preferences,
          builder: (ctx, _) => ThemeSettingsPage(
            strings: AppStrings.resolve(
              widget.deps.preferences.language,
              Localizations.maybeLocaleOf(ctx),
            ),
            preferences: widget.deps.preferences,
          ),
        ),
      ),
    );
  }

  void _openElectricitySettings(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ListenableBuilder(
          listenable: widget.deps.preferences,
          builder: (ctx, _) => ElectricitySettingsPage(
            strings: AppStrings.resolve(
              widget.deps.preferences.language,
              Localizations.maybeLocaleOf(ctx),
            ),
            electricity: widget.deps.electricity,
            campusCard: widget.deps.campusCard,
            isBlurEnabled: widget.deps.preferences.theme.isBlurEnabled,
          ),
        ),
      ),
    );
  }

  void _openUndergradEas(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ListenableBuilder(
          listenable: widget.deps.preferences,
          builder: (ctx, _) => UndergradEasPage(
            strings: AppStrings.resolve(
              widget.deps.preferences.language,
              Localizations.maybeLocaleOf(ctx),
            ),
            repository: widget.deps.services,
            isBlurEnabled: widget.deps.preferences.theme.isBlurEnabled,
          ),
        ),
      ),
    );
  }

  void _openAbout(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ListenableBuilder(
          listenable: widget.deps.preferences,
          builder: (ctx, _) => AboutPage(
            strings: AppStrings.resolve(
              widget.deps.preferences.language,
              Localizations.maybeLocaleOf(ctx),
            ),
            isBlurEnabled: widget.deps.preferences.theme.isBlurEnabled,
          ),
        ),
      ),
    );
  }
}
