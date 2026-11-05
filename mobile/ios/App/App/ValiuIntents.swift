import AppIntents
import Foundation

// Valiu's own actions for the Shortcuts app. They show up by themselves once the app is installed,
// so the automation "Transaction" and Back Tap only need to pick them: no link to paste and no JSON.
// The personal link is created inside Valiu (Perfil → Apple Pay automático) and kept on this phone.

@available(iOS 16.0, *)
enum ValiuLink {
    // Saved by the web app through @capacitor/preferences, which writes to UserDefaults with this prefix.
    static var url: URL? {
        guard let s = UserDefaults.standard.string(forKey: "CapacitorStorage.valiuWalletLink"), s.hasPrefix("https://") else { return nil }
        return URL(string: s)
    }

    static func send(amount: String, merchant: String?, card: String) async throws -> String {
        guard let url = url else {
            throw ValiuError.notSetUp
        }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.timeoutInterval = 20
        var body: [String: String] = ["amount": amount, "card": card]
        if let m = merchant?.trimmingCharacters(in: .whitespacesAndNewlines), !m.isEmpty { body["merchant"] = m }
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (_, res) = try await URLSession.shared.data(for: req)
        guard let http = res as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw ValiuError.server
        }
        return body["merchant"] ?? ""
    }
}

@available(iOS 16.0, *)
enum ValiuError: Error, CustomLocalizedStringResourceConvertible {
    case notSetUp, server, badAmount
    var localizedStringResource: LocalizedStringResource {
        switch self {
        case .notSetUp: return "Primero activa los gastos automáticos en Valiu: Perfil → Apple Pay automático."
        case .server: return "Valiu no pudo guardar el gasto. Revisa tu conexión e inténtalo otra vez."
        case .badAmount: return "Escribe un monto mayor que cero."
        }
    }
}

// For the "Transaction" automation: Wallet passes the amount, the store and the card.
@available(iOS 16.0, *)
struct RegistrarCompraIntent: AppIntent {
    static let title: LocalizedStringResource = "Registrar compra en Valiu"
    static let description = IntentDescription("Anota en Valiu una compra hecha con Apple Pay.")
    static let openAppWhenRun = false

    // Text on purpose: Wallet's amount arrives as "Q45.00" and Valiu's server reads it as is.
    @Parameter(title: "Monto") var amount: String
    @Parameter(title: "Comercio") var merchant: String?
    @Parameter(title: "Tarjeta") var card: String?

    static var parameterSummary: some ParameterSummary {
        Summary("Registrar \(\.$amount) en \(\.$merchant)") { \.$card }
    }

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let name = try await ValiuLink.send(amount: amount, merchant: merchant, card: card ?? "Apple Pay")
        let msg = name.isEmpty ? "Anotado en Valiu: \(amount)" : "Anotado en Valiu: \(amount) en \(name)"
        return .result(dialog: "\(msg)")
    }
}

// For Back Tap (or Siri): asks how much and on what. Great for cash.
@available(iOS 16.0, *)
struct AnotarEfectivoIntent: AppIntent {
    static let title: LocalizedStringResource = "Anotar gasto en Valiu"
    static let description = IntentDescription("Pregunta cuánto gastaste y en qué, y lo anota en Valiu.")
    static let openAppWhenRun = false

    @Parameter(title: "Monto", requestValueDialog: "¿Cuánto gastaste?") var amount: Double
    @Parameter(title: "En qué", requestValueDialog: "¿En qué?") var merchant: String

    func perform() async throws -> some IntentResult & ProvidesDialog {
        guard amount > 0, amount < 10_000_000 else { throw ValiuError.badAmount }
        let value = String(format: "%.2f", amount)
        _ = try await ValiuLink.send(amount: value, merchant: merchant, card: "Back Tap")
        let msg = "Anotado en Valiu: \(value) en \(merchant)"
        return .result(dialog: "\(msg)")
    }
}

@available(iOS 16.0, *)
struct ValiuShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: AnotarEfectivoIntent(),
                    phrases: ["Anotar gasto en \(.applicationName)", "Anota un gasto en \(.applicationName)"])
        AppShortcut(intent: RegistrarCompraIntent(),
                    phrases: ["Registrar compra en \(.applicationName)"])
    }
}
