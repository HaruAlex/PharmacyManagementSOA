package suites;

import org.junit.Test;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;

public class JupiterTestsTest {
    @Test
    public void runGatewayJupiterTests() {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(
                        selectPackage("controllers"),
                        selectPackage("services"),
                        selectPackage("integration.consul"))
                .build();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, listener);

        TestExecutionSummary summary = listener.getSummary();
        String failures = summary.getFailures().stream()
                .map(failure -> failure.getTestIdentifier().getDisplayName()
                        + ": " + failure.getException())
                .collect(Collectors.joining("\n"));

        assertTrue("No JUnit 5 tests were discovered", summary.getTestsFoundCount() > 0);
        assertEquals(failures, 0, summary.getTotalFailureCount());
    }
}
