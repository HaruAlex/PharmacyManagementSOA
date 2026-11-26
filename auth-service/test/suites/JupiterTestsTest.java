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
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;

public class JupiterTestsTest {
    @Test
    public void runAllJupiterTests() {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(
                        selectPackage("clients"),
                        selectClass("clients.FakeBranchLookupClientTest"),
                        selectPackage("controllers"),
                        selectPackage("errors"),
                        selectPackage("security"),
                        selectPackage("seed"),
                        selectPackage("services")
                )
                .build();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        Launcher launcher = LauncherFactory.create();
        launcher.execute(request, listener);

        TestExecutionSummary summary = listener.getSummary();
        String failures = summary.getFailures().stream()
                .map(failure -> failure.getTestIdentifier().getDisplayName()
                        + ": " + failure.getException())
                .collect(Collectors.joining("\n"));

        System.out.printf("JUnit 5 tests: %d found, %d successful%n",
                summary.getTestsFoundCount(), summary.getTestsSucceededCount());
        assertTrue("No JUnit 5 tests were discovered", summary.getTestsFoundCount() > 0);
        assertEquals(failures, 0, summary.getTotalFailureCount());
    }
}
