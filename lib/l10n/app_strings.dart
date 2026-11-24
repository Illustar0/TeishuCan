import 'package:flutter/widgets.dart';

import '../domain/campus_service.dart';
import '../domain/theme_settings.dart';

class AppStrings {
  const AppStrings._(this.isChinese);

  final bool isChinese;

  static AppStrings resolve(AppLanguage language, Locale? systemLocale) {
    final useZh = switch (language) {
      AppLanguage.chinese => true,
      AppLanguage.japanese => false,
      AppLanguage.system => systemLocale?.languageCode == 'zh',
    };
    return AppStrings._(useZh);
  }

  String get appName => 'TeishuCan';

  String get destinationHome => isChinese ? '首页' : 'ホーム';
  String get destinationTimetable => isChinese ? '课表' : '時間割';
  String get destinationServices => isChinese ? '服务' : 'サービス';
  String get destinationSettings => isChinese ? '设置' : '設定';
  String get navigateBack => isChinese ? '返回' : '戻る';

  String get servicesSearchPlaceholder => isChinese ? '搜索服务' : 'サービスを検索';
  String get servicesSearchCancel => isChinese ? '取消' : 'キャンセル';
  String get servicesSearchClear => isChinese ? '清除' : 'クリア';
  String get servicesSearchEmpty =>
      isChinese ? '未找到相关服务' : '該当するサービスが見つかりません';
  String get servicesLibrary => isChinese ? '图书馆' : '図書館';
  String get servicesStudentAffairs => isChinese ? '学生事务' : '学生サービス';
  String get servicesAcademic => isChinese ? '教务' : '教務';
  String get servicesEasRecords => isChinese ? '学籍与培养' : '学籍と教育課程';
  String get servicesEasExamsAndGrades => isChinese ? '考试与成绩' : '試験と成績';
  String get servicesEasSchedule => isChinese ? '课表与教室' : '時間割と教室';

  String get serviceUndergradEas => isChinese ? '本科教务系统' : '学部教務システム';
  String get serviceUndergradEasSummary =>
      isChinese ? '学籍、考试与成绩' : '学籍、試験、成績';

  String serviceTitle(CampusService service) => switch (service) {
        CampusService.library => isChinese ? '图书馆门户' : '図書館ポータル',
        CampusService.libraryReservation => isChinese ? '空间预约' : 'スペース予約',
        CampusService.campusCard => isChinese ? '一卡通' : '学生カード',
        CampusService.mail => isChinese ? '邮箱' : 'メール',
        CampusService.leave => isChinese ? '请假' : '休暇申請',
        CampusService.presidentMailbox =>
          isChinese ? '校长信箱' : '学長メールボックス',
        CampusService.studentStatus => isChinese ? '学籍信息' : '学籍情報',
        CampusService.programCompletion => isChinese ? '培养方案' : '教育課程',
        CampusService.academicWarnings => isChinese ? '学业预警' : '学業警告',
        CampusService.exams => isChinese ? '考试' : '試験',
        CampusService.grades => isChinese ? '成绩' : '成績',
        CampusService.timetable => isChinese ? '课表' : '時間割',
        CampusService.courseOfferings => isChinese ? '全校开课' : '全学開講科目',
        CampusService.schoolCalendar => isChinese ? '校历' : '学年暦',
        CampusService.availableClassrooms => isChinese ? '空闲教室' : '空き教室',
        CampusService.classroomTimetable => isChinese ? '教室课表' : '教室時間割',
      };

