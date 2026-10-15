import UIKit
import SwiftUI
import Shared
import StripeCore
import StripePaymentSheet

/// Presents Stripe's native PaymentSheet while hosting the Compose application.
private final class StripePaymentHostViewController: UIViewController {
    private let paymentHost: IosStripePaymentHost
    private var paymentSheet: PaymentSheet?
    private var activeOrderId: String?

    init(paymentHost: IosStripePaymentHost) {
        self.paymentHost = paymentHost
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    func embed(_ child: UIViewController) {
        addChild(child)
        child.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(child.view)
        NSLayoutConstraint.activate([
            child.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            child.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            child.view.topAnchor.constraint(equalTo: view.topAnchor),
            child.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        child.didMove(toParent: self)
    }

    /// Show PaymentSheet for an authenticated, server-created PaymentIntent.
    /// The client secret is passed directly to Stripe's SDK and is never persisted by QMarket.
    func presentStripePayment(_ session: PaymentSessionDto) -> Bool {
        guard isViewLoaded, view.window != nil else { return false }
        guard activeOrderId == nil, paymentSheet == nil else { return false }

        let publishableKeyIsValid =
            session.publishableKey.range(
                of: #"^pk_(test|live)_[A-Za-z0-9]+$"#,
                options: .regularExpression
            ) != nil
        guard session.providerId == "stripe",
              !session.clientSecret.isEmpty,
              publishableKeyIsValid else {
            paymentHost.publishPaymentResult(orderId: session.orderId, outcome: "failed")
            return true
        }
        guard paymentHost.beginPayment(orderId: session.orderId) else { return false }

        STPAPIClient.shared.publishableKey = session.publishableKey

        var configuration = PaymentSheet.Configuration()
        configuration.merchantDisplayName = "QMarket"
        configuration.returnURL = "com.kenlikdev.qmarket.stripe://stripe-redirect"

        let sheet = PaymentSheet(
            paymentIntentClientSecret: session.clientSecret,
            configuration: configuration
        )
        paymentSheet = sheet
        activeOrderId = session.orderId
        sheet.present(from: self) { [weak self] result in
            guard let self else { return }
            let orderId = self.activeOrderId
            self.activeOrderId = nil
            self.paymentSheet = nil
            guard let orderId else { return }

            let outcome: String
            switch result {
            case .completed:
                outcome = "completed"
            case .canceled:
                outcome = "canceled"
            case .failed(_):
                outcome = "failed"
            }
            self.paymentHost.publishPaymentResult(orderId: orderId, outcome: outcome)
        }
        return true
    }
}

struct ComposeView: UIViewControllerRepresentable {
    let paymentHost: IosStripePaymentHost

    func makeUIViewController(context: Self.Context) -> UIViewController {
        let hostController = StripePaymentHostViewController(paymentHost: paymentHost)
        let composeController = MainViewControllerKt.MainViewController(
            paymentHost: paymentHost,
            onConfirmStripePayment: { [weak hostController] session in
                guard let hostController else { return false }
                return hostController.presentStripePayment(session)
            }
        )
        hostController.embed(composeController)
        return hostController
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    @State private var paymentHost = IosStripePaymentHost()

    var body: some View {
        ComposeView(paymentHost: paymentHost)
            .ignoresSafeArea()
            .onOpenURL { url in
                // Stripe redirects from 3DS/bank authentication must be routed back to the SDK.
                _ = StripeAPI.handleURLCallback(with: url)
            }
    }
}
