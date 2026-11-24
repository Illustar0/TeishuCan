import 'package:material_ui/material_ui.dart';

import '../../../data/campus_service_repository.dart';
import '../../../domain/campus_exception.dart';
import '../../../domain/campus_service.dart';
import '../../../l10n/app_strings.dart';
import '../../theme/app_theme.dart';
import '../../widgets/frosted_bars.dart';
import '../../widgets/rounded_card.dart';
import '../../widgets/url_launcher_helper.dart';
import 'services_page.dart';

class UndergradEasPage extends StatefulWidget {
  const UndergradEasPage({
    super.key,
    required this.strings,
    required this.repository,
    required this.isBlurEnabled,
  });

  final AppStrings strings;
  final CampusServiceRepository repository;
  final bool isBlurEnabled;

  @override
  State<UndergradEasPage> createState() => _UndergradEasPageState();
}

class _UndergradEasPageState extends State<UndergradEasPage> {
  CampusService? _openingService;

  Future<void> _openService(CampusService service) async {
    if (_openingService != null) return;
    setState(() => _openingService = service);
    try {
      final url = await widget.repository.loginUrl(service);
      final launched = await openCampusUrl(url);
      if (!launched && mounted) {
        _showFailure(widget.strings.serviceOpenFailed);
      }
    } on CampusRequestException catch (e) {
      if (mounted) {
        _showFailure(e.reason ?? widget.strings.serviceOpenFailed);
      }
    } catch (_) {
      if (mounted) {
        _showFailure(widget.strings.serviceOpenFailed);
      }
    } finally {
      if (mounted) {
        setState(() => _openingService = null);
      }
    }
  }

  void _showFailure(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final strings = widget.strings;
    final palette = TeishuPalette.of(context);
    final topInset = MediaQuery.paddingOf(context).top + kToolbarHeight;

    return Scaffold(
      extendBodyBehindAppBar: true,
      appBar: FrostedTopBar(
        title: strings.serviceUndergradEas,
        isBlurEnabled: widget.isBlurEnabled,
      ),
      body: ListView(
        padding: EdgeInsets.only(
          top: topInset,
          bottom: MediaQuery.paddingOf(context).bottom + 16,
        ),
        children: [
          for (final group in undergradEasGroups) ...[
            SectionTitle(group.titleSelector(strings)),
            RoundedCard(
              margin: const EdgeInsets.fromLTRB(12, 8, 12, 0),
              child: Column(
                children: [
                  for (var i = 0; i < group.services.length; i++) ...[
                    if (i > 0) Divider(height: 1, color: palette.divider),
                    ServiceRow(
                      strings: strings,
                      service: group.services[i],
                      isOpening: _openingService == group.services[i],
                      onTap: () => _openService(group.services[i]),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}
