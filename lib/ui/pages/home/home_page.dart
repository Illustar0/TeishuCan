import 'package:material_ui/material_ui.dart';
import 'package:libzzu_ffi/libzzu_ffi.dart';

import '../../../domain/account.dart';
import '../../../domain/electricity.dart';
import '../../../domain/timetable.dart';
import '../../../l10n/app_strings.dart';
import '../../theme/app_theme.dart';
import '../../widgets/frosted_bars.dart';
import '../../widgets/period_strip.dart';
import '../../widgets/rounded_card.dart';
import '../../widgets/url_launcher_helper.dart';

class HomePage extends StatelessWidget {
  const HomePage({
    super.key,
    required this.strings,
    required this.profile,
    required this.terms,
    required this.campusCardBalanceYuan,
    required this.meters,
    required this.isRefreshing,
    required this.isBlurEnabled,
    required this.bottomPadding,
    required this.onRefresh,
    required this.onOpenRecharge,
    required this.onOpenElectricitySettings,
  });

  final AppStrings strings;
  final StudentProfile profile;
  final List<TimetableTerm> terms;
  final String? campusCardBalanceYuan;
  final List<MeterReading> meters;
  final bool isRefreshing;
  final bool isBlurEnabled;
  final double bottomPadding;
  final Future<void> Function() onRefresh;
  final VoidCallback onOpenRecharge;
  final VoidCallback onOpenElectricitySettings;

  @override
  Widget build(BuildContext context) {
    final upcoming = upcomingClassAfter(terms, ShanghaiDateTime.now());
    final totalKwh = totalKilowattHours(meters);
    final topInset = MediaQuery.paddingOf(context).top + kToolbarHeight;

    return Scaffold(
      extendBodyBehindAppBar: true,
      appBar: FrostedTopBar(
        title: strings.destinationHome,
        isBlurEnabled: isBlurEnabled,
      ),
      body: RefreshIndicator(
        edgeOffset: topInset,
        onRefresh: onRefresh,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: EdgeInsets.only(
            top: topInset,
            bottom: bottomPadding + 16,
          ),
          children: [
            _NextClassCard(strings: strings, upcoming: upcoming),
            _StatGrid(
              strings: strings,
              balanceYuan: campusCardBalanceYuan,
              totalKwh: totalKwh,
              hasMeters: meters.isNotEmpty,
              onOpenRecharge: onOpenRecharge,
              onOpenElectricitySettings: onOpenElectricitySettings,
            ),
            _ProfileCard(strings: strings, profile: profile),
            _LinksCard(strings: strings),
          ],
        ),
      ),
    );
  }
}

class SignedOutHomePage extends StatelessWidget {
  const SignedOutHomePage({
    super.key,
    required this.strings,
    required this.isBlurEnabled,
    required this.bottomPadding,
    required this.onLoginClick,
  });

  final AppStrings strings;
  final bool isBlurEnabled;
  final double bottomPadding;
  final VoidCallback onLoginClick;

  @override
  Widget build(BuildContext context) {
    final topInset = MediaQuery.paddingOf(context).top + kToolbarHeight;
    return Scaffold(
      extendBodyBehindAppBar: true,
      appBar: FrostedTopBar(
        title: strings.destinationHome,
        isBlurEnabled: isBlurEnabled,
      ),
      body: ListView(
        padding: EdgeInsets.only(
          top: topInset,
          bottom: bottomPadding + 16,
        ),
        children: [
          _LeadCard(
            icon: Icons.account_circle_outlined,
            onTap: onLoginClick,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _LeadTitle(text: strings.homeLoginTitle),
                const SizedBox(height: 2),
                _LeadDescription(text: strings.homeLoginSummary),
              ],
            ),
          ),
          _LinksCard(strings: strings),
        ],
      ),
    );
  }
}

class _LeadCard extends StatelessWidget {
  const _LeadCard({
    required this.icon,
    required this.child,
    this.onTap,
  });

  final IconData icon;
  final Widget child;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final palette = TeishuPalette.of(context);
    final primary = Theme.of(context).colorScheme.primary;

