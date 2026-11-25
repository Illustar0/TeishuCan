import 'dart:async';

import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:libzzu_ffi/libzzu_ffi.dart';

import '../../../data/zzu_session.dart';
import '../../../domain/account.dart';
import '../../../domain/campus_exception.dart';
import '../../../l10n/app_strings.dart';
import '../../theme/app_theme.dart';
import '../../widgets/rounded_card.dart';

enum _LoginAction { login, sendCode, verifyCode }

Future<void> showLoginSheet({
  required BuildContext context,
  required AppStrings strings,
  required ZzuSessionController session,
}) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => _LoginSheet(strings: strings, session: session),
  );
}

class _LoginSheet extends StatefulWidget {
  const _LoginSheet({required this.strings, required this.session});

  final AppStrings strings;
  final ZzuSessionController session;

  @override
  State<_LoginSheet> createState() => _LoginSheetState();
}

class _LoginSheetState extends State<_LoginSheet> {
  final TextEditingController _accountController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();
  final TextEditingController _mfaCodeController = TextEditingController();

  _LoginAction? _activeAction;
  String? _failureText;
  bool _inMfa = false;
  String? _securePhone;
  bool _isCodeRequested = false;
  bool _shouldTrustDevice = true;
  int _resendSecondsLeft = 0;
  Timer? _resendTimer;

  bool get _isBusy => _activeAction != null;

  @override
  void dispose() {
    _resendTimer?.cancel();
    if (_inMfa) {
      widget.session.abandonLogin();
    }
    _accountController.dispose();
    _passwordController.dispose();
    _mfaCodeController.dispose();
    super.dispose();
  }

