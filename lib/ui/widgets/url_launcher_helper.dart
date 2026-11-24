import 'package:url_launcher/url_launcher.dart';

Future<bool> openCampusUrl(String rawUrl) async {
  final uri = Uri.tryParse(rawUrl);
  if (uri == null || (uri.scheme != 'http' && uri.scheme != 'https')) {
    return false;
  }
  try {
    final launched = await launchUrl(uri, mode: LaunchMode.inAppBrowserView);
    if (launched) return true;
  } catch (_) {
    // Fallback to external browser on desktop or when in-app view is unavailable.
  }
  try {
    return await launchUrl(uri, mode: LaunchMode.externalApplication);
  } catch (_) {
    return false;
  }
}
