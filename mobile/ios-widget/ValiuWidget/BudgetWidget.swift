import WidgetKit
import SwiftUI

// Small widget: what you can still spend today, with the frog that matches how the day is going.
struct BudgetEntry: TimelineEntry {
    let date: Date
    let data: VData?
}

struct BudgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> BudgetEntry { BudgetEntry(date: Date(), data: ValiuStore.sample(for: Date())) }

    func getSnapshot(in context: Context, completion: @escaping (BudgetEntry) -> Void) {
        let now = Date()
        completion(BudgetEntry(date: now, data: context.isPreview ? ValiuStore.sample(for: now) : (ValiuStore.load() ?? ValiuStore.sample(for: now))))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<BudgetEntry>) -> Void) {
        let data = ValiuStore.load()
        let entries = ValiuDates.timeline(from: Date()).map { BudgetEntry(date: $0, data: data) }
        completion(Timeline(entries: entries, policy: .atEnd))
    }
}

struct BudgetView: View {
    let entry: BudgetEntry

    var body: some View {
        let data = entry.data
        let iso = ValiuDates.iso(entry.date)
        let day = data?.days?.first(where: { $0.d == iso })
        Group {
            if let data, let day {
                let daily = day.daily ?? 0
                let left = daily - (day.spent ?? 0)
                let ratio = daily > 0 ? max(0, min(1, left / daily)) : 0
                let over = left < -0.005
                let tone: Color = over || daily <= 0 ? VC.danger : ratio < 0.3 ? VC.warn : VC.accent
                let frog = over ? "frog_sin_fondos" : daily <= 0 ? "frog_preocupado" : ratio < 0.3 ? "frog_en_alerta"
                    : (data.day == iso ? frogImage(data.mascot) : "frog_equilibrio")
                content(frog: frog,
                        label: over ? data.text("over", "Te pasaste hoy") : data.text("can", "Puedes gastar hoy"),
                        amount: data.money(abs(left)), amountColor: tone,
                        of: data.text("of", "de {x} para hoy").replacingOccurrences(of: "{x}", with: data.money(daily)),
                        progress: over || daily <= 0 ? 1 : ratio, barColor: tone)
            } else {
                content(frog: "frog_pensando", label: data?.text("can", "Puedes gastar hoy") ?? "Puedes gastar hoy",
                        amount: "—", amountColor: VC.accent,
                        of: data?.text("open", "Abre Valiu para ver tu día") ?? "Abre Valiu para ver tu día",
                        progress: 0, barColor: VC.accent)
            }
        }
        .widgetURL(valiuURL.add)
        .containerBackground(for: .widget) { VC.bg }
    }

    private func content(frog: String, label: String, amount: String, amountColor: Color, of: String, progress: Double, barColor: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .top) {
                Image(frog).resizable().scaledToFit().frame(maxHeight: 54, alignment: .topLeading)
                Spacer(minLength: 4)
                PlusButton(size: 28)
            }
            Spacer(minLength: 2)
            Text(label).font(.system(size: 11)).foregroundStyle(VC.muted).lineLimit(1)
            Text(amount).font(.system(size: 24, weight: .black, design: .rounded)).foregroundStyle(amountColor)
                .lineLimit(1).minimumScaleFactor(0.5)
            Text(of).font(.system(size: 10)).foregroundStyle(VC.muted).lineLimit(1).minimumScaleFactor(0.8)
            ZStack(alignment: .leading) {
                Capsule().fill(VC.track)
                GeometryReader { g in Capsule().fill(barColor).frame(width: g.size.width * progress) }
            }
            .frame(height: 6)
            .padding(.top, 3)
        }
    }
}

struct BudgetWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ValiuBudget", provider: BudgetProvider()) { entry in
            BudgetView(entry: entry)
        }
        .configurationDisplayName("Tu presupuesto de hoy")
        .description("Lo que te queda para gastar hoy, con tu ranita. Tócalo para anotar un gasto.")
        .supportedFamilies([.systemSmall])
    }
}
