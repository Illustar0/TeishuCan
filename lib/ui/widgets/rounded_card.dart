import 'package:material_ui/material_ui.dart';

import '../theme/app_theme.dart';

class RoundedCard extends StatelessWidget {
  const RoundedCard({
    super.key,
    required this.child,
    this.color,
    this.margin = const EdgeInsets.fromLTRB(12, 12, 12, 0),
    this.padding = EdgeInsets.zero,
    this.radius = 20,
    this.onTap,
  });

  final Widget child;
  final Color? color;
  final EdgeInsetsGeometry margin;
  final EdgeInsetsGeometry padding;
  final double radius;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final palette = TeishuPalette.of(context);
    final shape = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(radius),
    );
    return Padding(
      padding: margin,
      child: Material(
        color: color ?? palette.card,
        shape: shape,
        clipBehavior: Clip.antiAlias,
        child: onTap == null
            ? Padding(padding: padding, child: child)
            : InkWell(
                onTap: onTap,
                customBorder: shape,
                child: Padding(padding: padding, child: child),
              ),
      ),
    );
  }
}

class SectionTitle extends StatelessWidget {
  const SectionTitle(
    this.text, {
    super.key,
    this.padding = const EdgeInsets.fromLTRB(24, 16, 24, 0),
  });

  final String text;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    final palette = TeishuPalette.of(context);
    return Padding(
      padding: padding,
      child: Text(
        text,
        style: Theme.of(context).textTheme.labelLarge?.copyWith(
              color: palette.mutedText,
              fontWeight: FontWeight.w600,
            ),
      ),
    );
  }
}

class PreferenceRow extends StatelessWidget {
  const PreferenceRow({
    super.key,
    required this.title,
    this.summary,
    this.leading,
    this.trailing,
    this.titleColor,
    this.onTap,
    this.onLongPress,
  });

  final String title;
  final String? summary;
  final Widget? leading;
  final Widget? trailing;
  final Color? titleColor;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;

  @override
  Widget build(BuildContext context) {
    final palette = TeishuPalette.of(context);
    final theme = Theme.of(context);

    return InkWell(
      onTap: onTap,
      onLongPress: onLongPress,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        child: Row(
          children: [
            if (leading != null) ...[
              IconTheme(
                data: IconThemeData(color: theme.colorScheme.onSurface, size: 22),
                child: leading!,
              ),
              const SizedBox(width: 14),
            ],
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: theme.textTheme.bodyLarge?.copyWith(
                      fontWeight: FontWeight.w500,
                      color: titleColor ?? theme.colorScheme.onSurface,
                    ),
                  ),
                  if (summary != null && summary!.isNotEmpty) ...[
                    const SizedBox(height: 2),
                    Text(
                      summary!,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: palette.mutedText,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            if (trailing != null) ...[
              const SizedBox(width: 8),
              trailing!,
            ],
          ],
        ),
      ),
    );
  }
}

class ChevronTrailing extends StatelessWidget {
  const ChevronTrailing({super.key, this.valueText});

  final String? valueText;

  @override
  Widget build(BuildContext context) {
    final palette = TeishuPalette.of(context);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (valueText != null) ...[
          Text(
            valueText!,
            style: Theme.of(context)
                .textTheme
                .bodyMedium
                ?.copyWith(color: palette.mutedText),
          ),
          const SizedBox(width: 4),
        ],
        Icon(
          Icons.chevron_right_rounded,
          size: 20,
          color: palette.mutedText,
        ),
      ],
    );
  }
}

String formatYuan(String raw) => '¥$raw';

String formatKilowattHours(double value) => value.toStringAsFixed(2);