    return RoundedCard(
      color: palette.leadTint,
      onTap: onTap,
      child: Stack(
        clipBehavior: Clip.hardEdge,
        children: [
          Positioned(
            right: -42,
            bottom: -36,
            child: Icon(
              icon,
              size: 168,
              color: primary.withValues(alpha: 0.22),
            ),
          ),
          ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 132),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 92, 16),
              child: child,
            ),
          ),
        ],
      ),
    );
  }
}

class _NextClassCard extends StatelessWidget {
  const _NextClassCard({
    required this.strings,
    required this.upcoming,
  });

  final AppStrings strings;
  final UpcomingClass upcoming;

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;
    final theme = Theme.of(context);

    return _LeadCard(
      icon: Icons.calendar_month_rounded,
      child: switch (upcoming) {
        UpcomingNotFetched() => Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _LeadTitle(text: strings.homeNextClass),
              const SizedBox(height: 2),
              _LeadDescription(text: strings.homeNotFetched),
            ],
          ),
        UpcomingNone() => Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _LeadTitle(text: strings.homeNextClass),
              const SizedBox(height: 2),
              _LeadDescription(text: strings.homeNextClassNone),
            ],
          ),
        NextClass next => Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text.rich(
                TextSpan(
                  children: [
                    if (next.isTomorrow)
                      TextSpan(
                        text: '${strings.homeNextClassTomorrow} ',
                        style: TextStyle(color: primary),
                      ),
                    TextSpan(text: next.name),
                  ],
                ),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.titleLarge?.copyWith(
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 2),
              _LeadDescription(
                text: strings.homeNextClassTime(
                  next.startHour,
                  next.startMinute,
                ),
              ),
              const SizedBox(height: 28),
              _LeadDescription(text: next.location),
              const SizedBox(height: 10),
              _DayPeriodStrip(nextClass: next),
            ],
          ),
      },
    );
  }
}

class _LeadTitle extends StatelessWidget {
  const _LeadTitle({required this.text});
  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      maxLines: 2,
      overflow: TextOverflow.ellipsis,
      style: Theme.of(context).textTheme.titleLarge?.copyWith(
            fontWeight: FontWeight.w600,
          ),
    );
  }
}

class _LeadDescription extends StatelessWidget {
  const _LeadDescription({required this.text});
  final String text;

  @override
  Widget build(BuildContext context) {
    final onSurface = Theme.of(context).colorScheme.onSurface;
    return Text(
      text,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
            fontWeight: FontWeight.w500,
            color: onSurface.withValues(alpha: 0.78),
          ),
    );
  }
}

class _DayPeriodStrip extends StatelessWidget {
  const _DayPeriodStrip({required this.nextClass});

  final NextClass nextClass;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final emptyColor = scheme.onSurface.withValues(alpha: 0.14);
    final finishedColor = scheme.onSurface.withValues(alpha: 0.32);
    final upcomingColor = scheme.primary.withValues(alpha: 0.42);

    return PeriodStrip(
      periodColor: (period) {
        ClassPeriods? dayClass;
        for (final item in nextClass.dayClasses) {
          if (item.contains(period)) {
            dayClass = item;
            break;
          }
        }
        if (dayClass == null) return emptyColor;
        if (dayClass == nextClass.periods) return scheme.primary;
        if (dayClass.startPeriod < nextClass.periods.startPeriod) {
          return finishedColor;
        }
        return upcomingColor;
      },
    );
  }
}

class _StatGrid extends StatelessWidget {
  const _StatGrid({
    required this.strings,
    required this.balanceYuan,
    required this.totalKwh,
    required this.hasMeters,
    required this.onOpenRecharge,
    required this.onOpenElectricitySettings,
  });