  String serviceSummary(CampusService service) => switch (service) {
        CampusService.library => isChinese ? '馆藏检索与借阅' : '蔵書検索と貸出',
        CampusService.libraryReservation =>
          isChinese ? '预约座位与研讨间' : '座席やディスカッションルームを予約',
        CampusService.campusCard =>
          isChinese ? '校园卡相关服务' : '学生カード関連サービス',
        CampusService.mail => isChinese ? '通过校园邮箱收发邮件' : '学内メールの送受信',
        CampusService.leave => isChinese ? '提交和查看请假申请' : '休暇の申請と確認',
        CampusService.presidentMailbox =>
          isChinese ? '向校长提交意见与建议' : '学長に意見や提案を送信',
        CampusService.studentStatus =>
          isChinese ? '查询个人学籍信息' : '個人の学籍情報を確認',
        CampusService.programCompletion =>
          isChinese ? '查看培养方案完成情况' : '教育課程の修了状況を確認',
        CampusService.academicWarnings =>
          isChinese ? '查询学业预警记录' : '学業警告の記録を確認',
        CampusService.exams => isChinese ? '查询考试安排' : '試験日程を確認',
        CampusService.grades => isChinese ? '查询课程成绩' : '科目の成績を確認',
        CampusService.timetable =>
          isChinese ? '查询教务系统中的课表' : '教務システムの時間割を確認',
        CampusService.courseOfferings =>
          isChinese ? '查询全校开设的课程' : '全学の開講科目を検索',
        CampusService.schoolCalendar =>
          isChinese ? '查看学期与假期安排' : '学期と休暇の日程を確認',
        CampusService.availableClassrooms =>
          isChinese ? '查询空闲的教室' : '空いている教室を検索',
        CampusService.classroomTimetable =>
          isChinese ? '查询各教室的课程安排' : '教室ごとの授業予定を確認',
      };

  String get homeNextClass => isChinese ? '下一节课' : '次の授業';
  String get homeRemainingElectricity => isChinese ? '剩余电量' : '残り電力量';
  String get homeElectricityEmpty =>
      isChinese ? '点击添加电表' : 'タップしてメーターを追加';
  String get homeKilowattHourUnit => 'kWh';
  String get homeProfileName => isChinese ? '姓名' : '氏名';
  String get homeProfileFaculty => isChinese ? '学院' : '学部';
  String get homeProfileIdentity => isChinese ? '身份类型' : '身分';

  String get homeSupportTitle => isChinese ? '支持开发' : '開発を支援';
  String get homeSupportSummary => isChinese
      ? 'TeishuCan 是一款开源且免费的软件。向开发者捐助以表示支持。'
      : 'TeishuCan はオープンソースの無料ソフトウェアです。開発者への寄付で支援できます。';
  String get homeSupportUrl => 'https://afdian.com/a/Illustar0';
  String get homeLearnTitle =>
      isChinese ? '了解 TeishuCan' : 'TeishuCan について知る';
  String get homeLearnSummary =>
      isChinese ? '了解功能和使用方法' : '機能や使い方を確認します';
  String get homeLearnUrl => 'https://github.com/Illustar0/TeishuCan';
  String get homeCampusCardBalance => isChinese ? '校园卡余额' : '学生カードの残高';
  String get homeNotFetched => isChinese ? '未获取' : '未取得';

  String get campusCardRechargeNotice => isChinese
      ? '这是一项实验性功能，使用风险由您自行承担。'
      : 'これは実験的な機能です。ご利用は自己責任でお願いします。';
  String get campusCardRechargeNext => isChinese ? '下一步' : '次へ';
  String get campusCardRechargeChannelTitle =>
      isChinese ? '选择支付方式' : '支払い方法を選択';
  String get campusCardRechargeAmount => isChinese ? '充值金额' : 'チャージ金額';
  String get campusCardRechargeFailed => isChinese
      ? '充值未能继续，请稍后再试'
      : 'チャージを続行できませんでした。しばらくしてからもう一度お試しください';

  String get homeLoginTitle => isChinese ? '未登录' : '未ログイン';
  String get homeLoginSummary => isChinese ? '点击登录' : 'タップしてログイン';
  String homeNextClassTime(int hour, int minute) {
    final mm = minute.toString().padLeft(2, '0');
    return isChinese ? '下一节课 $hour 点 $mm 分' : '次の授業 $hour 時 $mm 分';
  }

  String get homeNextClassTomorrow => isChinese ? '明天' : '明日';
  String get homeNextClassNone =>
      isChinese ? '暂无。享受生活吧~' : '予定なし。人生を楽しもう〜';

  String weekdayShort(int weekday) {
    if (isChinese) {
      return const ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日'][weekday];
    }
    return const ['', '月', '火', '水', '木', '金', '土', '日'][weekday];
  }

