import 'package:material_ui/material_ui.dart';

import '../../../data/campus_service_repository.dart';
import '../../../domain/campus_exception.dart';
import '../../../domain/campus_service.dart';
import '../../../l10n/app_strings.dart';
import '../../theme/app_theme.dart';
import '../../widgets/frosted_bars.dart';
import '../../widgets/rounded_card.dart';
import '../../widgets/url_launcher_helper.dart';

class ServicesPage extends StatefulWidget {
  const ServicesPage({
    super.key,
    required this.strings,
    required this.repository,
    required this.isBlurEnabled,
    required this.bottomPadding,
    required this.onOpenUndergradEas,
    required this.onOpenCampusNetwork,
  });

  final AppStrings strings;
  final CampusServiceRepository repository;
  final bool isBlurEnabled;
  final double bottomPadding;
  final VoidCallback onOpenUndergradEas;
  final VoidCallback onOpenCampusNetwork;

  @override
  State<ServicesPage> createState() => _ServicesPageState();
}

class _ServicesPageState extends State<ServicesPage> {
  final TextEditingController _searchController = TextEditingController();
  CampusService? _openingService;

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

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
    final query = _searchController.text;
    final isSearching = query.trim().isNotEmpty;
    final hits = isSearching
        ? searchCampusServices(query, strings)
        : const <ServiceSearchHit>[];
    final topInset = MediaQuery.paddingOf(context).top + kToolbarHeight;

    return Scaffold(
      extendBodyBehindAppBar: true,
      appBar: FrostedTopBar(
        title: strings.destinationServices,
        isBlurEnabled: widget.isBlurEnabled,
      ),
      body: ListView(
        padding: EdgeInsets.only(
          top: topInset + 8,
          bottom: widget.bottomPadding + 16,
        ),
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: TextField(
              controller: _searchController,
              onChanged: (_) => setState(() {}),
              decoration: InputDecoration(
                hintText: strings.servicesSearchPlaceholder,
                prefixIcon: const Icon(Icons.search_rounded),
                suffixIcon: isSearching
                    ? IconButton(
                        tooltip: strings.servicesSearchClear,
                        icon: const Icon(Icons.clear_rounded),
                        onPressed: () {
                          _searchController.clear();
                          setState(() {});
                        },
                      )
                    : null,
                filled: true,
                fillColor: palette.card,
                contentPadding: const EdgeInsets.symmetric(vertical: 10),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(18),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
          ),
          if (isSearching) ...[
            if (hits.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 48),
                child: Center(
                  child: Text(
                    strings.servicesSearchEmpty,
                    style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                          color: palette.mutedText,
                        ),
                  ),
                ),
              )
            else
              RoundedCard(
                child: Column(
                  children: [
                    for (var i = 0; i < hits.length; i++) ...[
                      if (i > 0) Divider(height: 1, color: palette.divider),
                      switch (hits[i]) {
                        CampusNetworkHit() => PreferenceRow(
                            leading: const Icon(Icons.wifi_rounded),
                            title: strings.serviceCampusNetwork,
                            summary: strings.serviceCampusNetworkSummary,
                            trailing: const ChevronTrailing(),
                            onTap: widget.onOpenCampusNetwork,
                          ),
                        UndergradEasHit() => PreferenceRow(
                            leading: const Icon(Icons.school_outlined),
                            title: strings.serviceUndergradEas,
                            summary: strings.serviceUndergradEasSummary,
                            trailing: const ChevronTrailing(),
                            onTap: widget.onOpenUndergradEas,
                          ),
                        CampusServiceHit(service: final service) => ServiceRow(
                            strings: strings,
                            service: service,
                            isOpening: _openingService == service,
                            onTap: () => _openService(service),
                          ),
                      },
                    ],
                  ],
                ),
              ),
          ] else ...[
            SectionTitle(strings.servicesAcademic),
            RoundedCard(
              margin: const EdgeInsets.fromLTRB(12, 8, 12, 0),
              child: PreferenceRow(
                leading: const Icon(Icons.school_outlined),
                title: strings.serviceUndergradEas,
                summary: strings.serviceUndergradEasSummary,
                trailing: const ChevronTrailing(),
                onTap: widget.onOpenUndergradEas,
              ),
            ),
            SectionTitle(strings.servicesNetwork),
            RoundedCard(
              margin: const EdgeInsets.fromLTRB(12, 8, 12, 0),
              child: PreferenceRow(
                leading: const Icon(Icons.wifi_rounded),
                title: strings.serviceCampusNetwork,
                summary: strings.serviceCampusNetworkSummary,
                trailing: const ChevronTrailing(),
                onTap: widget.onOpenCampusNetwork,
              ),
            ),
            for (final group in mainServiceGroups) ...[
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
        ],
      ),
    );
  }
}

class ServiceRow extends StatelessWidget {
  const ServiceRow({
    super.key,
    required this.strings,
    required this.service,
    required this.isOpening,
    required this.onTap,
  });

  final AppStrings strings;
  final CampusService service;
  final bool isOpening;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return PreferenceRow(
      leading: Icon(serviceIcon(service)),
      title: strings.serviceTitle(service),
      summary: strings.serviceSummary(service),
      trailing: isOpening
          ? const SizedBox(
              width: 18,
              height: 18,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : const ChevronTrailing(),
      onTap: isOpening ? null : onTap,
    );
  }
}

IconData serviceIcon(CampusService service) => switch (service) {
      CampusService.library => Icons.local_library_outlined,
      CampusService.libraryReservation => Icons.event_seat_outlined,
      CampusService.campusCard => Icons.credit_card_outlined,
      CampusService.mail => Icons.inbox_outlined,
      CampusService.leave => Icons.door_front_door_outlined,
      CampusService.presidentMailbox => Icons.mark_email_unread_outlined,
      CampusService.studentStatus => Icons.badge_outlined,
      CampusService.programCompletion => Icons.fact_check_outlined,
      CampusService.academicWarnings => Icons.warning_amber_rounded,
      CampusService.exams => Icons.edit_note_rounded,
      CampusService.grades => Icons.workspace_premium_outlined,
      CampusService.timetable => Icons.calendar_month_outlined,
      CampusService.courseOfferings => Icons.menu_book_outlined,
      CampusService.schoolCalendar => Icons.date_range_outlined,
      CampusService.availableClassrooms => Icons.meeting_room_outlined,
      CampusService.classroomTimetable => Icons.schedule_outlined,
    };
