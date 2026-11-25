import 'package:libzzu_ffi/libzzu_ffi.dart';
import 'package:material_ui/material_ui.dart';

import '../../../data/zzu_session.dart';
import '../../../l10n/app_strings.dart';
import '../../theme/app_theme.dart';
import '../../widgets/frosted_bars.dart';
import '../../widgets/rounded_card.dart';

class CampusNetworkPage extends StatefulWidget {
  const CampusNetworkPage({
    super.key,
    required this.strings,
    required this.session,
    required this.isBlurEnabled,
  });

  final AppStrings strings;
  final ZzuSessionController session;
  final bool isBlurEnabled;

  @override
  State<CampusNetworkPage> createState() => _CampusNetworkPageState();
}

class _CampusNetworkPageState extends State<CampusNetworkPage> {
  final TextEditingController _portalUrlController = TextEditingController();
  final TextEditingController _userIpController = TextEditingController();
  final TextEditingController _selfUrlController = TextEditingController();
  late final TextEditingController _accountController;
  late final TextEditingController _passwordController;

  bool _useSystemRoute = false;
  bool _isDiscovering = false;
  bool _isLoadingIsps = false;
  bool _isLoggingIn = false;
  bool _isLoadingDevices = false;
  String? _kickingSessionId;

  List<IspInfo> _isps = const <IspInfo>[];
  String _selectedSuffix = '';
  List<OnlineDevice> _devices = const <OnlineDevice>[];
  String? _portalStatusMessage;
  bool _portalStatusError = false;
  String? _selfStatusMessage;

  @override
  void initState() {
    super.initState();
    _accountController =
        TextEditingController(text: widget.session.savedAccount ?? '');
    _passwordController =
        TextEditingController(text: widget.session.savedPassword ?? '');
  }

