import java.util.Map
import static org.hamcrest.Matchers.hasItem
import static org.junit.Assert.assertThat

import org.hamcrest.Matchers
import static org.hamcrest.Matchers.containsString
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.RuleChain

import util.BasePiperTest
import util.JenkinsCredentialsRule
import util.JenkinsStepRule
import util.JenkinsLoggingRule
import util.JenkinsReadYamlRule
import util.JenkinsShellCallRule
import util.Rules

import hudson.AbortException

public class PullSoftwareComponentToAbapCloudPlatformTest extends BasePiperTest {

    private ExpectedException thrown = new ExpectedException()
    private JenkinsStepRule stepRule = new JenkinsStepRule(this)
    private JenkinsLoggingRule loggingRule = new JenkinsLoggingRule(this)
    private JenkinsShellCallRule shellRule = new JenkinsShellCallRule(this)

    @Rule
    public RuleChain ruleChain = Rules.getCommonRules(this)
        .around(new JenkinsReadYamlRule(this))
        .around(thrown)
        .around(stepRule)
        .around(loggingRule)
        .around(shellRule)

    @Before
    public void setup() {
    }

    @Test
    public void pullSuccessful() {
        shellRule.setReturnValue(JenkinsShellCallRule.Type.REGEX, /.*x-csrf-token: fetch.*/, "TOKEN")
        shellRule.setReturnValue(JenkinsShellCallRule.Type.REGEX, /.*POST.*/, /{"d" : { "__metadata" : { "uri" : "https:\/\/example.com\/URI" } , "status" : "R", "status_descr" : "RUNNING" }}/)
        shellRule.setReturnValue(JenkinsShellCallRule.Type.REGEX, /.*https:\/\/example\.com.*/, /{"d" : { "__metadata" : { "uri" : "https:\/\/example.com\/URI" } , "status" : "S", "status_descr" : "SUCCESS" }}/)

        helper.registerAllowedMethod("readFile", [String.class], {
            /HTTP\/1.1 200 OK
            set-cookie: sap-usercontext=sap-client=100; path=\/
            content-type: application\/json; charset=utf-8
            content-length: 9321
            sap-system: Y11
            x-csrf-token: TOKEN/
        })

        loggingRule.expect("[pullSoftwareComponentToAbapCloudPlatform] Pull Status: RUNNING")
        loggingRule.expect("[pullSoftwareComponentToAbapCloudPlatform] Entity URI: https://example.com/URI")
        loggingRule.expect("[pullSoftwareComponentToAbapCloudPlatform] Pull Status: SUCCESS")

        stepRule.step.pullSoftwareComponentToAbapCloudPlatform(script: nullScript, host: 'https://example.com', repositoryName: 'Z_DEMO_DM', username: 'user', password: 'password')

        assertThat(shellRule.shell[0], containsString(/#!\/bin\/bash curl -I -X GET https:\/\/example.com\/sap\/opu\/odata\/sap\/MANAGE_GIT_REPOSITORY\/Pull -H 'Authorization: Basic dXNlcjpwYXNzd29yZA==' -H 'Accept: application\/json' -H 'x-csrf-token: fetch' -D header.txt | awk 'BEGIN {FS=": "}\/^x-csrf-token\/{print $2}'/))
        assertThat(shellRule.shell[1], containsString(/#!\/bin\/bash curl -X POST "https:\/\/example.com\/sap\/opu\/odata\/sap\/MANAGE_GIT_REPOSITORY\/Pull" -H 'Authorization: Basic dXNlcjpwYXNzd29yZA==' -H 'Accept: application\/json' -H 'Content-Type: application\/json' -H 'x-csrf-token: TOKEN' --cookie header.txt -d '{ "sc_name": "Z_DEMO_DM" }'/))
        assertThat(shellRule.shell[2], containsString(/#!\/bin\/bash curl -X GET "https:\/\/example.com\/URI" -H 'Authorization: Basic dXNlcjpwYXNzd29yZA==' -H 'Accept: application\/json'/))
    }

    @Test
    public void pullFailsWhilePolling() {
        shellRule.setReturnValue(JenkinsShellCallRule.Type.REGEX, /.*x-csrf-token: fetch.*/, "TOKEN")
        shellRule.setReturnValue(JenkinsShellCallRule.Type.REGEX, /.*POST.*/, /{"d" : { "__metadata" : { "uri" : "https:\/\/example.com\/URI" } , "status" : "R", "status_descr" : "RUNNING" }}/)
        shellRule.setReturnValue(JenkinsShellCallRule.Type.REGEX, /.*https:\/\/example\.com.*/, /{"d" : { "__metadata" : { "uri" : "https:\/\/example.com\/URI" } , "status" : "E", "status_descr" : "ERROR" }}/)
        
        helper.registerAllowedMethod("readFile", [String.class], {
            /HTTP\/1.1 200 OK
            set-cookie: sap-usercontext=sap-client=100; path=\/
            content-type: application\/json; charset=utf-8
            content-length: 9321
            sap-system: Y11
            x-csrf-token: TOKEN/
        })

        loggingRule.expect("[pullSoftwareComponentToAbapCloudPlatform] Pull Status: RUNNING")
        loggingRule.expect("[pullSoftwareComponentToAbapCloudPlatform] Pull Status: ERROR")

        thrown.expect(Exception)
        thrown.expectMessage("[pullSoftwareComponentToAbapCloudPlatform] Pull Failed")

        stepRule.step.pullSoftwareComponentToAbapCloudPlatform(script: nullScript, host: 'https://example.com', repositoryName: 'Z_DEMO_DM', username: 'user', password: 'password')

        assertThat(shellRule.shell[0], containsString(/#!\/bin\/bash curl -I -X GET https:\/\/example.com\/sap\/opu\/odata\/sap\/MANAGE_GIT_REPOSITORY\/Pull -H 'Authorization: Basic dXNlcjpwYXNzd29yZA==' -H 'Accept: application\/json' -H 'x-csrf-token: fetch' -D header.txt | awk 'BEGIN {FS=": "}\/^x-csrf-token\/{print $2}'/))
        assertThat(shellRule.shell[1], containsString(/#!\/bin\/bash curl -X POST "https:\/\/example.com\/sap\/opu\/odata\/sap\/MANAGE_GIT_REPOSITORY\/Pull" -H 'Authorization: Basic dXNlcjpwYXNzd29yZA==' -H 'Accept: application\/json' -H 'Content-Type: application\/json' -H 'x-csrf-token: TOKEN' --cookie header.txt -d '{ "sc_name": "Z_DEMO_DM" }'/))
        assertThat(shellRule.shell[2], containsString(/#!\/bin\/bash curl -X GET "https:\/\/example.com\/URI" -H 'Authorization: Basic dXNlcjpwYXNzd29yZA==' -H 'Accept: application\/json'/))
    }

    @Test
    public void pullFailsWithPostRequest() {
        shellRule.setReturnValue(JenkinsShellCallRule.Type.REGEX, /.*x-csrf-token: fetch.*/, "TOKEN")
        shellRule.setReturnValue(JenkinsShellCallRule.Type.REGEX, /.*POST.*/, /{"d" : { "__metadata" : { "uri" : "https:\/\/example.com\/URI" } , "status" : "E", "status_descr" : "ERROR" }}/)
        shellRule.setReturnValue(JenkinsShellCallRule.Type.REGEX, /.*https:\/\/example\.com.*/, /{"d" : { "__metadata" : { "uri" : "https:\/\/example.com\/URI" } , "status" : "E", "status_descr" : "ERROR" }}/)
        
        helper.registerAllowedMethod("readFile", [String.class], {
            /HTTP\/1.1 200 OK
            set-cookie: sap-usercontext=sap-client=100; path=\/
            content-type: application\/json; charset=utf-8
            content-length: 9321
            sap-system: Y11
            x-csrf-token: TOKEN/
        })

        loggingRule.expect("[pullSoftwareComponentToAbapCloudPlatform] Pull Status: ERROR")

        thrown.expect(Exception)
        thrown.expectMessage("[pullSoftwareComponentToAbapCloudPlatform] Pull Failed")

        stepRule.step.pullSoftwareComponentToAbapCloudPlatform(script: nullScript, host: 'https://example.com', repositoryName: 'Z_DEMO_DM', username: 'user', password: 'password')

        assertThat(shellRule.shell[0], containsString(/#!\/bin\/bash curl -I -X GET https:\/\/example.com\/sap\/opu\/odata\/sap\/MANAGE_GIT_REPOSITORY\/Pull -H 'Authorization: Basic dXNlcjpwYXNzd29yZA==' -H 'Accept: application\/json' -H 'x-csrf-token: fetch' -D header.txt | awk 'BEGIN {FS=": "}\/^x-csrf-token\/{print $2}'/))
        assertThat(shellRule.shell[1], containsString(/#!\/bin\/bash curl -X POST "https:\/\/example.com\/sap\/opu\/odata\/sap\/MANAGE_GIT_REPOSITORY\/Pull" -H 'Authorization: Basic dXNlcjpwYXNzd29yZA==' -H 'Accept: application\/json' -H 'Content-Type: application\/json' -H 'x-csrf-token: TOKEN' --cookie header.txt -d '{ "sc_name": "Z_DEMO_DM" }'/))
        assertThat(shellRule.shell[2], containsString(/#!\/bin\/bash curl -X GET "https:\/\/example.com\/URI" -H 'Authorization: Basic dXNlcjpwYXNzd29yZA==' -H 'Accept: application\/json'/))
    }

    @Test
    public void pullWithHttpError() {
        shellRule.setReturnValue(JenkinsShellCallRule.Type.REGEX, /.*x-csrf-token: fetch.*/, "TOKEN")
        shellRule.setReturnValue(JenkinsShellCallRule.Type.REGEX, /.*POST.*/, /{"error" : { "message" : { "lang" : "en", "value": "text" } }}/)
        
        helper.registerAllowedMethod("readFile", [String.class], {
            /HTTP\/1.1 200 OK
            set-cookie: sap-usercontext=sap-client=100; path=\/
            content-type: application\/json; charset=utf-8
            content-length: 9321
            sap-system: Y11
            x-csrf-token: TOKEN/
        })

        thrown.expect(Exception)
        thrown.expectMessage("[pullSoftwareComponentToAbapCloudPlatform] text")

        stepRule.step.pullSoftwareComponentToAbapCloudPlatform(script: nullScript, host: 'https://example.com', repositoryName: 'Z_DEMO_DM', username: 'user', password: 'password')

        assertThat(shellRule.shell[0], containsString(/#!\/bin\/bash curl -I -X GET https:\/\/example.com\/sap\/opu\/odata\/sap\/MANAGE_GIT_REPOSITORY\/Pull -H 'Authorization: Basic dXNlcjpwYXNzd29yZA==' -H 'Accept: application\/json' -H 'x-csrf-token: fetch' -D header.txt | awk 'BEGIN {FS=": "}\/^x-csrf-token\/{print $2}'/))
        assertThat(shellRule.shell[1], containsString(/#!\/bin\/bash curl -X POST "https:\/\/example.com\/sap\/opu\/odata\/sap\/MANAGE_GIT_REPOSITORY\/Pull" -H 'Authorization: Basic dXNlcjpwYXNzd29yZA==' -H 'Accept: application\/json' -H 'Content-Type: application\/json' -H 'x-csrf-token: TOKEN' --cookie header.txt -d '{ "sc_name": "Z_DEMO_DM" }'/))
    }

    @Test
    public void connectionFails() {
        shellRule.setReturnValue(JenkinsShellCallRule.Type.REGEX, /.*x-csrf-token: fetch.*/, null)

        helper.registerAllowedMethod("readFile", [String.class], {
            /HTTP\/1.1 401 Unauthorized
            set-cookie: sap-usercontext=sap-client=100; path=\/
            content-type: application\/json; charset=utf-8
            content-length: 9321
            sap-system: Y11/
        })

        thrown.expect(Exception)
        thrown.expectMessage("[pullSoftwareComponentToAbapCloudPlatform] Connection Failed: 401 Unauthorized")

        stepRule.step.pullSoftwareComponentToAbapCloudPlatform(script: nullScript, host: 'https://example.com', repositoryName: 'Z_DEMO_DM', username: 'user', password: 'password')

        assertThat(shellRule.shell[0], containsString(/#!\/bin\/bash curl -I -X GET https:\/\/example.com\/sap\/opu\/odata\/sap\/MANAGE_GIT_REPOSITORY\/Pull -H 'Authorization: Basic dXNlcjpwYXNzd29yZA==' -H 'Accept: application\/json' -H 'x-csrf-token: fetch' -D header.txt | awk 'BEGIN {FS=": "}\/^x-csrf-token\/{print $2}'/))
    }

    @Test
    public void checkRepositoryProvided() {
       thrown.expect(IllegalArgumentException)
       thrown.expectMessage("Repository / Software Component not provided")
       stepRule.step.pullSoftwareComponentToAbapCloudPlatform(script: nullScript, host: 'https://www.example.com', username: 'user', password: 'password')
    }

    @Test
    public void checkHostProvided() {
       thrown.expect(IllegalArgumentException)
       thrown.expectMessage("Host not provided")
       stepRule.step.pullSoftwareComponentToAbapCloudPlatform(script: nullScript, repositoryName: 'REPO', username: 'user', password: 'password')
    }

    @Test
    public void testHttpHeader() {

        String header = /HTTP\/1.1 401 Unauthorized
            set-cookie: sap-usercontext=sap-client=100; path=\/
            content-type: text\/html; charset=utf-8
            content-length: 9321
            sap-system: Y11
            x-csrf-token: TOKEN
            www-authenticate: Basic realm="SAP NetWeaver Application Server [Y11\/100][alias]"
            sap-server: true
            sap-perf-fesrec: 72927.000000/

        HttpHeader httpHeader = new HttpHeader(header)
        assertThat(httpHeader.statusCode, containsString("401"))
        assertThat(httpHeader.statusMessage, containsString("Unauthorized"))
    }
}