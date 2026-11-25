import Foundation
import SwiftUI
import UIKit

// What the Valiu app sends (the same data the Android widgets use). Every field is optional so an
// older or newer app version never breaks the widget.
struct VDay: Decodable { let d: String; let daily: Double?; let spent: Double? }
struct VNext: Decodable { let name: String?; let amt: Double?; let due: String? }
struct VMonth: Decodable { let y: Int?; let m: Int?; let title: String?; let wds: [String]?; let due: [String: String]? }
struct VFormat: Decodable { let pre: String?; let post: String?; let dec: String?; let grp: String? }
struct VData: Decodable {
    let day: String?
    let days: [VDay]?
    let mascot: String?
    let month: VMonth?
    let next: [VNext]?
    let nf: VFormat?
    let str: [String: String]?
}

enum ValiuStore {
    static let appGroup = "group.com.valiugt.app"

    static func load() -> VData? {
        guard let s = UserDefaults(suiteName: appGroup)?.string(forKey: "widgetData"),
              let raw = s.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(VData.self, from: raw)
    }

    /// Made-up numbers for the widget gallery, so people see what they're adding.
    static func sample(for date: Date) -> VData {
        let cal = Calendar.current
        let y = cal.component(.year, from: date), m = cal.component(.month, from: date), d = cal.component(.day, from: date)
        let iso = ValiuDates.iso(date)
        func dayIso(_ n: Int) -> String { ValiuDates.iso(cal.date(byAdding: .day, value: n, to: date) ?? date) }
        var due: [String: String] = ["1": "paid"]
        if d > 3 { due[String(d - 2)] = "due" }
        due[String(min(d + 1, 28))] = "due"
        due[String(min(d + 4, 28))] = "due"
        return VData(day: iso, days: [VDay(d: iso, daily: 300, spent: 63)], mascot: "en-racha",
                     month: VMonth(y: y, m: m, title: nil, wds: nil, due: due),
                     next: [VNext(name: "Internet", amt: 350, due: dayIso(-2)), VNext(name: "Luz", amt: 280, due: dayIso(1)),
                            VNext(name: "Préstamo moto", amt: 950, due: dayIso(4))],
                     nf: nil, str: nil)
    }
}

enum ValiuDates {
    static let isoFormatter: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone.current
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()
    static func iso(_ d: Date) -> String { isoFormatter.string(from: d) }
    static func parse(_ s: String?) -> Date? { s.flatMap { isoFormatter.date(from: $0) } }
    /// Whole days from `from` to the ISO date (negative when it already passed).
    static func days(from: Date, to iso: String?) -> Int? {
        guard let target = parse(iso) else { return nil }
        let cal = Calendar.current
        return cal.dateComponents([.day], from: cal.startOfDay(for: from), to: cal.startOfDay(for: target)).day
    }
    /// The next few midnights, so the widget turns the page by itself even if Valiu isn't opened.
    static func timeline(from now: Date, days: Int = 7) -> [Date] {
        let cal = Calendar.current
        var out = [now]
        var d = cal.startOfDay(for: now)
        for _ in 0..<days {
            guard let next = cal.date(byAdding: .day, value: 1, to: d) else { break }
            out.append(next.addingTimeInterval(5))
            d = next
        }
        return out
    }
}

extension VData {
    func text(_ key: String, _ fallback: String) -> String {
        if let v = str?[key], !v.isEmpty { return v }
        return fallback
    }

    /// Money the way the app writes it (it sends its own prefix, suffix and separators).
    func money(_ value: Double) -> String {
        let pre = nf?.pre ?? "Q", post = nf?.post ?? "", dec = nf?.dec ?? ".", grp = nf?.grp ?? ","
        let cents = Int((abs(value) * 100).rounded())
        let whole = String(cents / 100), frac = String(format: "%02d", cents % 100)
        var grouped = ""
        for (i, ch) in whole.reversed().enumerated() {
            if i > 0 && i % 3 == 0 { grouped.append(contentsOf: grp.reversed()) }
            grouped.append(ch)
        }
        return (value < 0 ? "-" : "") + pre + String(grouped.reversed()) + dec + frac + post
    }
}

// Valiu's colours, light and dark.
extension UIColor {
    convenience init(hex: UInt32) {
        self.init(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
    }
}
extension Color {
    static func valiu(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light) })
    }
}
enum VC {
    static let bg = Color.valiu(0xFFFFFF, 0x15362F)
    static let text = Color.valiu(0x12302A, 0xEAF5EE)
    static let muted = Color.valiu(0x52695F, 0xA3BFB3)
    static let faint = Color.valiu(0x8AA197, 0x6D8C80)
    static let accent = Color.valiu(0x1E8150, 0x4CCB86)
    static let accentSoft = Color.valiu(0xDCF1E2, 0x264F42)
    static let warn = Color.valiu(0xA06400, 0xF2B43C)
    static let warnSoft = Color.valiu(0xFBEFD3, 0x4A4326)
    static let danger = Color.valiu(0xC9423F, 0xFF7D7D)
    static let dangerSoft = Color.valiu(0xFBE3E1, 0x4D2E2E)
    static let track = Color.valiu(0xE5EFE3, 0x234D43)
    static let onAccent = Color.valiu(0xFFFFFF, 0x072117)
}

/// The frog image for a mascot name sent by the app.
func frogImage(_ name: String?) -> String {
    switch name ?? "" {
    case "ahorrando": return "frog_ahorrando"
    case "en-racha": return "frog_en_racha"
    case "nivel-superior": return "frog_nivel_superior"
    case "en-alerta": return "frog_en_alerta"
    case "preocupado": return "frog_preocupado"
    case "sin-fondos": return "frog_sin_fondos"
    default: return "frog_equilibrio"
    }
}

struct PlusButton: View {
    var size: CGFloat = 30
    var body: some View {
        ZStack {
            Circle().fill(VC.accent)
            Image(systemName: "plus").font(.system(size: size * 0.5, weight: .bold)).foregroundStyle(VC.onAccent)
        }
        .frame(width: size, height: size)
    }
}

let valiuURL = (add: URL(string: "valiu://add")!, today: URL(string: "valiu://today")!, payments: URL(string: "valiu://payments")!)
