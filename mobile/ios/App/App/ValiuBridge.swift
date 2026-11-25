import Foundation
import Capacitor
import WidgetKit

// Valiu's own iPhone features for the web app (Capacitor.Plugins.ValiuIOS).
// Today: hands the widgets what to show. The widgets read it from the shared App Group.
@objc(ValiuIOSPlugin)
public class ValiuIOSPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "ValiuIOSPlugin"
    public let jsName = "ValiuIOS"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "setWidgetData", returnType: CAPPluginReturnPromise)
    ]

    static let appGroup = "group.com.valiugt.app"

    @objc func setWidgetData(_ call: CAPPluginCall) {
        guard let json = call.getString("json") else {
            call.reject("json")
            return
        }
        let defaults = UserDefaults(suiteName: ValiuIOSPlugin.appGroup) ?? UserDefaults.standard
        defaults.set(json, forKey: "widgetData")
        WidgetCenter.shared.reloadAllTimelines()
        call.resolve()
    }
}

// The app's screen: Capacitor's web view plus Valiu's own plugin.
class MainViewController: CAPBridgeViewController {
    override func capacitorDidLoad() {
        bridge?.registerPluginInstance(ValiuIOSPlugin())
    }
}
