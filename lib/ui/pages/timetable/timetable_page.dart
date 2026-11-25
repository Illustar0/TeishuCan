import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:libzzu_ffi/libzzu_ffi.dart';

import '../../../data/timetable_repository.dart';
import '../../../domain/campus_exception.dart';
import '../../../domain/timetable.dart';
import '../../../l10n/app_strings.dart';
import '../../theme/app_theme.dart';
import '../../widgets/frosted_bars.dart';
import '../../widgets/rounded_card.dart';
import 'timetable_grid.dart';

enum _TimetableStatus { ready, noLessons, loading, failed, notFetched }

class TimetablePage extends StatefulWidget {
  const TimetablePage({
    super.key,
    required this.strings,
    required this.repository,
    required this.isBlurEnabled,
    this.isLiquidGlassEnabled = false,
    required this.bottomPadding,
  });

  final AppStrings strings;
  final TimetableRepository repository;
  final bool isBlurEnabled;
  final bool isLiquidGlassEnabled;
  final double bottomPadding;

  @override
  State<TimetablePage> createState() => _TimetablePageState();
}

class _TimetablePageState extends State<TimetablePage> {
  String? _selectedTermId;
  final Set<String> _loadingTermIds = <String>{};
  final Set<String> _failedTermIds = <String>{};
  String? _activePageTermId;
  PageController? _weekPageController;
  int _selectedWeekIndex = 0;
  bool _manualRefreshing = false;

  @override
  void dispose() {
    _weekPageController?.dispose();
    super.dispose();
  }

  TimetableTerm? _resolveTerm(List<TimetableTerm> terms, ShanghaiDate today) {
    if (_selectedTermId != null) {
      for (final t in terms) {
        if (t.id == _selectedTermId) return t;
      }
    }
    return defaultTerm(terms, today);
  }

  void _ensureWeekController(TimetableTerm? term, ShanghaiDate today) {
    if (term == null) {
      _activePageTermId = null;
      _weekPageController?.dispose();
      _weekPageController = null;
      _selectedWeekIndex = 0;
      return;
    }
    if (_activePageTermId == term.id && _weekPageController != null) {
      return;
    }
    _activePageTermId = term.id;
    _weekPageController?.dispose();
    final idx = term.weeks.indexWhere((w) => w.dates.contains(today));
    _selectedWeekIndex = idx >= 0 ? idx : 0;
    _weekPageController = PageController(initialPage: _selectedWeekIndex);
  }