  final AppStrings strings;
  final String? balanceYuan;
  final double? totalKwh;
  final bool hasMeters;
  final VoidCallback onOpenRecharge;
  final VoidCallback onOpenElectricitySettings;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 0),
      child: IntrinsicHeight(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(
              child: _StatCard(
                title: strings.homeCampusCardBalance,
                valueText: balanceYuan != null
                    ? formatYuan(balanceYuan!)
                    : strings.homeNotFetched,
                isPlaceholder: balanceYuan == null,
                hasArrow: false,
                onTap: onOpenRecharge,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _StatCard(
                title: strings.homeRemainingElectricity,
                valueText: totalKwh != null
                    ? formatKilowattHours(totalKwh!)
                    : (hasMeters
                        ? strings.homeNotFetched
                        : strings.homeElectricityEmpty),
                unitText: totalKwh != null ? strings.homeKilowattHourUnit : null,
                isPlaceholder: totalKwh == null,
                hasArrow: true,
                onTap: onOpenElectricitySettings,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _StatCard extends StatelessWidget {
  const _StatCard({
    required this.title,
    required this.valueText,
    this.unitText,
    required this.isPlaceholder,
    required this.hasArrow,
    required this.onTap,
  });

  final String title;
  final String valueText;
  final String? unitText;
  final bool isPlaceholder;
  final bool hasArrow;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final palette = TeishuPalette.of(context);
    final theme = Theme.of(context);

    return RoundedCard(
      margin: EdgeInsets.zero,
      padding: const EdgeInsets.all(16),
      onTap: onTap,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodySmall?.copyWith(
                    fontWeight: FontWeight.w500,
                    color: palette.mutedText,
                  ),
                ),
              ),
              if (hasArrow)
                Icon(
                  Icons.chevron_right_rounded,
                  size: 18,
                  color: palette.mutedText,
                ),
            ],
          ),
          const SizedBox(height: 12),
          if (isPlaceholder)
            Text(
              valueText,
              style: theme.textTheme.bodySmall?.copyWith(
                color: palette.mutedText,
              ),
            )
          else
            Text.rich(
              TextSpan(
                children: [
                  TextSpan(
                    text: valueText,
                    style: theme.textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  if (unitText != null)
                    TextSpan(
                      text: ' $unitText',
                      style: theme.textTheme.bodySmall?.copyWith(
                        fontWeight: FontWeight.w500,
                        color: palette.mutedText,
                      ),
                    ),
                ],
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
        ],
      ),
    );
  }
}

class _ProfileCard extends StatelessWidget {
  const _ProfileCard({required this.strings, required this.profile});

  final AppStrings strings;
  final StudentProfile profile;

  @override
  Widget build(BuildContext context) {
    return RoundedCard(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          _ProfileRow(
            icon: Icons.person_outline_rounded,
            title: strings.homeProfileName,
            content: profile.name,
          ),
          const SizedBox(height: 20),
          _ProfileRow(
            icon: Icons.school_outlined,
            title: strings.homeProfileFaculty,
            content: profile.faculty,
          ),
          const SizedBox(height: 20),
          _ProfileRow(
            icon: Icons.badge_outlined,
            title: strings.homeProfileIdentity,
            content: profile.identityType,
          ),
        ],
      ),
    );
  }
}

class _ProfileRow extends StatelessWidget {
  const _ProfileRow({
    required this.icon,
    required this.title,
    required this.content,
  });

  final IconData icon;
  final String title;
  final String content;

  @override
  Widget build(BuildContext context) {
    final palette = TeishuPalette.of(context);
    final theme = Theme.of(context);
    return Row(
      children: [
        Icon(icon, size: 22),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: theme.textTheme.bodyLarge?.copyWith(
                  fontWeight: FontWeight.w500,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                content,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: palette.mutedText,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _LinksCard extends StatelessWidget {
  const _LinksCard({required this.strings});

  final AppStrings strings;

  @override
  Widget build(BuildContext context) {
    final palette = TeishuPalette.of(context);
    return RoundedCard(
      child: Column(
        children: [
          PreferenceRow(
            leading: const Icon(Icons.favorite_outline_rounded),
            title: strings.homeSupportTitle,
            summary: strings.homeSupportSummary,
            trailing: const ChevronTrailing(),
            onTap: () => openCampusUrl(strings.homeSupportUrl),
          ),
          Divider(height: 1, color: palette.divider),
          PreferenceRow(
            leading: const Icon(Icons.info_outline_rounded),
            title: strings.homeLearnTitle,
            summary: strings.homeLearnSummary,
            trailing: const ChevronTrailing(),
            onTap: () => openCampusUrl(strings.homeLearnUrl),
          ),
        ],
      ),
    );
  }
}
