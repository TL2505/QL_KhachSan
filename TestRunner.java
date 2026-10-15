import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintWriter;
import java.util.List;

public class TestRunner {
    public static void main(String[] args) {
        String testClassName = (args.length > 0) ? args[0] : "quanlykhachsan.frontend.view.Phase2UiAcceptanceTest";
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(testClassName))
                .build();
        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        TestExecutionSummary summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out));

        List<TestExecutionSummary.Failure> failures = summary.getFailures();
        for (TestExecutionSummary.Failure failure : failures) {
            System.out.println("FAILURE: " + failure.getTestIdentifier().getDisplayName());
            failure.getException().printStackTrace(System.out);
        }

        if (summary.getTotalFailureCount() > 0) {
            System.exit(1);
        }
    }
}
