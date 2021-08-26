package com.sequenceiq.it.cloudbreak.testcase.e2e.environment;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.testng.annotations.Test;

import com.sequenceiq.cloudbreak.common.mappable.CloudPlatform;
import com.sequenceiq.environment.api.v1.environment.model.response.DetailedEnvironmentResponse;
import com.sequenceiq.environment.api.v1.environment.model.response.EnvironmentStatus;
import com.sequenceiq.it.cloudbreak.EnvironmentClient;
import com.sequenceiq.it.cloudbreak.assertion.Assertion;
import com.sequenceiq.it.cloudbreak.client.CredentialTestClient;
import com.sequenceiq.it.cloudbreak.client.EnvironmentTestClient;
import com.sequenceiq.it.cloudbreak.context.Description;
import com.sequenceiq.it.cloudbreak.context.TestContext;
import com.sequenceiq.it.cloudbreak.dto.credential.CredentialTestDto;
import com.sequenceiq.it.cloudbreak.dto.environment.EnvironmentTestDto;
import com.sequenceiq.it.cloudbreak.dto.telemetry.TelemetryTestDto;
import com.sequenceiq.it.cloudbreak.testcase.e2e.AbstractE2ETest;
import com.sequenceiq.it.cloudbreak.util.spot.UseSpotInstances;

public class EnvironmentWithCustomerManagedKeyTests extends AbstractE2ETest {

    private static final String ENCRYPTION_KEY_URL = "https://dummyVaultName.vault.azure.net/keys/dummyKeyName/dummyKeyVersion";

    private static final String ENCRYPTION_KEY_URL_RESOURCE_GROUP = "dummyResourceGroup";

    private static final String RESOURCE_GROUP = "someOtherResourceGroup";

    @Inject
    private EnvironmentTestClient environmentTestClient;

    @Inject
    private CredentialTestClient credentialTestClient;

    @Override
    protected void setupTest(TestContext testContext) {
        checkCloudPlatform(CloudPlatform.AZURE);
        createDefaultUser(testContext);
    }

    @Test(dataProvider = TEST_CONTEXT)
    @UseSpotInstances
    @Description(
            given = "there is a running cloudbreak",
            when = "create an Environment with encryption parameters where key and environment are in same Resource groups",
            then = "should use encryption parameters for resource encryption.")
    public void testEnvironmentWithCustomerManagedKeyAndSameResourceGroup(TestContext testContext) {

        testContext
                .given(CredentialTestDto.class)
                .when(credentialTestClient.create())
                .given("telemetry", TelemetryTestDto.class)
                .withLogging()
                .withReportClusterLogs()
                .given(EnvironmentTestDto.class)
                .withNetwork()
                .withAzureResourceEncryptionParameters(ENCRYPTION_KEY_URL, null)
                .withResourceGroup("SINGLE", ENCRYPTION_KEY_URL_RESOURCE_GROUP)
                .withTelemetry("telemetry")
                .withCreateFreeIpa(Boolean.FALSE)
                .when(environmentTestClient.create())
                .await(EnvironmentStatus.AVAILABLE)
                .then((tc, testDto, cc) -> environmentTestClient.describe().action(tc, testDto, cc))
                .then(verifyEncryptionParameters())
                .validate();
    }

    @Test(dataProvider = TEST_CONTEXT)
    @UseSpotInstances
    @Description(
            given = "there is a running cloudbreak",
            when = "create an Environment with encryption parameters where key and environment are in different Resource groups",
            then = "should use encryption parameters for resource encryption.")
    public void testEnvironmentWithCustomerManagedKeyAndDifferentResourceGroup(TestContext testContext) {

        testContext
                .given(CredentialTestDto.class)
                .when(credentialTestClient.create())
                .given("telemetry", TelemetryTestDto.class)
                .withLogging()
                .withReportClusterLogs()
                .given(EnvironmentTestDto.class)
                .withNetwork()
                .withAzureResourceEncryptionParameters(ENCRYPTION_KEY_URL, ENCRYPTION_KEY_URL_RESOURCE_GROUP)
                .withResourceGroup("SINGLE", RESOURCE_GROUP)
                .withTelemetry("telemetry")
                .withCreateFreeIpa(Boolean.FALSE)
                .when(environmentTestClient.create())
                .await(EnvironmentStatus.AVAILABLE)
                .then((tc, testDto, cc) -> environmentTestClient.describe().action(tc, testDto, cc))
                .then(verifyEncryptionParameters())
                .validate();
    }

    private static Assertion<EnvironmentTestDto, EnvironmentClient> verifyEncryptionParameters() {
        return (testContext, testDto, environmentClient) -> {
            DetailedEnvironmentResponse environment = environmentClient.getDefaultClient().environmentV1Endpoint().getByName(testDto.getName());
            if (CloudPlatform.AZURE.name().equals(environment.getCloudPlatform())) {
                if (StringUtils.isEmpty(environment.getAzure().getResourceEncryptionParameters().getDiskEncryptionSetId())) {
                    throw new IllegalArgumentException("Failed to create disk encryption set.");
                }
            }
            return testDto;
        };
    }
}