  @override
  void dispose() {
    _portalUrlController.dispose();
    _userIpController.dispose();
    _selfUrlController.dispose();
    _accountController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  String _portalAccount() {
    final base = _accountController.text.trim();
    if (_selectedSuffix.isEmpty || base.endsWith(_selectedSuffix)) {
      return base;
    }
    return '$base$_selectedSuffix';
  }

  Future<void> _discoverPortal() async {
    if (_isDiscovering) return;
    setState(() {
      _isDiscovering = true;
      _portalStatusMessage = null;
      _portalStatusError = false;
    });
    try {
      final runtime = await widget.session.ensureRuntime();
      final info = await portalDiscover(runtime);
      if (!mounted) return;
      setState(() {
        _portalUrlController.text = info.portalServerUrl;
        _userIpController.text = info.userIp;
        _portalStatusMessage = widget.strings.networkDiscoverSuccess;
        _portalStatusError = false;
      });
    } on ZzuException catch (e) {
      if (!mounted) return;
      setState(() {
        _portalStatusMessage = e.message.isNotEmpty
            ? e.message
            : widget.strings.networkDiscoverFailed;
        _portalStatusError = true;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _portalStatusMessage = widget.strings.networkDiscoverFailed;
        _portalStatusError = true;
      });
    } finally {
      if (mounted) setState(() => _isDiscovering = false);
    }
  }

  Future<void> _loadIsps() async {
    final portalUrl = _portalUrlController.text.trim();
    final userIp = _userIpController.text.trim();
    if (portalUrl.isEmpty || userIp.isEmpty || _isLoadingIsps) return;
    setState(() {
      _isLoadingIsps = true;
      _portalStatusMessage = null;
      _portalStatusError = false;
    });
    PortalClient? client;
    try {
      final runtime = await widget.session.ensureRuntime();
      client = PortalClient(
        runtime,
        baseUrl: portalUrl,
        userIp: userIp,
        sourceAddress: _useSystemRoute ? 'system' : 'user_ip',
      );
      final list = await client.isps();
      if (!mounted) return;
      setState(() {
        _isps = list;
        if (list.isNotEmpty &&
            !list.any((item) => item.suffix == _selectedSuffix)) {
          _selectedSuffix = list.first.suffix;
        }
      });
    } on ZzuException catch (e) {
      if (!mounted) return;
      setState(() {
        _portalStatusMessage =
            e.message.isNotEmpty ? e.message : widget.strings.networkActionFailed;
        _portalStatusError = true;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _portalStatusMessage = widget.strings.networkActionFailed;
        _portalStatusError = true;
      });
    } finally {
      client?.dispose();
      if (mounted) setState(() => _isLoadingIsps = false);
    }
  }

  Future<void> _loginPortal() async {
    final portalUrl = _portalUrlController.text.trim();
    final userIp = _userIpController.text.trim();
    final account = _portalAccount();
    final password = _passwordController.text;
    if (portalUrl.isEmpty ||
        userIp.isEmpty ||
        account.isEmpty ||
        password.isEmpty ||
        _isLoggingIn) {
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() {
      _isLoggingIn = true;
      _portalStatusMessage = null;
      _portalStatusError = false;
    });
    PortalClient? client;
    try {
      final runtime = await widget.session.ensureRuntime();
      client = PortalClient(
        runtime,
        baseUrl: portalUrl,
        userIp: userIp,
        sourceAddress: _useSystemRoute ? 'system' : 'user_ip',
      );
      final auth = await client.login(account: account, password: password);
      if (!mounted) return;
      setState(() {
        _portalStatusMessage = auth.message.isNotEmpty
            ? auth.message
            : (auth.isSuccess
                ? widget.strings.networkLoginSuccess
                : widget.strings.networkLoginFailed);
        _portalStatusError = !auth.isSuccess;
      });
    } on ZzuException catch (e) {
      if (!mounted) return;
      setState(() {
        _portalStatusMessage =
            e.message.isNotEmpty ? e.message : widget.strings.networkLoginFailed;
        _portalStatusError = true;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _portalStatusMessage = widget.strings.networkLoginFailed;
        _portalStatusError = true;
      });
    } finally {
      client?.dispose();
      if (mounted) setState(() => _isLoggingIn = false);
    }
  }

  Future<void> _loadOnlineDevices() async {
    final baseUrl = _selfUrlController.text.trim();
    final account = _accountController.text.trim();
    final password = _passwordController.text;
    if (baseUrl.isEmpty ||
        account.isEmpty ||
        password.isEmpty ||
        _isLoadingDevices) {
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() {
      _isLoadingDevices = true;
      _selfStatusMessage = null;
    });
    SelfServiceClient? client;
    try {
      final runtime = await widget.session.ensureRuntime();
      client = SelfServiceClient(runtime, baseUrl: baseUrl);
      await client.login(account: account, password: password);
      final list = await client.onlineDevices();
      if (!mounted) return;
      setState(() {
        _devices = list;
      });
    } on ZzuException catch (e) {
      if (!mounted) return;
      setState(() {
        _selfStatusMessage =
            e.message.isNotEmpty ? e.message : widget.strings.networkActionFailed;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _selfStatusMessage = widget.strings.networkActionFailed;
      });
    } finally {
      client?.dispose();
      if (mounted) setState(() => _isLoadingDevices = false);
    }
  }

  Future<void> _kickDevice(OnlineDevice device) async {
    final strings = widget.strings;
    final label = device.hostName.isNotEmpty ? device.hostName : device.ip;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(strings.networkKickConfirmTitle),
        content: Text(strings.networkKickConfirmMessage(label)),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: Text(strings.logoutCancel),
          ),
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            style: TextButton.styleFrom(
              foregroundColor: Theme.of(ctx).colorScheme.error,
            ),
            child: Text(strings.networkKickDevice),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    final baseUrl = _selfUrlController.text.trim();
    final account = _accountController.text.trim();
    final password = _passwordController.text;
    if (baseUrl.isEmpty || account.isEmpty || password.isEmpty) return;

    setState(() {
      _kickingSessionId = device.sessionId;
      _selfStatusMessage = null;
    });
    SelfServiceClient? client;
    try {
      final runtime = await widget.session.ensureRuntime();
      client = SelfServiceClient(runtime, baseUrl: baseUrl);
      await client.login(account: account, password: password);
      await client.kickDevice(device.sessionId);
      if (!mounted) return;
      setState(() {
        _devices = _devices
            .where((d) => d.sessionId != device.sessionId)
            .toList(growable: false);
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(strings.networkKickSuccess)),
      );
    } on ZzuException catch (e) {
      if (!mounted) return;
      setState(() {
        _selfStatusMessage =
            e.message.isNotEmpty ? e.message : strings.networkActionFailed;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _selfStatusMessage = strings.networkActionFailed;
      });
    } finally {
      client?.dispose();
      if (mounted) setState(() => _kickingSessionId = null);
    }
  }

