import 'package:material_ui/material_ui.dart';
import 'package:libzzu_ffi/libzzu_ffi.dart';

import '../../../domain/timetable.dart';
import '../../../l10n/app_strings.dart';
import '../../theme/app_theme.dart';

class TimetableGrid extends StatelessWidget {
  const TimetableGrid({
    super.key,
    required this.strings,
    required this.week,
    required this.today,
    required this.onEntryTap,
    this.periodCount = 10,
    this.periodHeight = 64,
    this.periodLabelWidth = 26,
  });

  final AppStrings strings;
  final TimetableWeek week;
  final ShanghaiDate today;
  final ValueChanged<TimetableEntry> onEntryTap;
  final int periodCount;
  final double periodHeight;
  final double periodLabelWidth;

  @override
  Widget build(BuildContext context) {
    final palette = TeishuPalette.of(context);
    final primary = Theme.of(context).colorScheme.primary;
    final theme = Theme.of(context);
    final visibleEntries = week.entries
        .where((e) => e.startPeriod >= 1 && e.startPeriod <= periodCount && e.periodSpan >= 1)
        .toList(growable: false);

    return Column(
      children: [
        Row(
          children: [
            SizedBox(width: periodLabelWidth),
            for (var weekday = 1; weekday <= 7; weekday++)
              Expanded(
                child: _DayHeaderCell(
                  label: strings.weekdayShort(weekday),
                  date: _dateForWeekday(week.dates, weekday),
                  isToday: _dateForWeekday(week.dates, weekday) == today,
                  normalColor: palette.mutedText,
                  highlightColor: primary,
                ),
              ),
          ],
        ),
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              width: periodLabelWidth,
              child: Column(
                children: [
                  for (var i = 1; i <= periodCount; i++)
                    SizedBox(
                      height: periodHeight,
                      child: Center(
                        child: Text(
                          '$i',
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: palette.mutedText,
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ),
            Expanded(
              child: SizedBox(
                height: periodHeight * periodCount,
                child: LayoutBuilder(
                  builder: (context, constraints) {
                    final dayWidth = constraints.maxWidth / 7;
                    const gap = 2.0;
                    return Stack(
                      children: [
                        Positioned.fill(
                          child: CustomPaint(
                            painter: _GridLinesPainter(
                              periodCount: periodCount,
                              lineColor: palette.divider,
                            ),
                          ),
                        ),
                        for (final entry in visibleEntries)
                          Positioned(
                            left: (entry.day - 1) * dayWidth + gap / 2,
                            top: (entry.startPeriod - 1) * periodHeight + gap / 2,
                            width: (dayWidth - gap).clamp(0.0, double.infinity),
                            height: ((entry.periodSpan
                                        .clamp(1, periodCount - entry.startPeriod + 1) *
                                    periodHeight) -
                                gap)
                                .clamp(0.0, double.infinity),
                            child: _EntryCell(
                              entry: entry,
                              cellColor: palette.timetableCell,
                              textColor: palette.timetableCellText,
                              onTap: () => onEntryTap(entry),
                            ),
                          ),
                      ],
                    );
                  },
                ),
              ),
            ),
          ],
        ),
      ],
    );
  }

  ShanghaiDate? _dateForWeekday(List<ShanghaiDate> dates, int weekday) {
    for (final d in dates) {
      if (d.weekday == weekday) return d;
    }
    return null;
  }
}

class _DayHeaderCell extends StatelessWidget {
  const _DayHeaderCell({
    required this.label,
    required this.date,
    required this.isToday,
    required this.normalColor,
    required this.highlightColor,
  });

  final String label;
  final ShanghaiDate? date;
  final bool isToday;
  final Color normalColor;
  final Color highlightColor;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = isToday ? highlightColor : normalColor;
    final weight = isToday ? FontWeight.w700 : FontWeight.w400;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Column(
        children: [
          Text(
            label,
            maxLines: 1,
            style: theme.textTheme.labelSmall?.copyWith(
              color: color,
              fontWeight: weight,
            ),
          ),
          if (date != null)
            Text(
              '${date!.day}',
              style: theme.textTheme.labelSmall?.copyWith(
                color: color,
                fontWeight: weight,
              ),
            ),
        ],
      ),
    );
  }
}

class _GridLinesPainter extends CustomPainter {
  _GridLinesPainter({required this.periodCount, required this.lineColor});

  final int periodCount;
  final Color lineColor;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = lineColor
      ..strokeWidth = 0.6;
    final dayWidth = size.width / 7;
    final rowHeight = size.height / periodCount;

    for (var p = 0; p <= periodCount; p++) {
      final y = p * rowHeight;
      canvas.drawLine(Offset(0, y), Offset(size.width, y), paint);
    }
    for (var d = 0; d <= 7; d++) {
      final x = d * dayWidth;
      canvas.drawLine(Offset(x, 0), Offset(x, size.height), paint);
    }
  }

  @override
  bool shouldRepaint(covariant _GridLinesPainter oldDelegate) =>
      oldDelegate.lineColor != lineColor ||
      oldDelegate.periodCount != periodCount;
}

class _EntryCell extends StatelessWidget {
  const _EntryCell({
    required this.entry,
    required this.cellColor,
    required this.textColor,
    required this.onTap,
  });

  final TimetableEntry entry;
  final Color cellColor;
  final Color textColor;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final locationColor = textColor.withValues(alpha: 0.65);

    return Material(
      color: cellColor,
      borderRadius: BorderRadius.circular(6),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(4),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Text(
                  entry.title,
                  overflow: TextOverflow.ellipsis,
                  maxLines: 6,
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: textColor,
                    fontWeight: FontWeight.w500,
                    fontSize: 11,
                    height: 1.15,
                  ),
                ),
              ),
              if (entry.location != null && entry.location!.isNotEmpty)
                Text(
                  entry.location!,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: locationColor,
                    fontSize: 10,
                    height: 1.1,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