  String weekdayFull(int weekday) {
    if (isChinese) {
      return const ['', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日']
          [weekday];
    }
    return const ['', '月曜日', '火曜日', '水曜日', '木曜日', '金曜日', '土曜日', '日曜日']
        [weekday];
  }

  String timetableEntryPosition(String dayName, int period) =>
      isChinese ? '$dayName第 $period 节' : '$dayName $period 限';

  String timetableEntryPeriodRange(String dayName, int start, int end) =>
      isChinese ? '$dayName第 $start–$end 节' : '$dayName $start〜$end 限';

  String timetableEntrySchedule(String pos, String start, String end) =>
      isChinese ? '$pos · $start – $end' : '$pos・$start – $end';

  String get timetableDetailTime => isChinese ? '时间' : '時間';
  String get timetableDetailLocation => isChinese ? '地点' : '場所';
  String get timetableDetailTeacher => isChinese ? '老师' : '担当教員';
  String get timetableDetailCopy => isChinese ? '复制' : 'コピー';
  String get timetableTerm => isChinese ? '学期' : '学期';
  String timetableTermName(String year, String number) =>
      isChinese ? '$year 学年第 $number 学期' : '$year 年度第 $number 学期';
  String timetableWeekNumber(int number) => '第 $number ${isChinese ? '周' : '週'}';
  String get timetablePreviousWeek => isChinese ? '上一周' : '前の週';
  String get timetableNextWeek => isChinese ? '下一周' : '次の週';
  String get timetableBackToCurrentWeek =>
      isChinese ? '回到本周' : '今週に戻る';
  String get timetableNotFetched => isChinese ? '课表未获取' : '時間割は未取得です';
  String get timetableFetch => isChinese ? '获取课表' : '時間割を取得';
  String get timetableLoading => isChinese ? '课表加载中…' : '時間割を読み込み中…';
  String get timetableLoadFailed =>
      isChinese ? '课表加载失败' : '時間割を読み込めませんでした';
  String get timetableRetry => isChinese ? '重试' : '再試行';
  String get timetableNoLessons =>
      isChinese ? '本学期没有课程' : '今学期の授業はありません';

  String get electricityMeterEmpty => isChinese ? '还没有电表' : 'メーターはまだありません';
  String get electricityMeterEmptyHint =>
      isChinese ? '点右上角 + 添加' : '右上の + から追加できます';
  String electricityMeterKilowattHours(String value) => '$value kWh';
  String get electricityMeterAdd => isChinese ? '添加电表' : 'メーターを追加';
  String get electricityMeterEdit => isChinese ? '编辑电表' : 'メーターを編集';
  String get electricityMeterName =>
      isChinese ? '名称（例如：空调）' : '名前（例：エアコン）';
  String get electricityMeterLocation => isChinese ? '位置' : '場所';
  String get electricityMeterChooseLocation => isChinese ? '选择位置' : '場所を選択';
  String get electricityMeterNotSelected => isChinese ? '未选择' : '未選択';
  String get electricityMeterSave => isChinese ? '保存' : '保存';
  String get electricityMeterClose => isChinese ? '关闭' : '閉じる';
  String get electricityMeterDelete => isChinese ? '删除' : '削除';
  String get electricityMeterEditAction => isChinese ? '编辑' : '編集';
  String get electricityRecharge => isChinese ? '充值' : 'チャージ';
  String get electricityRechargeAmount =>
      isChinese ? '金额（元）' : '金額（人民元）';

  String get refreshFailed =>
      isChinese ? '刷新失败，请重试' : '更新に失敗しました。もう一度お試しください';
  String get profileRefreshFailed =>
      isChinese ? '个人信息刷新失败，请重试' : '個人情報の更新に失敗しました';
  String get timetableRefreshFailed =>
      isChinese ? '课表刷新失败，请重试' : '時間割の更新に失敗しました';
  String get campusCardRefreshFailed =>
      isChinese ? '校园卡信息刷新失败，请重试' : '学生カード情報の更新に失敗しました';
  String get electricityRefreshFailed =>
      isChinese ? '电费信息刷新失败，请重试' : '電気料金情報の更新に失敗しました';
  String get serviceOpenFailed => isChinese
      ? '无法打开服务，请稍后再试'
      : 'サービスを開けませんでした。しばらくしてからもう一度お試しください';
  String get electricityMeterReselectLocation => isChinese
      ? '部分电表位置已失效，请编辑电表并重新选择位置'
      : '一部のメーターの場所が無効です。編集して場所を選び直してください';

  String get mfaCodeDestination => isChinese
      ? '验证码将发送至绑定的安全手机'
      : '登録済みの認証用携帯電話に確認コードを送信します';
  String mfaCodeDestinationPhone(String phone) => isChinese
      ? '验证码将发送至 $phone'
      : '$phone に確認コードを送信します';
  String get mfaCodeSent => isChinese
      ? '验证码已发送至绑定的安全手机'
      : '登録済みの認証用携帯電話に確認コードを送信しました';
  String mfaCodeSentPhone(String phone) => isChinese
      ? '验证码已发送至 $phone'
      : '$phone に確認コードを送信しました';

  String electricityRechargeBalance(String balance) =>
      isChinese ? '校园卡余额：$balance' : '学生カードの残高：$balance';
  String get electricityRechargePassword =>
      isChinese ? '支付密码（6 位数字）' : '支払いパスワード（数字 6 桁）';
  String get electricityRechargeConfirm => isChinese ? '确认充值' : 'チャージする';
  String get electricityRechargeSubmitting => isChinese ? '支付中…' : '処理中…';
  String get electricityRechargeSuccess => isChinese ? '充值成功' : 'チャージしました';
  String get electricityRechargeFailed => isChinese
      ? '支付失败，未扣款，请稍后再试'
      : '支払いに失敗しました。しばらくしてからもう一度お試しください';
  String get electricityRechargeUnconfirmed => isChinese
      ? '无法确认支付结果，可能已扣款。请先核对校园卡余额，不要直接重试'
      : '支払い結果を確認できませんでした。引き落とされた可能性があります。再度チャージする前に学生カードの残高を確認してください';

  List<String> get meterLevelTitles => isChinese
      ? const ['校区', '楼栋', '楼层', '房间']
      : const ['キャンパス', '建物', '階', '部屋'];
  String get meterOptionsEmpty => isChinese ? '没有可选项' : '選択できる項目はありません';
  String get meterOptionsFailed => isChinese ? '加载失败' : '読み込めませんでした';
  String get meterOptionsRetry => isChinese ? '重试' : '再試行';

  String get settingsAbout => isChinese ? '关于' : 'このアプリについて';
  String get settingsAboutSummary => isChinese ? '版本信息与许可证' : 'バージョン情報とライセンス';
  String get settingsAboutSourceCode => isChinese ? '源代码' : 'ソースコード';
  String get settingsAboutSourceCodeSummary => isChinese
      ? '在 GitHub 上查看源代码'
      : 'GitHub でソースコードを表示します';
  String get settingsAboutLicense => isChinese ? '许可证' : 'ライセンス';
  String get settingsAboutLicenseSummary => isChinese
      ? '本应用依据 GPL-3.0 协议发布'
      : 'このアプリは GPL-3.0 で公開されています';
  String get settingsAboutOpenSourceLicenses =>
      isChinese ? '开源许可证' : 'オープンソースライセンス';
  String get settingsAboutOpenSourceLicensesSummary => isChinese
      ? '本应用使用的第三方开源库'
      : 'このアプリで使用しているライブラリ';

  String get settingsElectricity => isChinese ? '电表管理' : '電力メーター';
  String get settingsElectricitySummary =>
      isChinese ? '添加或管理宿舍电表' : 'メーターを追加・管理します';
  String get settingsLanguage => isChinese ? '语言' : '言語';
  String get languageSystem => isChinese ? '跟随系统' : 'システムに従う';
  String get languageJapanese => '日本語';
  String get languageChinese => '简体中文';

  String get settingsLogin => isChinese ? '登录' : 'ログイン';
  String get settingsLoginSummary =>
      isChinese ? '登录后同步课表和校园卡信息' : 'ログインして時間割やカード情報を同期します';
  String get settingsLogout => isChinese ? '退出登录' : 'ログアウト';
  String get loginAccount => isChinese ? '账户' : 'アカウント';
  String get loginPassword => isChinese ? '密码' : 'パスワード';
  String get loginSubmitting => isChinese ? '登录中…' : 'ログイン中…';
  String get loginFailed =>
      isChinese ? '登录失败，请检查账户和密码' : 'ログインに失敗しました。アカウントとパスワードを確認してください';
  String get loginCredentialsNotice => isChinese
      ? '您的账户与密码将会存储在本地以刷新 Token，并受系统安全存储（Keychain / KeyStore）保护。'
      : 'アカウントとパスワードは、トークンを更新するために端末内に保存され、システムの安全なストレージ（Keychain / KeyStore）によって保護されます。';

  String get mfaTitle => isChinese ? '手机验证' : 'SMS 認証';
  String get mfaCode => isChinese ? '验证码' : '確認コード';
  String get mfaSend => isChinese ? '获取验证码' : 'コードを取得';
  String get mfaSending => isChinese ? '发送中…' : '送信中…';
  String get mfaVerify => isChinese ? '验证' : '確認';
  String get mfaVerifying => isChinese ? '验证中…' : '確認中…';
  String get mfaResend => isChinese ? '重新发送' : '再送信';
  String mfaResendCountdown(int seconds) =>
      isChinese ? '重新发送（$seconds）' : '再送信（$seconds）';
  String get mfaFailed => isChinese
      ? '验证码不正确，请重新输入'
      : '確認コードが正しくありません。もう一度入力してください';
  String get mfaSendFailed =>
      isChinese ? '发送失败，请稍后再试' : '送信できませんでした。しばらくしてからもう一度お試しください';
  String get mfaTrustDevice => isChinese ? '信任此设备' : 'この端末を信頼する';
  String get mfaTrustDeviceSummary => isChinese
      ? '之后在此设备登录时无需手机验证'
      : '次回以降、この端末でのログイン時に SMS 認証を省略します';

  String get logoutTitle => isChinese ? '确定要退出登录吗？' : 'ログアウトしますか？';
  String get logoutSummary =>
      isChinese ? '保存在本机的账户密码将被删除。' : '端末に保存されたアカウントとパスワードは削除されます。';
  String get logoutCancel => isChinese ? '取消' : 'キャンセル';
  String get logoutFailed =>
      isChinese ? '退出登录失败，请重试' : 'ログアウトできませんでした。もう一度お試しください';

  String get settingsTheme => isChinese ? '主题' : 'テーマ';
  String get settingsThemeSummary =>
      isChinese ? '自定义外观和配色' : '外観とカラーをカスタマイズします';
  String get settingsThemeMode => isChinese ? '深色模式' : 'ダークモード';
  String get settingsThemeSeedColor => isChinese ? '主题色' : 'テーマカラー';
  String get settingsThemeBlur => isChinese ? '模糊效果' : 'ぼかし効果';
  String get settingsThemeBlurSummary =>
      isChinese ? '模糊顶栏和底栏后方的内容' : 'バーの背後のコンテンツをぼかします';
  String get settingsThemeFloatingNavBar => isChinese ? '悬浮底栏' : 'フローティングナビゲーションバー';
  String get settingsThemeFloatingNavBarSummary =>
      isChinese ? '让底部导航栏悬浮显示' : '下部のナビゲーションバーを浮かせて表示します';

  String themeModeLabel(AppThemeMode mode) => switch (mode) {
        AppThemeMode.system => isChinese ? '跟随系统' : 'システム',
        AppThemeMode.light => isChinese ? '浅色' : 'ライト',
        AppThemeMode.dark => isChinese ? '深色' : 'ダーク',
      };

  String seedColorLabel(SeedColor seed) => switch (seed) {
        SeedColor.blue => isChinese ? '蓝色' : 'ブルー',
        SeedColor.green => isChinese ? '绿色' : 'グリーン',
        SeedColor.purple => isChinese ? '紫色' : 'パープル',
        SeedColor.yellow => isChinese ? '黄色' : 'イエロー',
        SeedColor.orange => isChinese ? '橙色' : 'オレンジ',
        SeedColor.pink => isChinese ? '粉色' : 'ピンク',
        SeedColor.teal => isChinese ? '青色' : 'ティール',
        SeedColor.red => isChinese ? '红色' : 'レッド',
      };
}
