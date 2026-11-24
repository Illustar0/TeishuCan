import 'package:material_ui/material_ui.dart';

class PeriodStrip extends StatelessWidget {
  const PeriodStrip({
    super.key,
    required this.periodColor,
    this.segmentHeight = 6,
    this.segmentGap = 3,
    this.separatorGap = 6,
    this.separatorDotSize = 4,
    this.separatorColor,
  });

  final Color Function(int period) periodColor;
  final double segmentHeight;
  final double segmentGap;
  final double separatorGap;
  final double separatorDotSize;
  final Color? separatorColor;

  static const List<List<int>> _groups = <List<int>>[
    [1, 2, 3, 4],
    [5, 6, 7, 8],
    [9, 10],
  ];

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final dotColor =
        separatorColor ?? scheme.onSurface.withValues(alpha: 0.35);
    final height =
        segmentHeight > separatorDotSize ? segmentHeight : separatorDotSize;

    return SizedBox(
      width: double.infinity,
      height: height,
      child: CustomPaint(
        painter: _PeriodStripPainter(
          periodColor: periodColor,
          segmentHeight: segmentHeight,
          segmentGap: segmentGap,
          separatorGap: separatorGap,
          separatorDotSize: separatorDotSize,
          separatorColor: dotColor,
          isRtl: Directionality.of(context) == TextDirection.rtl,
        ),
      ),
    );
  }
}

class _PeriodStripPainter extends CustomPainter {
  _PeriodStripPainter({
    required this.periodColor,
    required this.segmentHeight,
    required this.segmentGap,
    required this.separatorGap,
    required this.separatorDotSize,
    required this.separatorColor,
    required this.isRtl,
  });

  final Color Function(int period) periodColor;
  final double segmentHeight;
  final double segmentGap;
  final double separatorGap;
  final double separatorDotSize;
  final Color separatorColor;
  final bool isRtl;

  static const int _totalPeriods = 10;
  static const int _separatorCount = 2;

  @override
  void paint(Canvas canvas, Size size) {
    final dotRadius = separatorDotSize / 2;
    final barRadius = Radius.circular(segmentHeight / 2);
    final barTop = (size.height - segmentHeight) / 2;
    final centerY = size.height / 2;

    final separatorWidth = separatorDotSize + 2 * separatorGap;
    final groupsTotalWidth =
        (size.width - _separatorCount * separatorWidth).clamp(0.0, size.width);

    var cursorX = 0.0;
    for (var g = 0; g < PeriodStrip._groups.length; g++) {
      final group = PeriodStrip._groups[g];
      if (g > 0) {
        cursorX += separatorGap;
        final dotCenterX =
            isRtl ? size.width - cursorX - dotRadius : cursorX + dotRadius;
        canvas.drawCircle(
          Offset(dotCenterX, centerY),
          dotRadius,
          Paint()..color = separatorColor,
        );
        cursorX += separatorDotSize + separatorGap;
      }

      final groupSize = group.length;
      final groupWidth = groupsTotalWidth * (groupSize / _totalPeriods);
      final segWidth = ((groupWidth - (groupSize - 1) * segmentGap) / groupSize)
          .clamp(0.0, groupWidth);

      var segX = cursorX;
      for (final period in group) {
        final drawX = isRtl ? size.width - segX - segWidth : segX;
        final rect = RRect.fromRectAndRadius(
          Rect.fromLTWH(drawX, barTop, segWidth, segmentHeight),
          barRadius,
        );
        canvas.drawRRect(rect, Paint()..color = periodColor(period));
        segX += segWidth + segmentGap;
      }
      cursorX += groupWidth;
    }
  }

  @override
  bool shouldRepaint(covariant _PeriodStripPainter oldDelegate) => true;
}