  void _ensureTermLoaded(TimetableTerm? term) {
    if (term == null || term.isLoaded) return;
    if (_loadingTermIds.contains(term.id) || _failedTermIds.contains(term.id)) {
      return;
    }
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        _loadTerm(term.id);
      }
    });
  }

  Future<void> _loadTerm(String termId) async {
    if (_loadingTermIds.contains(termId)) return;
    setState(() {
      _loadingTermIds.add(termId);
      _failedTermIds.remove(termId);
    });
    try {
      await widget.repository.loadTerm(termId);
    } on CampusRequestException catch (e) {
      if (mounted) {
        setState(() => _failedTermIds.add(termId));
        _showMessage(e.reason ?? widget.strings.timetableRefreshFailed);
      }
    } catch (_) {
      if (mounted) {
        setState(() => _failedTermIds.add(termId));
        _showMessage(widget.strings.timetableRefreshFailed);
      }
    } finally {
      if (mounted) {
        setState(() => _loadingTermIds.remove(termId));
      }
    }
  }

  Future<void> _refresh() async {
    if (_manualRefreshing) return;
    setState(() => _manualRefreshing = true);
    try {
      await widget.repository.refresh();
      final today = ShanghaiDateTime.now().date;
      final term = _resolveTerm(widget.repository.terms, today);
      if (term != null && (_selectedTermId != null || !term.isLoaded)) {
        await _loadTerm(term.id);
      }
    } on CampusRequestException catch (e) {
      _showMessage(e.reason ?? widget.strings.timetableRefreshFailed);
    } catch (_) {
      _showMessage(widget.strings.timetableRefreshFailed);
    } finally {
      if (mounted) {
        setState(() => _manualRefreshing = false);
      }
    }
  }

  Future<void> _exportIcs(TimetableTerm term) async {
    final strings = widget.strings;
    try {
      final ics = widget.repository.exportTermIcs(
        term.id,
        calendarName: strings.timetableTermName(term.schoolYear, term.number),
      );
      if (ics == null || ics.isEmpty) {
        _showMessage(strings.timetableExportUnavailable);
        return;
      }
      await Clipboard.setData(ClipboardData(text: ics));
      if (!mounted) return;
      _showMessage(strings.timetableExportedIcs);
    } on ZzuException catch (e) {
      if (mounted) _showMessage(e.message);
    } catch (_) {
      if (mounted) _showMessage(strings.timetableExportUnavailable);
    }
  }

  void _showMessage(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  void _selectWeek(int index) {
    setState(() => _selectedWeekIndex = index);
    _weekPageController?.animateToPage(
      index,
      duration: const Duration(milliseconds: 280),
      curve: Curves.easeOutCubic,
    );
  }

  _TimetableStatus _statusOf(TimetableTerm? term, bool isBusy) {
    if (term != null && term.isLoaded) {
      final hasLessons = term.weeks.any((w) => w.entries.isNotEmpty);
      return hasLessons ? _TimetableStatus.ready : _TimetableStatus.noLessons;
    }
    if (isBusy || (term != null && _loadingTermIds.contains(term.id))) {
      return _TimetableStatus.loading;
    }
    if (term != null && _failedTermIds.contains(term.id)) {
      return _TimetableStatus.failed;
    }
    return _TimetableStatus.notFetched;
  }

  @override
  Widget build(BuildContext context) {
    final strings = widget.strings;
    final terms = widget.repository.terms;
    final today = ShanghaiDateTime.now().date;
    final term = _resolveTerm(terms, today);
    final isBusy = _manualRefreshing || widget.repository.isRefreshing;
    final status = _statusOf(term, isBusy);

    _ensureWeekController(term, today);
    _ensureTermLoaded(term);

    final topInset = MediaQuery.paddingOf(context).top + kToolbarHeight;
    final readyTerm = status == _TimetableStatus.ready ? term : null;
    final weeks = readyTerm?.weeks ?? const <TimetableWeek>[];
    final safeWeekIndex = weeks.isEmpty
        ? 0
        : _selectedWeekIndex.clamp(0, weeks.length - 1);
    final selectedWeek = weeks.isEmpty ? null : weeks[safeWeekIndex];
    final currentWeek = weeks
        .cast<TimetableWeek?>()
        .firstWhere((w) => w!.dates.contains(today), orElse: () => null);
    final canExport =
        readyTerm != null && widget.repository.canExportTerm(readyTerm.id);

    return Scaffold(
      extendBodyBehindAppBar: true,
      appBar: FrostedTopBar(
        title: strings.destinationTimetable,
        subtitle: term == null
            ? null
            : strings.timetableTermName(term.schoolYear, term.number),
        isBlurEnabled: widget.isBlurEnabled,
        actions: [
          if (canExport)
            IconButton(
              icon: const Icon(Icons.file_download_outlined),
              tooltip: strings.timetableExportIcs,
              onPressed: () => _exportIcs(readyTerm),
            ),
          if (terms.isNotEmpty && term != null)
            IconButton(
              icon: const Icon(Icons.tune_rounded),
              tooltip: strings.timetableTerm,
              onPressed: () => _showTermPicker(terms, term),
            ),
        ],
      ),
      body: RefreshIndicator(
        edgeOffset: topInset,
        onRefresh: _refresh,
        child: Stack(
          children: [
            if (readyTerm == null || selectedWeek == null)
              ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: EdgeInsets.only(
                  top: topInset + 48,
                  bottom: widget.bottomPadding + 24,
                ),
                children: [
                  SizedBox(
                    height: MediaQuery.sizeOf(context).height * 0.55,
                    child: _StatusMessage(
                      strings: strings,
                      status: status,
                      onAction: _refresh,
                    ),
                  ),
                ],
              )
            else
              ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: EdgeInsets.only(
                  top: topInset + 72,
                  bottom: widget.bottomPadding + 16,
                ),
                children: [
                  RoundedCard(
                    padding: const EdgeInsets.fromLTRB(4, 0, 8, 8),
                    child: SizedBox(
                      height: 64.0 * 10 + 48,
                      child: PageView.builder(
                        controller: _weekPageController,
                        physics: const NeverScrollableScrollPhysics(),
                        itemCount: weeks.length,
                        itemBuilder: (_, index) => TimetableGrid(
                          strings: strings,
                          week: weeks[index],
                          today: today,
                          onEntryTap: (entry) => _showEntryDetail(entry),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            if (readyTerm != null && selectedWeek != null)
              Positioned(
                top: topInset + 8,
                left: 12,
                right: 12,
                child: _WeekSwitcherBar(
                  strings: strings,
                  weeks: weeks,
                  selectedIndex: safeWeekIndex,
                  selectedWeek: selectedWeek,
                  isBlurEnabled: widget.isBlurEnabled,
                  isLiquidGlassEnabled: widget.isLiquidGlassEnabled,
                  onSelectIndex: _selectWeek,
                  onOpenWeekPicker: () => _showWeekPicker(
                    weeks: weeks,
                    selectedIndex: safeWeekIndex,
                    currentWeek: currentWeek,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Future<void> _showWeekPicker({
    required List<TimetableWeek> weeks,
    required int selectedIndex,
    required TimetableWeek? currentWeek,
  }) async {
    final strings = widget.strings;
    final palette = TeishuPalette.of(context);
    final currentIdx = currentWeek == null
        ? -1
        : weeks.indexWhere((w) => w.number == currentWeek.number);

    await showModalBottomSheet<void>(
      context: context,
      useSafeArea: true,
      isScrollControlled: true,
      builder: (ctx) => Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Expanded(
                  child: Padding(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    child: Text(
                      strings.timetableSelectWeek,
                      style: Theme.of(ctx).textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                    ),
                  ),
                ),
                if (currentIdx >= 0 && currentIdx != selectedIndex)
                  TextButton(
                    onPressed: () {
                      Navigator.of(ctx).pop();
                      _selectWeek(currentIdx);
                    },
                    child: Text(strings.timetableBackToCurrentWeek),
                  ),
              ],
            ),
            const SizedBox(height: 8),
            ConstrainedBox(
              constraints: BoxConstraints(
                maxHeight: MediaQuery.sizeOf(ctx).height * 0.55,
              ),
              child: RoundedCard(
                color: palette.secondaryCard,
                margin: EdgeInsets.zero,
                radius: 16,
                child: ListView.separated(
                  shrinkWrap: true,
                  itemCount: weeks.length,
                  separatorBuilder: (_, _) =>
                      Divider(height: 1, color: palette.divider),
                  itemBuilder: (_, index) {
                    final week = weeks[index];
                    final isCurrent =
                        currentWeek != null && week.number == currentWeek.number;
                    final dateRange = week.dates.isNotEmpty
                        ? _formatRange(
                            week.dates.first,
                            week.dates.last,
                            includeYear: false,
                          )
                        : '';
                    final lessonCountText =
                        strings.timetableLessonCount(week.entries.length);
                    final summary = dateRange.isNotEmpty
                        ? '$dateRange · $lessonCountText'
                        : lessonCountText;
                    return PreferenceRow(
                      title: isCurrent
                          ? '${strings.timetableWeekNumber(week.number)} (${strings.timetableCurrentWeekTag})'
                          : strings.timetableWeekNumber(week.number),
                      summary: summary,
                      trailing: index == selectedIndex
                          ? Icon(
                              Icons.check_rounded,
                              color: Theme.of(ctx).colorScheme.primary,
                            )
                          : null,
                      onTap: () {
                        Navigator.of(ctx).pop();
                        _selectWeek(index);
                      },
                    );
                  },
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _showTermPicker(
    List<TimetableTerm> terms,
    TimetableTerm selected,
  ) async {
    final sorted = terms.toList()
      ..sort((a, b) => b.startDate.compareTo(a.startDate));
    final strings = widget.strings;
    final palette = TeishuPalette.of(context);

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
                strings.timetableTerm,
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
                  for (var i = 0; i < sorted.length; i++) ...[
                    if (i > 0) Divider(height: 1, color: palette.divider),
                    PreferenceRow(
                      title: strings.timetableTermName(
                        sorted[i].schoolYear,
                        sorted[i].number,
                      ),
                      summary: _formatRange(
                        sorted[i].startDate,
                        sorted[i].endDate,
                        includeYear: true,
                      ),
                      trailing: sorted[i].id == selected.id
                          ? Icon(
                              Icons.check_rounded,
                              color: Theme.of(ctx).colorScheme.primary,
                            )
                          : null,
                      onTap: () {
                        Navigator.of(ctx).pop();
                        setState(() {
                          _selectedTermId = sorted[i].id;
                          _failedTermIds.remove(sorted[i].id);
                        });
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

  Future<void> _showEntryDetail(TimetableEntry entry) async {
    final strings = widget.strings;
    final palette = TeishuPalette.of(context);
    final dayName = strings.weekdayFull(entry.day);
    final pos = entry.periodSpan > 1
        ? strings.timetableEntryPeriodRange(
            dayName,
            entry.startPeriod,
            entry.startPeriod + entry.periodSpan - 1,
          )
        : strings.timetableEntryPosition(dayName, entry.startPeriod);
    final scheduleText = strings.timetableEntrySchedule(
      pos,
      entry.startTimeText,
      entry.endTimeText,
    );

    await showModalBottomSheet<void>(
      context: context,
      useSafeArea: true,
      builder: (ctx) => _EntryDetailSheet(
        strings: strings,
        palette: palette,
        entry: entry,
        scheduleText: scheduleText,
      ),
    );
  }
}

class _WeekSwitcherBar extends StatelessWidget {
  const _WeekSwitcherBar({
    required this.strings,
    required this.weeks,
    required this.selectedIndex,
    required this.selectedWeek,
    required this.isBlurEnabled,
    required this.isLiquidGlassEnabled,
    required this.onSelectIndex,
    required this.onOpenWeekPicker,
  });

  final AppStrings strings;
  final List<TimetableWeek> weeks;
  final int selectedIndex;
  final TimetableWeek selectedWeek;
  final bool isBlurEnabled;
  final bool isLiquidGlassEnabled;
  final ValueChanged<int> onSelectIndex;
  final VoidCallback onOpenWeekPicker;

  @override
  Widget build(BuildContext context) {
    final palette = TeishuPalette.of(context);
    final theme = Theme.of(context);
    final hasPrev = selectedIndex > 0;
    final hasNext = selectedIndex < weeks.length - 1;

    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 420),
        child: LiquidGlassPillContainer(
          isBlurEnabled: isBlurEnabled,
          isLiquidGlass: isLiquidGlassEnabled,
          borderRadius: 28,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
            child: Row(
              children: [
                IconButton(
                  icon: const Icon(Icons.chevron_left_rounded),
                  tooltip: strings.timetablePreviousWeek,
                  onPressed:
                      hasPrev ? () => onSelectIndex(selectedIndex - 1) : null,
                ),
                Expanded(
                  child: InkWell(
                    borderRadius: BorderRadius.circular(20),
                    onTap: onOpenWeekPicker,
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 4),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            strings.timetableWeekNumber(selectedWeek.number),
                            style: theme.textTheme.titleSmall?.copyWith(
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          if (selectedWeek.dates.isNotEmpty)
                            Text(
                              _formatRange(
                                selectedWeek.dates.first,
                                selectedWeek.dates.last,
                                includeYear: false,
                              ),
                              style: theme.textTheme.labelSmall?.copyWith(
                                color: palette.mutedText,
                              ),
                            ),
                        ],
                      ),
                    ),
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.chevron_right_rounded),
                  tooltip: strings.timetableNextWeek,
                  onPressed:
                      hasNext ? () => onSelectIndex(selectedIndex + 1) : null,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _StatusMessage extends StatelessWidget {
  const _StatusMessage({
    required this.strings,
    required this.status,
    required this.onAction,
  });

  final AppStrings strings;
  final _TimetableStatus status;
  final VoidCallback onAction;

  @override
  Widget build(BuildContext context) {
    final palette = TeishuPalette.of(context);
    final message = switch (status) {
      _TimetableStatus.ready => null,
      _TimetableStatus.noLessons => strings.timetableNoLessons,
      _TimetableStatus.loading => strings.timetableLoading,
      _TimetableStatus.failed => strings.timetableLoadFailed,
      _TimetableStatus.notFetched => strings.timetableNotFetched,
    };
    final actionLabel = switch (status) {
      _TimetableStatus.failed => strings.timetableRetry,
      _TimetableStatus.notFetched => strings.timetableFetch,
      _ => null,
    };

    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (status == _TimetableStatus.loading)
              const Padding(
                padding: EdgeInsets.only(bottom: 12),
                child: CircularProgressIndicator(),
              ),
            if (message != null)
              Text(
                message,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                      color: palette.mutedText,
                    ),
              ),
            if (actionLabel != null) ...[
              const SizedBox(height: 12),
              FilledButton.tonal(
                onPressed: onAction,
                child: Text(actionLabel),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _EntryDetailSheet extends StatefulWidget {
  const _EntryDetailSheet({
    required this.strings,
    required this.palette,
    required this.entry,
    required this.scheduleText,
  });

  final AppStrings strings;
  final TeishuPalette palette;
  final TimetableEntry entry;
  final String scheduleText;

  @override
  State<_EntryDetailSheet> createState() => _EntryDetailSheetState();
}

class _EntryDetailSheetState extends State<_EntryDetailSheet> {
  bool _copiedLocation = false;

  Future<void> _copy(String value, {bool markLocation = false}) async {
    await Clipboard.setData(ClipboardData(text: value));
    if (!mounted) return;
    if (markLocation) {
      setState(() => _copiedLocation = true);
      Future<void>.delayed(const Duration(seconds: 2), () {
        if (mounted) setState(() => _copiedLocation = false);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final strings = widget.strings;
    final entry = widget.entry;
    final rows = <Widget>[
      PreferenceRow(
        title: strings.timetableDetailTime,
        summary: widget.scheduleText,
        onLongPress: () => _copy(widget.scheduleText),
      ),
      if (entry.location != null && entry.location!.isNotEmpty)
        PreferenceRow(
          title: strings.timetableDetailLocation,
          summary: entry.location!,
          onLongPress: () => _copy(entry.location!, markLocation: true),
          trailing: IconButton(
            onPressed: () => _copy(entry.location!, markLocation: true),
            tooltip: strings.timetableDetailCopy,
            icon: Icon(
              _copiedLocation ? Icons.check_rounded : Icons.copy_rounded,
              size: 20,
              color: _copiedLocation
                  ? Theme.of(context).colorScheme.primary
                  : widget.palette.mutedText,
            ),
          ),
        ),
      if (entry.teacher.isNotEmpty)
        PreferenceRow(
          title: strings.timetableDetailTeacher,
          summary: entry.teacher,
          onLongPress: () => _copy(entry.teacher),
        ),
    ];

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            child: Text(
              entry.title,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
            ),
          ),
          const SizedBox(height: 10),
          RoundedCard(
            color: widget.palette.secondaryCard,
            margin: EdgeInsets.zero,
            radius: 16,
            child: Column(
              children: [
                for (var i = 0; i < rows.length; i++) ...[
                  if (i > 0) Divider(height: 1, color: widget.palette.divider),
                  rows[i],
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

String _formatRange(
  ShanghaiDate start,
  ShanghaiDate end, {
  required bool includeYear,
}) {
  String fmt(ShanghaiDate d) => includeYear
      ? '${d.year}/${d.month}/${d.day}'
      : '${d.month}/${d.day}';
  return '${fmt(start)} – ${fmt(end)}';
}
