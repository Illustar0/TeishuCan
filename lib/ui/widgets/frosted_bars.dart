import 'dart:ui';

import 'package:material_ui/material_ui.dart';

import '../theme/app_theme.dart';

class FrostedTopBar extends StatelessWidget implements PreferredSizeWidget {
  const FrostedTopBar({
    super.key,
    required this.title,
    this.subtitle,
    this.leading,
    this.actions,
    this.bottom,
    this.isBlurEnabled = true,
    this.transparent = false,
  });

  final String title;
  final String? subtitle;
  final Widget? leading;
  final List<Widget>? actions;
  final PreferredSizeWidget? bottom;
  final bool isBlurEnabled;
  final bool transparent;

  @override
  Size get preferredSize => Size.fromHeight(
        kToolbarHeight + (bottom?.preferredSize.height ?? 0),
      );

  @override
  Widget build(BuildContext context) {
    final palette = TeishuPalette.of(context);
    final theme = Theme.of(context);
    final bgColor = transparent
        ? Colors.transparent
        : (isBlurEnabled ? palette.frostedBar : palette.background);

    final bar = AppBar(
      backgroundColor: bgColor,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      leading: leading,
      titleSpacing: leading == null ? 20 : 4,
      title: subtitle == null || subtitle!.isEmpty
          ? Text(
              title,
              style: theme.textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.w700,
              ),
            )
          : Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
                Text(
                  subtitle!,
                  style: theme.textTheme.labelMedium?.copyWith(
                    color: palette.mutedText,
                  ),
                ),
              ],
            ),
      actions: actions,
      bottom: bottom,
    );

    if (transparent || !isBlurEnabled) {
      return bar;
    }

    return ClipRect(
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 24, sigmaY: 24),
        child: Container(
          decoration: BoxDecoration(
            border: Border(
              bottom: BorderSide(
                color: palette.divider.withValues(alpha: 0.45),
                width: 0.5,
              ),
            ),
          ),
          child: bar,
        ),
      ),
    );
  }
}

class TeishuNavDestination {
  const TeishuNavDestination({
    required this.label,
    required this.icon,
    required this.selectedIcon,
  });

  final String label;
  final IconData icon;
  final IconData selectedIcon;
}

class TeishuBottomBar extends StatelessWidget {
  const TeishuBottomBar({
    super.key,
    required this.destinations,
    required this.selectedIndex,
    required this.onSelect,
    required this.isFloating,
    required this.isBlurEnabled,
  });

  final List<TeishuNavDestination> destinations;
  final int selectedIndex;
  final ValueChanged<int> onSelect;
  final bool isFloating;
  final bool isBlurEnabled;

  @override
  Widget build(BuildContext context) {
    final palette = TeishuPalette.of(context);
    final theme = Theme.of(context);

    if (isFloating) {
      return SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
          child: Center(
            heightFactor: 1,
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(32),
                child: BackdropFilter(
                  filter: isBlurEnabled
                      ? ImageFilter.blur(sigmaX: 24, sigmaY: 24)
                      : ImageFilter.blur(sigmaX: 0, sigmaY: 0),
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      color: isBlurEnabled ? palette.frostedBar : palette.card,
                      borderRadius: BorderRadius.circular(32),
                      border: Border.all(
                        color: palette.divider.withValues(alpha: 0.7),
                        width: 0.8,
                      ),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withValues(alpha: 0.08),
                          blurRadius: 18,
                          offset: const Offset(0, 6),
                        ),
                      ],
                    ),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 6,
                      ),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                        children: [
                          for (var i = 0; i < destinations.length; i++)
                            _FloatingNavItem(
                              destination: destinations[i],
                              selected: i == selectedIndex,
                              onTap: () => onSelect(i),
                            ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      );
    }

    final bar = NavigationBar(
      selectedIndex: selectedIndex,
      onDestinationSelected: onSelect,
      backgroundColor: isBlurEnabled ? palette.frostedBar : palette.card,
      surfaceTintColor: Colors.transparent,
      indicatorColor: theme.colorScheme.primary.withValues(alpha: 0.14),
      destinations: destinations
          .map(
            (d) => NavigationDestination(
              icon: Icon(d.icon),
              selectedIcon: Icon(d.selectedIcon, color: theme.colorScheme.primary),
              label: d.label,
            ),
          )
          .toList(growable: false),
    );

    if (!isBlurEnabled) {
      return DecoratedBox(
        decoration: BoxDecoration(
          border: Border(
            top: BorderSide(
              color: palette.divider.withValues(alpha: 0.6),
              width: 0.5,
            ),
          ),
        ),
        child: bar,
      );
    }

    return ClipRect(
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 24, sigmaY: 24),
        child: DecoratedBox(
          decoration: BoxDecoration(
            border: Border(
              top: BorderSide(
                color: palette.divider.withValues(alpha: 0.5),
                width: 0.5,
              ),
            ),
          ),
          child: bar,
        ),
      ),
    );
  }
}

class _FloatingNavItem extends StatelessWidget {
  const _FloatingNavItem({
    required this.destination,
    required this.selected,
    required this.onTap,
  });

  final TeishuNavDestination destination;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final palette = TeishuPalette.of(context);
    final color = selected ? theme.colorScheme.primary : palette.mutedText;

    return Expanded(
      child: GestureDetector(
        onTap: onTap,
        behavior: HitTestBehavior.opaque,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 220),
          curve: Curves.easeOutCubic,
          padding: const EdgeInsets.symmetric(vertical: 8),
          decoration: BoxDecoration(
            color: selected
                ? theme.colorScheme.primary.withValues(alpha: 0.14)
                : Colors.transparent,
            borderRadius: BorderRadius.circular(24),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                selected ? destination.selectedIcon : destination.icon,
                color: color,
                size: 22,
              ),
              const SizedBox(height: 2),
              Text(
                destination.label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.labelSmall?.copyWith(
                  color: color,
                  fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
