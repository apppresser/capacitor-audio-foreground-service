import Foundation
import Capacitor

@objc(AudioForegroundPlugin)
public class AudioForegroundPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "AudioForegroundPlugin"
    public let jsName = "AudioForeground"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "start", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "update", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stop", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isRunning", returnType: CAPPluginReturnPromise)
    ]

    @objc func start(_ call: CAPPluginCall) {
        call.resolve(["running": false])
    }

    @objc func update(_ call: CAPPluginCall) {
        call.resolve([:])
    }

    @objc func stop(_ call: CAPPluginCall) {
        call.resolve([:])
    }

    @objc func isRunning(_ call: CAPPluginCall) {
        call.resolve(["running": false])
    }
}
