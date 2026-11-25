import WidgetKit
import SwiftUI

@main
struct ValiuWidgetBundle: WidgetBundle {
    var body: some Widget {
        BudgetWidget()
        PaymentsWidget()
    }
}
