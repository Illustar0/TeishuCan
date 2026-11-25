import 'dart:math' as math;

import 'package:material_ui/material_ui.dart';

class TeishuCanLogo extends StatelessWidget {
  const TeishuCanLogo({
    super.key,
    this.size = 84,
    this.showTile = true,
  });

  final double size;
  final bool showTile;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final tileBg = showTile ? const Color(0xFFF3F4F6) : Colors.transparent;
    final foreground = showTile
        ? const Color(0xFF141414)
        : (isDark ? const Color(0xFFF3F4F6) : const Color(0xFF141414));

    return Container(
      width: size,
      height: size,
      decoration: showTile
          ? BoxDecoration(
              color: tileBg,
              borderRadius: BorderRadius.circular(size * 0.24),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: isDark ? 0.35 : 0.08),
                  blurRadius: 18,
                  offset: const Offset(0, 6),
                ),
              ],
            )
          : null,
      child: CustomPaint(
        size: Size.square(size),
        painter: _TeishuCanLogoPainter(color: foreground),
      ),
    );
  }
}

class _TeishuCanLogoPainter extends CustomPainter {
  const _TeishuCanLogoPainter({required this.color});

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.save();
    // Crop Android's 108x108 adaptive icon canvas to its centered 72x72 safe area.
    final scale = math.min(size.width, size.height) / 72.0;
    canvas.scale(scale, scale);
    canvas.translate(-18.0, -18.0);
    // Match Android VectorDrawable group transform: translate(-0.5, -1), rotate(-4) around (54, 54).
    canvas.translate(54.0 - 0.5, 54.0 - 1.0);
    canvas.rotate(-4.0 * math.pi / 180.0);
    canvas.translate(-54.0, -54.0);

    final strokePaint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 4.0
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    final facePath = Path()
      ..moveTo(41, 53)
      ..lineTo(41, 68.5)
      ..cubicTo(41, 73.8, 44.8, 76.8, 50, 76.8)
      ..lineTo(58, 76.8)
      ..cubicTo(63.2, 76.8, 67, 73.8, 67, 68.5)
      ..lineTo(67, 53);
    canvas.drawPath(facePath, strokePaint);

    final capPath = Path()
      ..moveTo(52, 32)
      ..quadraticBezierTo(54, 31, 56, 32)
      ..lineTo(72.2, 40)
      ..quadraticBezierTo(74.2, 41, 72.2, 42)
      ..lineTo(56, 50)
      ..quadraticBezierTo(54, 51, 52, 50)
      ..lineTo(35.8, 42)
      ..quadraticBezierTo(33.8, 41, 35.8, 40)
      ..close();
    canvas.drawPath(capPath, strokePaint);

    final tasselStem = Path()
      ..moveTo(73.5, 42.2)
      ..cubicTo(74.5, 46, 74.3, 50.2, 73.6, 54.2);
    canvas.drawPath(tasselStem, strokePaint);

    final fillPaint = Paint()
      ..color = color
      ..style = PaintingStyle.fill;
    canvas.drawOval(
      Rect.fromCenter(center: const Offset(49.2, 62.5), width: 4.6, height: 6.6),
      fillPaint,
    );
    canvas.drawOval(
      Rect.fromCenter(center: const Offset(58.8, 62.5), width: 4.6, height: 6.6),
      fillPaint,
    );
    canvas.drawCircle(const Offset(73.6, 59.0), 2.2, fillPaint);

    canvas.restore();
  }

  @override
  bool shouldRepaint(_TeishuCanLogoPainter oldDelegate) =>
      oldDelegate.color != color;
}