  Future<void> _submitCredentials() async {
    final account = _accountController.text.trim();
    final password = _passwordController.text;
    if (account.isEmpty || password.isEmpty || _isBusy) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _activeAction = _LoginAction.login;
      _failureText = null;
    });

    try {
      final result = await widget.session.login(account, password);
      if (!mounted) return;
      switch (result) {
        case LoginSignedIn():
          _inMfa = false;
          Navigator.of(context).pop();
        case LoginMfaRequired(securePhone: final phone):
          setState(() {
            _inMfa = true;
            _securePhone = phone;
            _isCodeRequested = false;
            _mfaCodeController.clear();
          });
      }
    } on CampusRequestException catch (e) {
      if (e.cause is ZzuAuthenticationException) {
        _passwordController.clear();
      }
      if (!mounted) return;
      setState(() {
        _failureText = _formatLoginFailure(e, widget.strings.loginFailed);
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _failureText = widget.strings.loginFailed;
      });
    } finally {
      if (mounted) {
        setState(() => _activeAction = null);
      }
    }
  }

  Future<void> _sendMfaCode() async {
    if (_isBusy || _resendSecondsLeft > 0) return;
    setState(() {
      _activeAction = _LoginAction.sendCode;
      _failureText = null;
    });

    try {
      final phone = await widget.session.sendMfaCode();
      if (!mounted) return;
      setState(() {
        if (phone != null) _securePhone = phone;
        _isCodeRequested = true;
        _startCountdown();
      });
    } on CampusRequestException catch (e) {
      if (!mounted) return;
      setState(() {
        _failureText = e.reason ?? widget.strings.mfaSendFailed;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _failureText = widget.strings.mfaSendFailed;
      });
    } finally {
      if (mounted) {
        setState(() => _activeAction = null);
      }
    }
  }

  Future<void> _verifyMfa() async {
    final code = _mfaCodeController.text.trim();
    if (!_isCodeRequested || code.isEmpty || _isBusy) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _activeAction = _LoginAction.verifyCode;
      _failureText = null;
    });

    try {
      await widget.session.verifyMfa(
        code,
        shouldTrustDevice: _shouldTrustDevice,
      );
      if (!mounted) return;
      _inMfa = false;
      Navigator.of(context).pop();
    } on CampusRequestException catch (e) {
      _mfaCodeController.clear();
      if (!mounted) return;
      setState(() {
        _failureText = e.reason ?? widget.strings.mfaFailed;
      });
    } catch (_) {
      _mfaCodeController.clear();
      if (!mounted) return;
      setState(() {
        _failureText = widget.strings.mfaFailed;
      });
    } finally {
      if (mounted) {
        setState(() => _activeAction = null);
      }
    }
  }

  void _startCountdown() {
    _resendTimer?.cancel();
    _resendSecondsLeft = 60;
    _resendTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!mounted || _resendSecondsLeft <= 1) {
        timer.cancel();
        if (mounted) setState(() => _resendSecondsLeft = 0);
        return;
      }
      setState(() => _resendSecondsLeft--);
    });
  }

  void _leaveMfa() {
    if (_isBusy) return;
    _resendTimer?.cancel();
    widget.session.abandonLogin();
    setState(() {
      _inMfa = false;
      _failureText = null;
      _resendSecondsLeft = 0;
    });
  }

  @override
  Widget build(BuildContext context) {
    final strings = widget.strings;
    final palette = TeishuPalette.of(context);
    final theme = Theme.of(context);
    final bottomInset = MediaQuery.viewInsetsOf(context).bottom;

    return PopScope(
      canPop: !_isBusy,
      child: Padding(
        padding: EdgeInsets.fromLTRB(20, 12, 20, 20 + bottomInset),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                IconButton(
                  onPressed: _isBusy
                      ? null
                      : (_inMfa ? _leaveMfa : () => Navigator.of(context).pop()),
                  icon: Icon(
                    _inMfa ? Icons.arrow_back_rounded : Icons.close_rounded,
                  ),
                ),
                Expanded(
                  child: Text(
                    _inMfa ? strings.mfaTitle : strings.settingsLogin,
                    textAlign: TextAlign.center,
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                const SizedBox(width: 48),
              ],
            ),
            const SizedBox(height: 12),
            if (!_inMfa)
              _buildCredentialsForm(strings, palette, theme)
            else
              _buildMfaForm(strings, palette, theme),
          ],
        ),
      ),
    );
  }

  Widget _buildCredentialsForm(
    AppStrings strings,
    TeishuPalette palette,
    ThemeData theme,
  ) {
    final canSubmit = _accountController.text.trim().isNotEmpty &&
        _passwordController.text.isNotEmpty &&
        !_isBusy;

    return AutofillGroup(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextField(
            controller: _accountController,
            enabled: !_isBusy,
            autofillHints: const [AutofillHints.username],
            onChanged: (_) => setState(() {}),
            decoration: InputDecoration(
              labelText: strings.loginAccount,
              filled: true,
              fillColor: palette.secondaryCard,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(14),
                borderSide: BorderSide.none,
              ),
            ),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _passwordController,
            enabled: !_isBusy,
            obscureText: true,
            autofillHints: const [AutofillHints.password],
            onChanged: (_) => setState(() {}),
            onSubmitted: (_) => _submitCredentials(),
            decoration: InputDecoration(
              labelText: strings.loginPassword,
              filled: true,
              fillColor: palette.secondaryCard,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(14),
                borderSide: BorderSide.none,
              ),
            ),
          ),
          if (_failureText != null) ...[
            const SizedBox(height: 8),
            Text(
              _failureText!,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.error,
              ),
            ),
          ],
          const SizedBox(height: 8),
          Text(
            strings.loginCredentialsNotice,
            style: theme.textTheme.bodySmall?.copyWith(color: palette.mutedText),
          ),
          const SizedBox(height: 14),
          FilledButton(
            onPressed: canSubmit ? _submitCredentials : null,
            style: FilledButton.styleFrom(
              minimumSize: const Size.fromHeight(48),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(14),
              ),
            ),
            child: _activeAction == _LoginAction.login
                ? Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      ),
                      const SizedBox(width: 10),
                      Text(strings.loginSubmitting),
                    ],
                  )
                : Text(strings.settingsLogin),
          ),
        ],
      ),
    );
  }

  Widget _buildMfaForm(
    AppStrings strings,
    TeishuPalette palette,
    ThemeData theme,
  ) {
    final canEnterCode = _isCodeRequested && !_isBusy;
    final helper = switch ((_isCodeRequested, _securePhone)) {
      (true, final phone?) => strings.mfaCodeSentPhone(phone),
      (true, null) => strings.mfaCodeSent,
      (false, final phone?) => strings.mfaCodeDestinationPhone(phone),
      (false, null) => strings.mfaCodeDestination,
    };

    final sendLabel = switch ((_activeAction == _LoginAction.sendCode,
        _isCodeRequested, _resendSecondsLeft > 0)) {
      (true, _, _) => strings.mfaSending,
      (false, false, _) => strings.mfaSend,
      (false, true, true) => strings.mfaResendCountdown(_resendSecondsLeft),
      (false, true, false) => strings.mfaResend,
    };

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        TextField(
          controller: _mfaCodeController,
          enabled: canEnterCode,
          keyboardType: TextInputType.number,
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          autofillHints: const [AutofillHints.oneTimeCode],
          onChanged: (_) => setState(() {}),
          decoration: InputDecoration(
            labelText: strings.mfaCode,
            filled: true,
            fillColor: palette.secondaryCard,
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(14),
              borderSide: BorderSide.none,
            ),
          ),
        ),
        const SizedBox(height: 8),
        Text(
          helper,
          style: theme.textTheme.bodySmall?.copyWith(color: palette.mutedText),
        ),
        if (_failureText != null) ...[
          const SizedBox(height: 6),
          Text(
            _failureText!,
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.error,
            ),
          ),
        ],
        const SizedBox(height: 12),
        RoundedCard(
          color: palette.secondaryCard,
          margin: EdgeInsets.zero,
          radius: 16,
          child: PreferenceRow(
            title: strings.mfaTrustDevice,
            summary: strings.mfaTrustDeviceSummary,
            trailing: Switch(
              value: _shouldTrustDevice,
              onChanged: _isBusy
                  ? null
                  : (v) => setState(() => _shouldTrustDevice = v),
            ),
            onTap: _isBusy
                ? null
                : () => setState(() => _shouldTrustDevice = !_shouldTrustDevice),
          ),
        ),
        const SizedBox(height: 14),
        Row(
          children: [
            Expanded(
              child: FilledButton.tonal(
                onPressed:
                    _resendSecondsLeft == 0 && !_isBusy ? _sendMfaCode : null,
                style: FilledButton.styleFrom(
                  minimumSize: const Size.fromHeight(46),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14),
                  ),
                ),
                child: Text(sendLabel),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: FilledButton(
                onPressed: canEnterCode && _mfaCodeController.text.isNotEmpty
                    ? _verifyMfa
                    : null,
                style: FilledButton.styleFrom(
                  minimumSize: const Size.fromHeight(46),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14),
                  ),
                ),
                child: Text(
                  _activeAction == _LoginAction.verifyCode
                      ? strings.mfaVerifying
                      : strings.mfaVerify,
                ),
              ),
            ),
          ],
        ),
      ],
    );
  }
}

String _formatLoginFailure(CampusRequestException error, String fallback) {
  final reason = error.reason?.trim();
  final base = (reason != null && reason.isNotEmpty) ? reason : fallback;
  final cause = error.cause;
  if (cause is ZzuTransportException || cause is ZzuRuntimeException) {
    final rawDetail = (cause as ZzuException).cause?.trim();
    if (rawDetail != null && rawDetail.isNotEmpty && !base.contains(rawDetail)) {
      final compact = rawDetail.replaceAll(RegExp(r'\s+'), ' ');
      final shortDetail =
          compact.length > 120 ? '${compact.substring(0, 120)}…' : compact;
      return '$base ($shortDetail)';
    }
  }
  return base;
}