  String _selectedIspName(AppStrings strings) {
    for (final isp in _isps) {
      if (isp.suffix == _selectedSuffix) return isp.name;
    }
    return strings.networkIspAuto;
  }

  Future<void> _pickIsp(AppStrings strings) async {
    final palette = TeishuPalette.of(context);
    final options = <IspInfo>[
      IspInfo(name: strings.networkIspAuto, suffix: ''),
      ..._isps,
    ];
    await showModalBottomSheet<void>(
      context: context,
      useSafeArea: true,
      builder: (ctx) => Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              child: Text(
                strings.networkIsp,
                style: Theme.of(ctx).textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
              ),
            ),
            const SizedBox(height: 8),
            RoundedCard(
              color: palette.secondaryCard,
              margin: EdgeInsets.zero,
              radius: 16,
              child: Column(
                children: [
                  for (var i = 0; i < options.length; i++) ...[
                    if (i > 0) Divider(height: 1, color: palette.divider),
                    PreferenceRow(
                      title: options[i].name,
                      summary: options[i].suffix.isEmpty
                          ? null
                          : options[i].suffix,
                      trailing: _selectedSuffix == options[i].suffix
                          ? Icon(
                              Icons.check_rounded,
                              color: Theme.of(ctx).colorScheme.primary,
                            )
                          : null,
                      onTap: () {
                        Navigator.of(ctx).pop();
                        setState(() => _selectedSuffix = options[i].suffix);
                      },
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final strings = widget.strings;
    final palette = TeishuPalette.of(context);
    final theme = Theme.of(context);
    final topInset = MediaQuery.paddingOf(context).top + kToolbarHeight;

    final canFetchIsps = _portalUrlController.text.trim().isNotEmpty &&
        _userIpController.text.trim().isNotEmpty &&
        !_isLoadingIsps;
    final canLoginPortal = canFetchIsps &&
        _accountController.text.trim().isNotEmpty &&
        _passwordController.text.isNotEmpty &&
        !_isLoggingIn;
    final canQuerySelf = _selfUrlController.text.trim().isNotEmpty &&
        _accountController.text.trim().isNotEmpty &&
        _passwordController.text.isNotEmpty &&
        !_isLoadingDevices;

    return Scaffold(
      extendBodyBehindAppBar: true,
      appBar: FrostedTopBar(
        title: strings.serviceCampusNetwork,
        isBlurEnabled: widget.isBlurEnabled,
      ),
      body: ListView(
        padding: EdgeInsets.only(
          top: topInset + 4,
          bottom: MediaQuery.paddingOf(context).bottom + 24,
        ),
        children: [
          SectionTitle(strings.networkPortalSection),
          RoundedCard(
            margin: const EdgeInsets.fromLTRB(12, 8, 12, 0),
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                FilledButton.tonalIcon(
                  onPressed: _isDiscovering ? null : _discoverPortal,
                  icon: const Icon(Icons.radar_rounded, size: 18),
                  label: Text(
                    _isDiscovering
                        ? strings.networkDiscovering
                        : strings.networkDiscoverAction,
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _portalUrlController,
                  keyboardType: TextInputType.url,
                  onChanged: (_) => setState(() {}),
                  decoration: InputDecoration(
                    labelText: strings.networkPortalUrl,
                    hintText: 'http://10.x.x.x:801',
                    filled: true,
                    fillColor: palette.secondaryCard,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                      borderSide: BorderSide.none,
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _userIpController,
                  keyboardType: TextInputType.number,
                  onChanged: (_) => setState(() {}),
                  decoration: InputDecoration(
                    labelText: strings.networkUserIp,
                    hintText: '10.x.x.x',
                    filled: true,
                    fillColor: palette.secondaryCard,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                      borderSide: BorderSide.none,
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _accountController,
                  onChanged: (_) => setState(() {}),
                  decoration: InputDecoration(
                    labelText: strings.networkAccount,
                    filled: true,
                    fillColor: palette.secondaryCard,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                      borderSide: BorderSide.none,
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _passwordController,
                  obscureText: true,
                  onChanged: (_) => setState(() {}),
                  decoration: InputDecoration(
                    labelText: strings.networkPassword,
                    filled: true,
                    fillColor: palette.secondaryCard,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                      borderSide: BorderSide.none,
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                RoundedCard(
                  color: palette.secondaryCard,
                  margin: EdgeInsets.zero,
                  radius: 14,
                  child: Column(
                    children: [
                      PreferenceRow(
                        title: strings.networkIsp,
                        trailing: ChevronTrailing(
                          valueText: _selectedIspName(strings),
                        ),
                        onTap: () => _pickIsp(strings),
                      ),
                      Divider(height: 1, color: palette.divider),
                      PreferenceRow(
                        title: strings.networkUseSystemRoute,
                        summary: strings.networkUseSystemRouteSummary,
                        trailing: Switch(
                          value: _useSystemRoute,
                          onChanged: (v) => setState(() => _useSystemRoute = v),
                        ),
                        onTap: () =>
                            setState(() => _useSystemRoute = !_useSystemRoute),
                      ),
                    ],
                  ),
                ),
                if (_portalStatusMessage != null) ...[
                  const SizedBox(height: 10),
                  Text(
                    _portalStatusMessage!,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: _portalStatusError
                          ? theme.colorScheme.error
                          : theme.colorScheme.primary,
                    ),
                  ),
                ],
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: FilledButton.tonal(
                        onPressed: canFetchIsps ? _loadIsps : null,
                        child: Text(
                          _isLoadingIsps
                              ? strings.networkLoadingIsps
                              : strings.networkLoadIsps,
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: FilledButton(
                        onPressed: canLoginPortal ? _loginPortal : null,
                        child: Text(
                          _isLoggingIn
                              ? strings.networkLoggingIn
                              : strings.networkLoginAction,
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          SectionTitle(strings.networkSelfServiceSection),
          RoundedCard(
            margin: const EdgeInsets.fromLTRB(12, 8, 12, 0),
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                TextField(
                  controller: _selfUrlController,
                  keyboardType: TextInputType.url,
                  onChanged: (_) => setState(() {}),
                  decoration: InputDecoration(
                    labelText: strings.networkSelfServiceUrl,
                    hintText: 'https://...',
                    filled: true,
                    fillColor: palette.secondaryCard,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                      borderSide: BorderSide.none,
                    ),
                  ),
                ),
                if (_selfStatusMessage != null) ...[
                  const SizedBox(height: 8),
                  Text(
                    _selfStatusMessage!,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.error,
                    ),
                  ),
                ],
                const SizedBox(height: 12),
                FilledButton.tonal(
                  onPressed: canQuerySelf ? _loadOnlineDevices : null,
                  child: Text(
                    _isLoadingDevices
                        ? strings.networkLoadingDevices
                        : strings.networkLoadDevices,
                  ),
                ),
                const SizedBox(height: 10),
                if (_devices.isEmpty)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    child: Text(
                      strings.networkNoDevices,
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: palette.mutedText,
                      ),
                    ),
                  )
                else
                  RoundedCard(
                    color: palette.secondaryCard,
                    margin: EdgeInsets.zero,
                    radius: 14,
                    child: Column(
                      children: [
                        for (var i = 0; i < _devices.length; i++) ...[
                          if (i > 0) Divider(height: 1, color: palette.divider),
                          _DeviceRow(
                            device: _devices[i],
                            kickLabel: strings.networkKickDevice,
                            isKicking: _kickingSessionId == _devices[i].sessionId,
                            onKick: () => _kickDevice(_devices[i]),
                          ),
                        ],
                      ],
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _DeviceRow extends StatelessWidget {
  const _DeviceRow({
    required this.device,
    required this.kickLabel,
    required this.isKicking,
    required this.onKick,
  });

  final OnlineDevice device;
  final String kickLabel;
  final bool isKicking;
  final VoidCallback onKick;

  @override
  Widget build(BuildContext context) {
    final title = device.hostName.isNotEmpty
        ? device.hostName
        : (device.terminalType.isNotEmpty ? device.terminalType : device.ip);
    final summary = <String>[
      device.ip,
      if (device.mac.isNotEmpty) device.mac,
      if (device.downFlow.isNotEmpty || device.upFlow.isNotEmpty)
        '↑${device.upFlow} ↓${device.downFlow}',
    ].join(' · ');

    return PreferenceRow(
      title: title,
      summary: summary,
      trailing: isKicking
          ? const SizedBox(
              width: 18,
              height: 18,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : TextButton(
              onPressed: onKick,
              style: TextButton.styleFrom(
                foregroundColor: Theme.of(context).colorScheme.error,
              ),
              child: Text(kickLabel),
            ),
    );
  }
}
