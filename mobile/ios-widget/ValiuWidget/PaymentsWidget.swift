import WidgetKit
import SwiftUI

// Large widget: this month's calendar with the payment days marked, and the next payments underneath.
struct PaymentsEntry: TimelineEntry {
    let date: Date
    let data: VData?
}

struct PaymentsProvider: TimelineProvider {
    func placeholder(in context: Context) -> PaymentsEntry { PaymentsEntry(date: Date(), data: ValiuStore.sample(for: Date())) }

    func getSnapshot(in context: Context, completion: @escaping (PaymentsEntry) -> Void) {
        let now = Date()
        completion(PaymentsEntry(date: now, data: context.isPreview ? ValiuStore.sample(for: now) : (ValiuStore.load() ?? ValiuStore.sample(for: now))))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<PaymentsEntry>) -> Void) {
        let data = ValiuStore.load()
        let entries = ValiuDates.timeline(from: Date()).map { PaymentsEntry(date: $0, data: data) }
        completion(Timeline(entries: entries, policy: .atEnd))
    }
}

struct PaymentsView: View {
    let entry: PaymentsEntry

    private var cal: Calendar { Calendar.current }

    var body: some View {
        let now = entry.date
        let y = cal.component(.year, from: now), m = cal.component(.month, from: now), today = cal.component(.day, from: now)
        let month = entry.data?.month
        let fresh = month?.y == y && month?.m == m
        let due = fresh ? (month?.due ?? [:]) : [:]
        let first = cal.date(from: DateComponents(year: y, month: m, day: 1)) ?? now
        let offset = cal.component(.weekday, from: first) - 1          // Sunday first, like the app
        let dim = cal.range(of: .day, in: .month, for: first)?.count ?? 30
        let rows = (offset + dim) > 35 ? 6 : 5
        let next = (entry.data?.next ?? []).sorted { ($0.due ?? "") < ($1.due ?? "") }
        let data = entry.data

        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Image(fresh && next.isEmpty ? "frog_celebrando" : "frog_presupuesto").resizable().scaledToFit().frame(width: 34, height: 34)
                VStack(alignment: .leading, spacing: 0) {
                    Text(fresh ? (month?.title ?? monthTitle(first)) : monthTitle(first))
                        .font(.system(size: 17, weight: .black, design: .rounded)).foregroundStyle(VC.text).lineLimit(1)
                    Text(subtitle(data: data, fresh: fresh, count: next.count)).font(.system(size: 11)).foregroundStyle(VC.muted).lineLimit(1)
                }
                Spacer(minLength: 4)
                Link(destination: valiuURL.add) { PlusButton(size: 30) }
            }

            let wds = (month?.wds?.count == 7 ? month?.wds : nil) ?? ["D", "L", "M", "M", "J", "V", "S"]
            HStack(spacing: 0) {
                ForEach(0..<7, id: \.self) { k in
                    Text(wds[k]).font(.system(size: 11, weight: .bold)).foregroundStyle(VC.faint).frame(maxWidth: .infinity)
                }
            }

            VStack(spacing: 0) {
                ForEach(0..<rows, id: \.self) { r in
                    HStack(spacing: 0) {
                        ForEach(0..<7, id: \.self) { k in
                            dayCell(day: r * 7 + k - offset + 1, dim: dim, today: today, state: due[String(r * 7 + k - offset + 1)] ?? "")
                        }
                    }
                    .frame(maxHeight: .infinity)
                }
            }
            .frame(maxHeight: .infinity)

            ForEach(Array(next.prefix(3).enumerated()), id: \.offset) { _, item in
                nextRow(item, data: data, now: now)
            }
        }
        .widgetURL(valiuURL.payments)
        .containerBackground(for: .widget) { VC.bg }
    }

    @ViewBuilder
    private func dayCell(day: Int, dim: Int, today: Int, state: String) -> some View {
        if day < 1 || day > dim {
            Color.clear.frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            let open = state == "due"
            let style: (Color, Color) = {
                if day == today { return (open ? VC.warn : VC.accent, VC.onAccent) }
                if open && day < today { return (VC.dangerSoft, VC.danger) }
                if open { return (VC.warnSoft, VC.warn) }
                if state == "paid" { return (VC.accentSoft, VC.accent) }
                return (Color.clear, day < today ? VC.faint : VC.text)
            }()
            ZStack {
                Circle().fill(style.0).frame(width: 26, height: 26)
                Text("\(day)").font(.system(size: 13, weight: day == today ? .bold : .regular)).foregroundStyle(style.1)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func nextRow(_ item: VNext, data: VData?, now: Date) -> some View {
        let n = ValiuDates.days(from: now, to: item.due) ?? 0
        let when: String = {
            guard let data else { return n < 0 ? "Vencido" : n == 0 ? "Hoy" : n == 1 ? "Mañana" : "\(n) días" }
            if n < 0 { return data.text("overdue", "Vencido") }
            if n == 0 { return data.text("today", "Hoy") }
            if n == 1 { return data.text("tomorrow", "Mañana") }
            return data.text("inDays", "{n} días").replacingOccurrences(of: "{n}", with: String(n))
        }()
        let fg = n < 0 ? VC.danger : n <= 3 ? VC.warn : VC.accent
        let bg = n < 0 ? VC.dangerSoft : n <= 3 ? VC.warnSoft : VC.accentSoft
        return HStack(spacing: 8) {
            Text(when).font(.system(size: 11, weight: .bold)).foregroundStyle(fg)
                .frame(minWidth: 56).padding(.vertical, 2).padding(.horizontal, 6)
                .background(Capsule().fill(bg))
            Text(item.name ?? "").font(.system(size: 13)).foregroundStyle(VC.text).lineLimit(1)
            Spacer(minLength: 4)
            Text((data ?? ValiuStore.sample(for: now)).money(item.amt ?? 0)).font(.system(size: 13, weight: .bold)).foregroundStyle(VC.text).lineLimit(1)
        }
    }

    private func subtitle(data: VData?, fresh: Bool, count: Int) -> String {
        guard let data, fresh else { return data?.text("open", "Abre Valiu para ver tu día") ?? "Abre Valiu para ver tu día" }
        if count == 0 { return data.text("allPaid", "Todo pagado este mes") }
        if count == 1 { return data.text("pending1", "1 pago pendiente") }
        return data.text("pendingN", "{n} pagos pendientes").replacingOccurrences(of: "{n}", with: String(count))
    }

    private func monthTitle(_ d: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "es_GT")
        f.dateFormat = "LLLL 'de' yyyy"
        let s = f.string(from: d)
        return s.prefix(1).uppercased() + s.dropFirst()
    }
}

struct PaymentsWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ValiuPayments", provider: PaymentsProvider()) { entry in
            PaymentsView(entry: entry)
        }
        .configurationDisplayName("Calendario de pagos")
        .description("Tus fechas de pago del mes y lo próximo que toca pagar.")
        .supportedFamilies([.systemLarge])
    }
}
