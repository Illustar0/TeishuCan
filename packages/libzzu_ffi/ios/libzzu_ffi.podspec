Pod::Spec.new do |s|
  s.name             = 'libzzu_ffi'
  s.version          = '0.2.0'
  s.summary          = 'Dart FFI bindings and native plugin for libzzu.'
  s.description      = <<-DESC
Dart FFI bindings and native iOS/Android bridge for libzzu.
                       DESC
  s.homepage         = 'https://github.com/Illustar0/libzzu'
  s.license          = { :type => 'GPL-3.0' }
  s.author           = { 'TeishuCan Contributors' => 'noreply@example.com' }
  s.source           = { :path => '.' }
  s.source_files     = 'Classes/**/*'
  s.public_header_files = 'Classes/libzzu_force_link.h'
  s.dependency       'Flutter'
  s.platform         = :ios, '15.0'
  s.vendored_frameworks = 'Frameworks/libzzu.xcframework'
  s.frameworks       = 'Security', 'CoreFoundation', 'SystemConfiguration'
  s.library          = 'resolv'
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
    'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386'
  }
  s.swift_version    = '5.0'
end
