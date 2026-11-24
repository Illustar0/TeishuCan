import Flutter
import UIKit

public class LibzzuFfiPlugin: NSObject, FlutterPlugin {
  private static let forceLinked = libzzu_force_link()

  public static func register(with registrar: FlutterPluginRegistrar) {
    _ = forceLinked
    let channel = FlutterMethodChannel(
      name: "com.illustar0.libzzu_ffi",
      binaryMessenger: registrar.messenger()
    )
    let instance = LibzzuFfiPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    if call.method == "getSystemRootCertificatesPem" {
      result(nil)
    } else {
      result(FlutterMethodNotImplemented)
    }
  }
}
